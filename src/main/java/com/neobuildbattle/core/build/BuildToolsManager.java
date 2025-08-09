package com.neobuildbattle.core.build;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.build.gui.BuildGuiHolder;
import com.neobuildbattle.core.build.gui.GuiType;
import com.neobuildbattle.core.build.gui.MainGui;
import com.neobuildbattle.core.build.ui.ItemFactory;
import com.neobuildbattle.core.game.GameManager;
import com.neobuildbattle.core.game.GameState;
import com.neobuildbattle.core.plot.Plot;
import com.neobuildbattle.core.plot.PlotManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
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
            // Open main GUI on any click while holding tool
            if (ensureIsBuilding(player)) {
                event.setCancelled(true);
                openMainGui(player);
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
        if (!ensureIsBuilding(player)) return;
        applyEnvForPlayer(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // In case of mid-game join as spectator, ignore; otherwise state bootstrap
        playerState.computeIfAbsent(event.getPlayer().getUniqueId(), id -> new PlayerBuildState());
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
                    if (cursor != null && cursor.getType().isBlock()) {
                        event.setCancelled(true);
                        setFloorTo(player, cursor.getType());
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
            inv.setItem(10, ItemFactory.named(st.floorMaterial, ChatColor.GREEN + "Пол: " + neat(st.floorMaterial), List.of(ChatColor.GRAY + "Перетащите новый блок, чтобы изменить")));
        }

        openLater(player, inv);
    }

    private void openToolsGui(Player player) {
        Inventory inv = Bukkit.createInventory(new BuildGuiHolder(GuiType.TOOLS), 54, ChatColor.YELLOW + "Инструменты");
        fillBackground(inv);
        // Placeholder content for now
        inv.setItem(22, ItemFactory.named(Material.BARRIER, ChatColor.RED + "В разработке", List.of(ChatColor.GRAY + "Скоро")));
        openLater(player, inv);
    }

    private void openTimeGui(Player player) {
        Inventory inv = Bukkit.createInventory(new BuildGuiHolder(GuiType.TIME), 36, ChatColor.GOLD + "Выбор времени");
        fillBackground(inv);
        // Arrange times
        String[] labels = {"0:00", "3:00", "6:00", "9:00", "12:00", "15:00", "18:00", "21:00"};
        int[] slots = {11, 12, 13, 14, 15, 22, 23, 24};
        for (int i = 0; i < labels.length; i++) {
            inv.setItem(slots[i], ItemFactory.named(Material.CLOCK, ChatColor.YELLOW + labels[i], null));
        }
        openLater(player, inv);
    }

    private void openWeatherGui(Player player) {
        Inventory inv = Bukkit.createInventory(new BuildGuiHolder(GuiType.WEATHER), 27, ChatColor.AQUA + "Погода");
        fillBackground(inv);
        inv.setItem(11, ItemFactory.named(Material.SUNFLOWER, ChatColor.YELLOW + "Ясно", null));
        inv.setItem(13, ItemFactory.named(Material.WATER_BUCKET, ChatColor.BLUE + "Дождь", null));
        inv.setItem(15, ItemFactory.named(Material.LIGHTNING_ROD, ChatColor.DARK_BLUE + "Шторм", null));
        openLater(player, inv);
    }

    private void openParticlesGui(Player player) {
        Inventory inv = Bukkit.createInventory(new BuildGuiHolder(GuiType.PARTICLES), 54, ChatColor.LIGHT_PURPLE + "Партиклы");
        fillBackground(inv);
        // Center row with items (infinite)
        placeParticleItem(inv, 20, Particle.HAPPY_VILLAGER, Material.EMERALD, ChatColor.GREEN + "Жители");
        placeParticleItem(inv, 21, Particle.HEART, Material.POPPY, ChatColor.RED + "Сердечки");
        placeParticleItem(inv, 22, Particle.FLAME, Material.BLAZE_POWDER, ChatColor.GOLD + "Огонь");
        placeParticleItem(inv, 23, Particle.LARGE_SMOKE, Material.CAMPFIRE, ChatColor.GRAY + "Дым");
        placeParticleItem(inv, 24, Particle.POOF, Material.TNT, ChatColor.DARK_RED + "Взрывы");
        placeParticleItem(inv, 29, Particle.WITCH, Material.POTION, ChatColor.DARK_PURPLE + "Ведьма");
        placeParticleItem(inv, 30, Particle.SOUL, Material.SOUL_SOIL, ChatColor.AQUA + "Души");
        placeParticleItem(inv, 31, Particle.SOUL_FIRE_FLAME, Material.SOUL_TORCH, ChatColor.BLUE + "Синий огонь");
        // right bottom slot milk bucket eraser
        ItemStack eraser = ItemFactory.named(Material.MILK_BUCKET, ChatColor.WHITE + "Удаление точки", List.of(ChatColor.GRAY + "Кликните в мире по точке, чтобы удалить"));
        ItemMeta meta = eraser.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(particleEraserKey, PersistentDataType.INTEGER, 1);
            eraser.setItemMeta(meta);
        }
        inv.setItem(53, eraser);

        openLater(player, inv);
    }

    private void openGradientsGui(Player player) {
        PlayerBuildState st = playerState.computeIfAbsent(player.getUniqueId(), id -> new PlayerBuildState());
        int page = Math.max(0, Math.min(st.gradientPage, Math.max(0, GRADIENTS.size() - 1)));
        Inventory inv = Bukkit.createInventory(new BuildGuiHolder(GuiType.GRADIENTS), 54, ChatColor.BLUE + "Градиенты (" + (page + 1) + "/" + Math.max(1, GRADIENTS.size()) + ")");
        fillBackground(inv);
        // fill rows with multiple gradients until pagination row
        int writeIndex = 0; // 0..44 usable (last row 45..53 reserved for pagination)
        int g = page;
        while (writeIndex < 45 && g < GRADIENTS.size()) {
            List<Material> mats = GRADIENTS.get(g);
            for (int i = 0; i < Math.min(8, mats.size()) && writeIndex < 45; i++) {
                inv.setItem(writeIndex++, ItemFactory.named(mats.get(i), ChatColor.AQUA + "Блок " + (i + 1), null));
            }
            if (writeIndex < 45) {
                inv.setItem(writeIndex++, ItemFactory.named(Material.PAPER, ChatColor.GREEN + "Взять на хотбар", List.of(ChatColor.GRAY + "Слоты 1-8")));
            }
            g++;
        }
        // pagination on last row (centered-ish at 47/51)
        if (page > 0) inv.setItem(47, ItemFactory.named(Material.PAPER, ChatColor.YELLOW + "Страница назад", null));
        if (page < GRADIENTS.size()) inv.setItem(51, ItemFactory.named(Material.PAPER, ChatColor.YELLOW + "Страница вперед", null));

        openLater(player, inv);
    }

    // ---------- Click handlers ----------
    private void handleMainClick(Player player, int slot, InventoryClickEvent event) {
        if (slot == 10) {
            // F slot: if clicking with a block on cursor, set as floor
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType().isBlock()) {
                setFloorTo(player, cursor.getType());
                openMainGui(player);
            } else {
                // Or click on existing block icon to apply it
                ItemStack current = event.getCurrentItem();
                if (current != null && current.getType().isBlock()) {
                    setFloorTo(player, current.getType());
                    openMainGui(player);
                }
            }
        } else if (slot == 11) {
            openToolsGui(player);
        } else if (slot == 12) {
            openTimeGui(player);
        } else if (slot == 14) {
            openWeatherGui(player);
        } else if (slot == 15) {
            openParticlesGui(player);
        } else if (slot == 16) {
            openGradientsGui(player);
        }
    }

    private void handleTimeClick(Player player, int slot) {
        Map<Integer, Integer> map = Map.of(
                11, 18000, // 0:00
                12, 21000, // 3:00
                13, 0,     // 6:00
                14, 3000,  // 9:00
                15, 6000,  // 12:00
                22, 9000,  // 15:00
                23, 12000, // 18:00
                24, 15000  // 21:00
        );
        Integer ticks = map.get(slot);
        if (ticks == null) return;
        Plot plot = plotManager.getPlotByOwner(player.getUniqueId());
        if (plot == null) return;
        PlotEnvironmentState env = plotEnv.computeIfAbsent(plot.getOwnerId(), id -> new PlotEnvironmentState());
        env.timeTicks = ticks;
        applyEnvForPlot(plot);
        player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.GOLD + "Время обновлено"));
    }

    private void handleWeatherClick(Player player, int slot) {
        Plot plot = plotManager.getPlotByOwner(player.getUniqueId());
        if (plot == null) return;
        PlotEnvironmentState env = plotEnv.computeIfAbsent(plot.getOwnerId(), id -> new PlotEnvironmentState());
        if (slot == 11) {
            env.weather = WeatherType.CLEAR;
            env.storm = false;
        } else if (slot == 13) {
            env.weather = WeatherType.DOWNFALL;
            env.storm = false;
        } else if (slot == 15) {
            env.weather = WeatherType.DOWNFALL;
            env.storm = true;
        } else {
            return;
        }
        applyEnvForPlot(plot);
        player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.AQUA + "Погода обновлена"));
    }

    private void handleParticlesClick(Player player, int slot) {
        // Clicking on particle icon should give an infinite tool item (we simply add a copy)
        ItemStack clicked = player.getOpenInventory().getTopInventory().getItem(slot);
        if (clicked == null) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(particleTypeKey, PersistentDataType.STRING) || pdc.has(particleEraserKey, PersistentDataType.INTEGER)) {
            player.getInventory().addItem(clicked.clone());
            player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.LIGHT_PURPLE + "Инструмент добавлен"));
        }
    }

    private void handleGradientsClick(Player player, int slot) {
        PlayerBuildState st = playerState.computeIfAbsent(player.getUniqueId(), id -> new PlayerBuildState());
        int page = st.gradientPage;
        // hotbar grab button is at index 8 of each gradient row block (0..44), i.e. any slot where slot % 9 == 8 and slot < 45
        if (slot < 45 && slot % 9 == 8) {
            int gIndex = page + (slot / 9);
            if (gIndex >= 0 && gIndex < GRADIENTS.size()) {
                List<Material> mats = GRADIENTS.get(gIndex);
                for (int i = 0; i < Math.min(8, mats.size()); i++) {
                    player.getInventory().setItem(i, new ItemStack(mats.get(i)));
                }
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.GREEN + "Выдано на хотбар"));
                return;
            }
        }
        int prevSlot = 47;
        int nextSlot = 51;
        if (slot == prevSlot && page > 0) {
            st.gradientPage = page - 1;
            Bukkit.getScheduler().runTask(plugin, () -> openGradientsGui(player));
        } else if (slot == nextSlot && page < GRADIENTS.size()) {
            st.gradientPage = page + 1;
            Bukkit.getScheduler().runTask(plugin, () -> openGradientsGui(player));
        }
    }

    // ---------- Helpers ----------
    private boolean ensureIsBuilding(Player player) {
        GameManager gm = plugin.getGameManager();
        return gm != null && gm.getState() == GameState.BUILDING;
    }

    private void setFloorTo(Player player, Material material) {
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
        for (int zOff = 0; zOff < buildArea; zOff += rowsPerTick) {
            final int zStart = zOff;
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int dz = 0; dz < rowsPerTick && zStart + dz < buildArea; dz++) {
                    int z = startZ + zStart + dz;
                    for (int x = startX; x < startX + buildArea; x++) {
                        world.getBlockAt(x, baseY, z).setType(material, false);
                    }
                }
            });
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.GREEN + "Пол изменен: " + neat(material)));
    }

    private void applyEnvForPlot(Plot plot) {
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
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
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

    private static final class PlayerBuildState {
        Material floorMaterial;
        int gradientPage = 0;
    }

    private static final class PlotEnvironmentState {
        Integer timeTicks; // null => default
        WeatherType weather; // null => default
        boolean storm;
        final List<ParticleCenter> particleCenters = new ArrayList<>();
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


