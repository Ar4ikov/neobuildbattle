package com.neobuildbattle.core.build;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.build.gui.BuildGuiHolder;
import com.neobuildbattle.core.build.gui.GuiType;
import com.neobuildbattle.core.build.gui.MainGui;
import com.neobuildbattle.core.build.ui.ItemFactory;
import com.neobuildbattle.core.build.biome.BiomeGui;
import com.neobuildbattle.core.build.time.TimeGui;
import com.neobuildbattle.core.build.weather.WeatherGui;
import com.neobuildbattle.core.build.particles.ParticlesGui;
import com.neobuildbattle.core.build.gradients.GradientsGui;
import com.neobuildbattle.core.game.GameManager;
import com.neobuildbattle.core.game.GameState;
import com.neobuildbattle.core.plot.Plot;
import com.neobuildbattle.core.plot.PlotManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.block.Biome;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player build GUI, tools item, per-plot time/weather and particle centers.
 */
public final class BuildToolsManager implements Listener {

    private final NeoBuildBattleCore plugin;
    private final PlotManager plotManager;

    private final NamespacedKey toolKey;
    private final NamespacedKey guiKey;
    private final NamespacedKey particleTypeKey;
    private final NamespacedKey particleEraserKey;

    // Per-player persisted UI state for the duration of a game
    private final Map<UUID, PlayerBuildState> playerState = new ConcurrentHashMap<>();

    // Per-plot environment state (time/weather) and particles
    private final Map<UUID, PlotEnvironmentState> plotEnv = new ConcurrentHashMap<>();

    // Track which GUI requires returning to main when closed
    // old approach replaced by holder-type check on close

    // Particle centers per plot
    private static final int MAX_PARTICLE_CENTERS_PER_PLOT = 100;

    // Gradient pages (each 8 materials)
    private final List<List<Material>> GRADIENTS = new ArrayList<>();

    public BuildToolsManager(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
        this.plotManager = plugin.getPlotManager();
        this.toolKey = new NamespacedKey(plugin, "build_tool");
        this.guiKey = new NamespacedKey(plugin, "build_gui");
        this.particleTypeKey = new NamespacedKey(plugin, "particle_type");
        this.particleEraserKey = new NamespacedKey(plugin, "particle_eraser");

        // particle tick - efficient spawn loop
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickParticlesAndEnv, 10L, 8L);

        // Load gradients from gradients.yml
        reloadGradients();
    }

    // ---------- Public API ----------
    public void giveBuildTool(Player player) {
        ItemStack item = new ItemStack(Material.LIGHT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Освещение и инструменты");
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        // Place in last hotbar slot (index 8) if possible
        Inventory inv = player.getInventory();
        int target = 8;
        ItemStack existing = inv.getItem(target);
        if (existing == null || existing.getType() == Material.AIR) {
            inv.setItem(target, item);
        } else {
            int free = firstEmptyExcept(inv, -1);
            if (free != -1) {
                inv.setItem(free, existing);
                inv.setItem(target, item);
            } else {
                // no space, just add normally (ensures presence)
                inv.addItem(item);
            }
        }

        playerState.computeIfAbsent(player.getUniqueId(), id -> new PlayerBuildState());
    }

    public void resetAll() {
        // Reset player env overrides
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.resetPlayerTime();
            p.resetPlayerWeather();
        }
        // Reset biomes for all tracked plots back to default
        try {
            Biome defaultBiome = Biome.THE_VOID; // reasonable default for arena reset
            for (UUID owner : new ArrayList<>(plotEnv.keySet())) {
                Plot plot = plotManager.getPlotByOwner(owner);
                if (plot != null) applyBiomeToPlotInternal(plot, defaultBiome);
            }
        } catch (Throwable ignored) {}
        playerState.clear();
        plotEnv.clear();
    }

    // ---------- Event handlers ----------
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Player player = event.getPlayer();

        if (pdc.has(toolKey, PersistentDataType.INTEGER)) {
            // Only builders can open GUI
            if (isBuilder(player)) {
                event.setCancelled(true);
                openMainGui(player);
            } else {
                // Block spectators from using the tool at all
                event.setCancelled(true);
            }
            return;
        }

        if (pdc.has(particleTypeKey, PersistentDataType.STRING)) {
            if (!ensureIsBuilding(player)) return;
            event.setCancelled(true);
            String type = pdc.get(particleTypeKey, PersistentDataType.STRING);
            Particle particle = safeParticle(type);
            if (particle == null) return;
            Plot plot = plotManager.getPlotByOwner(player.getUniqueId());
            if (plot == null) return;
            UUID plotId = plot.getOwnerId();
            PlotEnvironmentState env = plotEnv.computeIfAbsent(plotId, id -> new PlotEnvironmentState());
            if (env.particleCenters.size() >= MAX_PARTICLE_CENTERS_PER_PLOT) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.RED + "Лимит партиклов достигнут"));
                return;
            }
            Location center = frontBlockCenter(player);
            if (!plot.containsLocation(center)) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.RED + "За пределами плота"));
                return;
            }
            env.particleCenters.add(new ParticleCenter(center, particle));
            player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.GREEN + "Точка партиклов добавлена"));
            return;
        }

        if (pdc.has(particleEraserKey, PersistentDataType.INTEGER)) {
            if (!ensureIsBuilding(player)) return;
            event.setCancelled(true);
            Plot plot = plotManager.getPlotByOwner(player.getUniqueId());
            if (plot == null) return;
            UUID plotId = plot.getOwnerId();
            PlotEnvironmentState env = plotEnv.get(plotId);
            if (env == null) return;
            ParticleCenter toRemove = raycastParticleCenter(player, env.particleCenters, 32.0, 0.9);
            if (toRemove != null) {
                env.particleCenters.remove(toRemove);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.YELLOW + "Точка удалена"));
            } else {
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.RED + "Рядом нет точки"));
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Apply env overrides when entering/leaving plots
        Player player = event.getPlayer();
        applyEnvForPlayer(player);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Keep environment in sync after teleports across plots/phases
        applyEnvForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // In case of mid-game join as spectator, ignore; otherwise state bootstrap
        playerState.computeIfAbsent(event.getPlayer().getUniqueId(), id -> new PlayerBuildState());
        // Apply current env if they join onto a plot
        applyEnvForPlayer(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        // Handle build GUIs
        if (inv.getHolder() instanceof BuildGuiHolder holder) {
            if (!ensureIsBuilding(player)) {
                event.setCancelled(true);
                return;
            }
            int topSize = event.getView().getTopInventory().getSize();
            int raw = event.getRawSlot();
            boolean inTop = raw < topSize;
            if (inTop) {
                event.setCancelled(true);
                GuiType type = holder.type;
                if (type == GuiType.MAIN) {
                    handleMainClick(player, raw, event);
                } else if (type == GuiType.TIME) {
                    handleTimeClick(player, raw);
                } else if (type == GuiType.WEATHER) {
                    handleWeatherClick(player, raw);
                } else if (type == GuiType.PARTICLES) {
                    handleParticlesClick(player, raw);
                } else if (type == GuiType.GRADIENTS) {
                    handleGradientsClick(player, raw);
                } else if (type == GuiType.BIOMES) {
                    handleBiomesClick(player, raw);
                }
            } else {
                // Allow interacting with player inventory while GUI open
                event.setCancelled(false);
            }
        }

        // Protect build tool from deletion/replacement; allow moving
        if (ensureIsBuilding(player)) {
            handleToolProtectionOnClick(player, event);
            // After any operation, ensure exactly one tool exists
            Bukkit.getScheduler().runTask(plugin, () -> enforceToolPresence(player));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        if (inv.getHolder() instanceof BuildGuiHolder holder) {
            if (!ensureIsBuilding(player)) {
                event.setCancelled(true);
                return;
            }
            int topSize = event.getView().getTopInventory().getSize();
            boolean affectsTop = event.getRawSlots().stream().anyMatch(s -> s < topSize);
                if (holder.type == GuiType.MAIN && affectsTop) {
                int floorSlot = 10;
                if (event.getRawSlots().contains(floorSlot)) {
                    ItemStack cursor = event.getOldCursor();
                    if (cursor != null && canUseAsFloor(cursor.getType())) {
                        event.setCancelled(true);
                        applyFloor(player, cursor.getType());
                        openMainGui(player);
                        return;
                    }
                }
                event.setCancelled(true);
            }
        }
        if (ensureIsBuilding(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> enforceToolPresence(player));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof BuildGuiHolder holder)) return;
        // Reopen main if player actually closed our GUI (Esc/E), not when we programmatically opened another GUI
        if (holder.type != GuiType.MAIN) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!ensureIsBuilding(player)) return;
                InventoryView view = player.getOpenInventory();
                if (view == null) { openMainGui(player); return; }
                InventoryHolder h = view.getTopInventory().getHolder();
                if (!(h instanceof BuildGuiHolder)) {
                    openMainGui(player);
                }
            });
        }
    }

    // ---------- GUI creation ----------
    public void openMainGui(Player player) {
        Inventory inv = MainGui.create(player);

        // Show selected floor preview in F slot if any
        PlayerBuildState st = playerState.computeIfAbsent(player.getUniqueId(), id -> new PlayerBuildState());
        if (st.floorMaterial != null) {
            Material displayMat = displayIconForFloor(st.floorMaterial);
            inv.setItem(10, ItemFactory.named(displayMat, ChatColor.GREEN + "Пол: " + neat(st.floorMaterial), List.of(ChatColor.GRAY + "Перетащите новый блок/ведро, чтобы изменить")));
        }

        openLater(player, inv);
    }

    public void openToolsGui(Player player) {
        Inventory inv = com.neobuildbattle.core.build.tools.ToolsGui.render();
        fillBackground(inv);
        openLater(player, inv);
    }

    public void openTimeGui(Player player) {
        Inventory inv = TimeGui.render();
        fillBackground(inv);
        openLater(player, inv);
    }

    public void openWeatherGui(Player player) {
        Inventory inv = WeatherGui.render();
        fillBackground(inv);
        openLater(player, inv);
    }

    public void openParticlesGui(Player player) {
        Inventory inv = ParticlesGui.render(particleTypeKey, particleEraserKey);
        fillBackground(inv);
        openLater(player, inv);
    }

    public void openGradientsGui(Player player) {
        PlayerBuildState st = playerState.computeIfAbsent(player.getUniqueId(), id -> new PlayerBuildState());
        int page = Math.max(0, Math.min(st.gradientPage, Math.max(0, GRADIENTS.size() - 1)));
        Inventory inv = GradientsGui.render(GRADIENTS, page);
        fillBackground(inv);
        openLater(player, inv);
    }

    // ---------- Click handlers ----------
    private void handleMainClick(Player player, int slot, InventoryClickEvent event) {
        new com.neobuildbattle.core.build.click.MainClickHandler(this).handle(player, slot, event);
    }

    private void handleTimeClick(Player player, int slot) {
        new com.neobuildbattle.core.build.click.TimeClickHandler(this).handle(player, slot, null);
    }

    private void handleWeatherClick(Player player, int slot) {
        new com.neobuildbattle.core.build.click.WeatherClickHandler(this).handle(player, slot, null);
    }

    public void openBiomesGui(Player player) {
        var options = BiomeGui.loadOptions(plugin);
        Inventory inv = BiomeGui.render(options);
        fillBackground(inv);
        openLater(player, inv);
    }

    private void handleBiomesClick(Player player, int slot) {
        new com.neobuildbattle.core.build.click.BiomeClickHandler(plugin, this).handle(player, slot, null);
    }

    private void handleParticlesClick(Player player, int slot) {
        new com.neobuildbattle.core.build.click.ParticlesClickHandler(this).handle(player, slot, null);
    }

    private void handleGradientsClick(Player player, int slot) {
        new com.neobuildbattle.core.build.click.GradientsClickHandler(this).handle(player, slot, null);
    }

    // ---------- Helpers ----------
    private boolean ensureIsBuilding(Player player) {
        GameManager gm = plugin.getGameManager();
        return gm != null && gm.getState() == GameState.BUILDING;
    }

    private boolean isBuilder(Player player) {
        return ensureIsBuilding(player) && plugin.getPlayerRegistry().isActive(player.getUniqueId());
    }

    public void applyFloor(Player player, Material material) {
        // map water/lava buckets and all water creatures buckets to water/lava floor
        if (material == Material.WATER_BUCKET || material == Material.LAVA_BUCKET) {
            material = material == Material.WATER_BUCKET ? Material.WATER : Material.LAVA;
        }
        // treat all fish buckets and axolotl/tadpole buckets as WATER
        String name = material.name();
        if (name.endsWith("_BUCKET") && (name.contains("SALMON") || name.contains("COD") || name.contains("PUFFERFISH") || name.contains("TROPICAL_FISH") || name.contains("AXOLOTL") || name.contains("TADPOLE"))) {
            material = Material.WATER;
        }
        PlayerBuildState st = playerState.computeIfAbsent(player.getUniqueId(), id -> new PlayerBuildState());
        st.floorMaterial = material;
        Plot plot = plotManager.getPlotByOwner(player.getUniqueId());
        if (plot == null) return;
        int buildArea = plugin.getConfig().getInt("build-area-size", 65);
        int baseY = plot.getSpawnLocation().getBlockY() - 1;
        World world = plot.getSpawnLocation().getWorld();
        int startX = plot.getMinX() + 3;
        int startZ = plot.getMinZ() + 3;
        // Spread changes across ticks to avoid lag
        final int rowsPerTick = 8;
        final Material applyMaterial = material;
        for (int zOff = 0; zOff < buildArea; zOff += rowsPerTick) {
            final int zStart = zOff;
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int dz = 0; dz < rowsPerTick && zStart + dz < buildArea; dz++) {
                    int z = startZ + zStart + dz;
                    for (int x = startX; x < startX + buildArea; x++) {
                        world.getBlockAt(x, baseY, z).setType(applyMaterial, false);
                    }
                }
            });
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.GREEN + "Пол изменен: " + neat(material)));
    }

    // Allow floor replacement using blocks or specific buckets that map to fluids
    public boolean canUseAsFloor(Material material) {
        if (material == null) return false;
        if (material.isBlock()) return true;
        if (material == Material.WATER_BUCKET || material == Material.LAVA_BUCKET) return true;
        String name = material.name();
        return name.endsWith("_BUCKET") && (name.contains("SALMON") || name.contains("COD") || name.contains("PUFFERFISH") || name.contains("TROPICAL_FISH") || name.contains("AXOLOTL") || name.contains("TADPOLE"));
    }

    private Material displayIconForFloor(Material floor) {
        if (floor == Material.WATER) return Material.WATER_BUCKET;
        if (floor == Material.LAVA) return Material.LAVA_BUCKET;
        // If the floor material isn't an item (just in case), show a barrier icon
        try {
            if (!floor.isItem()) return Material.BARRIER;
        } catch (Throwable ignored) {}
        return floor;
    }

    public void applyEnvForPlot(Plot plot) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plot.containsLocation(p.getLocation())) {
                applyEnvForPlayer(p);
            }
        }
    }

    private void applyEnvForPlayer(Player player) {
        Plot inside = findPlotByLocation(player.getLocation());
        if (inside == null) {
            // reset to server defaults if previously overridden
            player.resetPlayerTime();
            player.resetPlayerWeather();
            return;
        }
        PlotEnvironmentState env = plotEnv.get(inside.getOwnerId());
        if (env != null) {
            if (env.timeTicks != null) player.setPlayerTime(env.timeTicks, false);
            if (env.weather != null) player.setPlayerWeather(env.weather);
            if (env.biome != null) sendBiomePatch(player, inside, env.biome);
        } else {
            player.resetPlayerTime();
            player.resetPlayerWeather();
        }
    }

    private Plot findPlotByLocation(Location loc) {
        if (loc == null) return null;
        // We only track owner plots; iterate
        for (UUID owner : plotEnv.keySet()) {
            Plot p = plotManager.getPlotByOwner(owner);
            if (p != null && p.containsLocation(loc)) return p;
        }
        // Fallback: also check player's own plot if any
        if (loc.getWorld() != null) {
            for (UUID owner : plugin.getPlotManager().getAllOwners()) {
                Plot p = plotManager.getPlotByOwner(owner);
                if (p != null && p.containsLocation(loc)) return p;
            }
        }
        return null;
    }

    private void tickParticlesAndEnv() {
        // particles only for players within the same plot
        for (Map.Entry<UUID, PlotEnvironmentState> e : plotEnv.entrySet()) {
            Plot plot = plotManager.getPlotByOwner(e.getKey());
            if (plot == null) continue;
            PlotEnvironmentState env = e.getValue();
            if (env.biome != null) {
                // keep biome visuals updated for players currently on plot
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (plot.containsLocation(p.getLocation())) sendBiomePatch(p, plot, env.biome);
                }
            }
            if (env.particleCenters.isEmpty()) continue;
            List<Player> viewers = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plot.containsLocation(p.getLocation())) viewers.add(p);
            }
            if (viewers.isEmpty()) continue;
            for (ParticleCenter pc : env.particleCenters) {
                for (Player viewer : viewers) {
                    viewer.spawnParticle(pc.particle, pc.location, 3, 0.1, 0.2, 0.1, 0.01);
                }
            }
            // Optionally, thunder SFX if storm
            if (env.storm && Math.random() < 0.05 && !viewers.isEmpty()) {
                Player any = viewers.get(0);
                any.playSound(plot.getViewLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.0f);
            }
        }
    }

    private void placeParticleItem(Inventory inv, int slot, Particle particle, Material icon, String name) {
        ItemStack item = ItemFactory.named(icon, name, List.of(ChatColor.GRAY + "ЛКМ в руке — поставить точку"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(particleTypeKey, PersistentDataType.STRING, particle.name());
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    public static void fillBackground(Inventory inv) {
        ItemStack pane = ItemFactory.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "", null);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack prev = inv.getItem(i);
            if (prev == null || prev.getType() == Material.AIR) {
                inv.setItem(i, pane);
            }
        }
    }

    private String neat(Material material) {
        return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void openLater(Player player, Inventory inv) {
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inv));
    }

    // ---------- Build tool protection ----------
    private boolean isBuildTool(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(toolKey, PersistentDataType.INTEGER);
    }

    private int firstEmptyExcept(Inventory inv, int except) {
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            if (i == except) continue;
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() == Material.AIR) return i;
        }
        return -1;
    }

    private void handleToolProtectionOnClick(Player player, InventoryClickEvent event) {
        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;
        // Only protect in player inventory space
        if (event.getView().getBottomInventory().equals(clickedInv)) {
            ItemStack current = event.getCurrentItem();
            ItemStack cursor = event.getCursor();
            // Prevent dropping of tool via click types
            if (isBuildTool(current) && (event.getClick().isKeyboardClick() || event.getClick().isCreativeAction())) {
                event.setCancelled(true);
                return;
            }
            // If replacing tool with cursor item, move tool to a free slot
            if (isBuildTool(current) && cursor != null && cursor.getType() != Material.AIR) {
                int free = firstEmptyExcept(clickedInv, event.getSlot());
                if (free != -1) {
                    clickedInv.setItem(free, current);
                    // allow the cursor item to be placed into this slot by not cancelling
                } else {
                    // no space; cancel to prevent loss
                    event.setCancelled(true);
                }
            }
        }
    }

    private void enforceToolPresence(Player player) {
        Inventory inv = player.getInventory();
        int foundIndex = -1;
        int count = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isBuildTool(it)) {
                count++;
                if (foundIndex == -1) foundIndex = i; else {
                    // remove duplicates
                    inv.setItem(i, null);
                }
            }
        }
        if (count == 0) {
            giveBuildTool(player);
        }
    }

    // Expose small helpers for click handlers
    public NamespacedKey getParticleTypeKey() { return particleTypeKey; }
    public NamespacedKey getParticleEraserKey() { return particleEraserKey; }
    public List<List<Material>> getGradients() { return GRADIENTS; }
    public PlayerBuildState getOrCreateState(Player p) { return playerState.computeIfAbsent(p.getUniqueId(), id -> new PlayerBuildState()); }
    public void applyTime(Player player, int ticks) {
        Plot plot = plotManager.getPlotByOwner(player.getUniqueId());
        if (plot == null) return;
        PlotEnvironmentState env = plotEnv.computeIfAbsent(plot.getOwnerId(), id -> new PlotEnvironmentState());
        env.timeTicks = ticks;
        // Ставим время локально всем, кто на плоте, и серверное время для консистентности эффектов
        applyEnvForPlot(plot);
        plot.getSpawnLocation().getWorld().setTime(ticks);
        player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.GOLD + "Время обновлено"));
    }
    public void applyWeather(Player player, WeatherType wt, boolean storm) {
        Plot plot = plotManager.getPlotByOwner(player.getUniqueId());
        if (plot == null) return;
        PlotEnvironmentState env = plotEnv.computeIfAbsent(plot.getOwnerId(), id -> new PlotEnvironmentState());
        env.weather = wt;
        env.storm = storm;
        // Ставим локально всем на плоте и синхронизируем мир, чтобы были капли дождя/гром
        applyEnvForPlot(plot);
        var world = plot.getSpawnLocation().getWorld();
        if (world != null) {
            if (wt == WeatherType.CLEAR) {
                world.setStorm(false);
                world.setThundering(false);
            } else {
                world.setStorm(true);
                world.setThundering(storm);
            }
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.AQUA + "Погода обновлена"));
    }

    public void applyBiome(Player player, Biome biome) {
        Plot plot = plotManager.getPlotByOwner(player.getUniqueId());
        if (plot == null) return;
        UUID owner = plot.getOwnerId();
        PlotEnvironmentState env = plotEnv.computeIfAbsent(owner, id -> new PlotEnvironmentState());
        env.biome = biome;
        applyEnvForPlot(plot);
        player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.GREEN + "Биом обновлён"));
    }

    // Send biome update by setting biomes in the inner build area. Client refreshes visuals as chunks stream.
    private void sendBiomePatch(Player player, Plot plot, Biome biome) {
        try {
            World world = plot.getSpawnLocation().getWorld();
            if (world == null) return;
            int innerMinX = plot.getMinX() + 3;
            int innerMinZ = plot.getMinZ() + 3;
            int size = NeoBuildBattleCore.getInstance().getConfig().getInt("build-area-size", 65);
            int baseY = plot.getSpawnLocation().getBlockY();
            int minY = Math.max(world.getMinHeight(), baseY - 2);
            int maxY = Math.min(world.getMaxHeight() - 1, baseY + 6);
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        world.setBiome(innerMinX + x, y, innerMinZ + z, biome);
                    }
                }
            }
            // Hint the client to refresh
            player.sendBlockChange(plot.getSpawnLocation(), world.getBlockAt(plot.getSpawnLocation()).getBlockData());
        } catch (Throwable ignored) { }
    }

    private void applyBiomeToPlotInternal(Plot plot, Biome biome) {
        World world = plot.getSpawnLocation().getWorld();
        if (world == null) return;
        int innerMinX = plot.getMinX() + 3;
        int innerMinZ = plot.getMinZ() + 3;
        int size = NeoBuildBattleCore.getInstance().getConfig().getInt("build-area-size", 65);
        int baseY = plot.getSpawnLocation().getBlockY();
        int minY = Math.max(world.getMinHeight(), baseY - 2);
        int maxY = Math.min(world.getMaxHeight() - 1, baseY + 6);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                for (int y = minY; y <= maxY; y++) {
                    world.setBiome(innerMinX + x, y, innerMinZ + z, biome);
                }
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (isBuildTool(stack)) {
            event.setCancelled(true);
        }
    }

    private Location frontBlockCenter(Player player) {
        Location eye = player.getEyeLocation();
        Location loc = eye.clone().add(eye.getDirection().normalize());
        return new Location(player.getWorld(), Math.floor(loc.getX()) + 0.5, Math.floor(loc.getY()), Math.floor(loc.getZ()) + 0.5);
    }

    private ParticleCenter raycastParticleCenter(Player player, List<ParticleCenter> centers, double maxDistance, double tolerance) {
        if (centers.isEmpty()) return null;
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection().normalize();
        ParticleCenter best = null;
        double bestT = Double.MAX_VALUE;
        for (ParticleCenter c : centers) {
            if (!eye.getWorld().equals(c.location.getWorld())) continue;
            org.bukkit.util.Vector oc = c.location.toVector().subtract(eye.toVector());
            double t = oc.dot(dir); // distance along the ray
            if (t < 0 || t > maxDistance) continue;
            // perpendicular distance^2
            org.bukkit.util.Vector closest = eye.toVector().add(dir.clone().multiply(t));
            double dist2 = c.location.toVector().distanceSquared(closest);
            if (dist2 <= tolerance * tolerance && t < bestT) {
                bestT = t;
                best = c;
            }
        }
        return best;
    }

    private Particle safeParticle(String name) {
        try { return Particle.valueOf(name); } catch (Throwable t) { return null; }
    }

    public static final class PlayerBuildState {
        public Material floorMaterial;
        public int gradientPage = 0;
    }

    private static final class PlotEnvironmentState {
        Integer timeTicks; // null => default
        WeatherType weather; // null => default
        boolean storm;
        final List<ParticleCenter> particleCenters = new ArrayList<>();
        Biome biome;
    }

    private static final class ParticleCenter {
        final Location location;
        final Particle particle;
        ParticleCenter(Location location, Particle particle) { this.location = location.clone(); this.particle = particle; }
    }

    // ---------- Gradients loading ----------
    public void reloadGradients() {
        GRADIENTS.clear();
        try {
            java.io.File file = new java.io.File(plugin.getDataFolder(), "gradients.yml");
            if (!file.exists()) {
                // ensure default shipped resource is saved on first run
                plugin.saveResource("gradients.yml", false);
            }
            org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            java.util.List<?> list = cfg.getList("gradients");
            if (list != null) {
                for (Object entry : list) {
                    if (entry instanceof java.util.List<?>) {
                        java.util.List<?> names = (java.util.List<?>) entry;
                        java.util.List<Material> mats = new java.util.ArrayList<>();
                        for (Object n : names) {
                            if (n != null) {
                                Material mat = Material.matchMaterial(String.valueOf(n));
                                if (mat != null) mats.add(mat);
                            }
                            if (mats.size() >= 8) break;
                        }
                        if (!mats.isEmpty()) GRADIENTS.add(mats);
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to load gradients.yml: " + t.getMessage());
        }
        if (GRADIENTS.isEmpty()) {
            // sane fallback
            GRADIENTS.add(java.util.List.of(
                    Material.BLACK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.WHITE_CONCRETE,
                    Material.WHITE_TERRACOTTA, Material.QUARTZ_BLOCK, Material.SMOOTH_QUARTZ, Material.DIORITE
            ));
        }
    }
}


