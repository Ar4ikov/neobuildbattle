package com.neobuildbattle.core.build.tools;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.build.clipboard.ClipboardService;
import com.neobuildbattle.core.build.fill.FillAlgorithm;
import com.neobuildbattle.core.build.fill.HollowFill;
import com.neobuildbattle.core.build.fill.SolidFill;
import com.neobuildbattle.core.build.fill.area.AreaIterators;
import com.neobuildbattle.core.build.pattern.BlockPattern;
import com.neobuildbattle.core.build.selection.Selection;
import com.neobuildbattle.core.build.selection.SelectionMode;
import com.neobuildbattle.core.build.selection.SelectionService;
import com.neobuildbattle.core.plot.Plot;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wires selection service, fill algorithms and clipboard operations.
 */
public final class AdvancedToolsManager implements Listener {
    private final NeoBuildBattleCore plugin;
    private final SelectionService selectionService;
    private final ClipboardService clipboardService;
    private final NamespacedKey toolKey;
    private final Map<UUID, BlockPattern> patterns = new HashMap<>();

    public AdvancedToolsManager(NeoBuildBattleCore plugin, SelectionService selectionService, ClipboardService clipboardService) {
        this.plugin = plugin;
        this.selectionService = selectionService;
        this.clipboardService = clipboardService;
        this.toolKey = new NamespacedKey(plugin, "adv_tool_kind");
    }

    public enum ToolKind { FILL_SOLID, FILL_HOLLOW, FILL_WALLS, COPY, PASTE, PASTE_AIR_TOGGLE }

    public ItemStack createToolItem(ToolKind kind, Material icon, String name) {
        ItemStack it = new ItemStack(icon);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, kind.name());
        it.setItemMeta(meta);
        return it;
    }

    public BlockPattern getOrCreatePattern(Player p) {
        return patterns.computeIfAbsent(p.getUniqueId(), id -> new BlockPattern());
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = e.getItem();
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        String kind = meta.getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        if (kind == null) return;
        Player p = e.getPlayer();
        ToolKind tk;
        try { tk = ToolKind.valueOf(kind); } catch (Throwable t) { return; }
        boolean sneaking = p.isSneaking();
        Action action = e.getAction();
        // transform предметы будут выделены отдельно — убираем шорткаты с кликов
        e.setCancelled(true);
        switch (tk) {
            case FILL_SOLID -> performFill(p, new SolidFill());
            case FILL_HOLLOW -> performFill(p, new HollowFill());
            case FILL_WALLS -> performFill(p, new com.neobuildbattle.core.build.fill.WallFill());
            case COPY -> performCopy(p);
            case PASTE -> clipboardService.pasteAtFeet(p);
            case PASTE_AIR_TOGGLE -> clipboardService.setPasteAir(p, !clipboardService.isPasteAir(p));
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        ItemStack main = e.getMainHandItem();
        if (main == null) return;
        ItemMeta meta = main.getItemMeta();
        if (meta == null) return;
        String kind = meta.getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        if (kind == null) return;
        if (!e.getPlayer().isSneaking()) return;
        if (!ToolKind.PASTE.name().equals(kind)) return;
        clipboardService.mirrorVertical(e.getPlayer());
        e.setCancelled(true);
    }

    private void performCopy(Player p) {
        Selection s = selectionService.getSelection(p);
        if (s.getPoints().size() < 2) {
            p.sendActionBar(net.kyori.adventure.text.Component.text(org.bukkit.ChatColor.RED + "Выделите 2 точки"));
            return;
        }
        clipboardService.copyFromSelection(p, s.getPoints().get(0), s.getPoints().get(1));
    }

    private void performFill(Player p, FillAlgorithm algo) {
        Selection s = selectionService.getSelection(p);
        if (s.getPoints().isEmpty()) {
            p.sendActionBar(net.kyori.adventure.text.Component.text(org.bukkit.ChatColor.RED + "Нет выделения"));
            return;
        }
        World w = p.getWorld();
        BlockPattern pattern = getOrCreatePattern(p);
        // Clip to inner play area
        var plot = plugin.getPlotManager().getPlotByOwner(p.getUniqueId());
        int innerMinX = plot != null ? plot.getMinX() + 3 : Integer.MIN_VALUE;
        int innerMaxX = plot != null ? plot.getMaxX() - 3 : Integer.MAX_VALUE;
        int innerMinZ = plot != null ? plot.getMinZ() + 3 : Integer.MIN_VALUE;
        int innerMaxZ = plot != null ? plot.getMaxZ() - 3 : Integer.MAX_VALUE;
        int baseY = plot != null ? plot.getSpawnLocation().getBlockY() - 1 : Integer.MIN_VALUE;
        int maxBuild = plugin.getConfig().getInt("max-build-height", 40);
        int maxY = baseY + maxBuild;
        FillAlgorithm.MaterialProvider provider = (x, y, z) -> {
            if (x < innerMinX || x > innerMaxX || z < innerMinZ || z > innerMaxZ) return null;
            if (y < baseY || y > maxY) return null;
            return pattern.pickForHeight(x, y, z, baseY, maxY);
        };
        SelectionMode m = s.getMode();
        switch (m) {
            case CUBOID -> {
                Vector min = s.getMin();
                Vector max = s.getMax();
                if (min == null || max == null) return;
                var it = AreaIterators.cuboid(min, max);
                algo.fill(w, it, provider);
            }
            case SPHERE -> {
                if (s.getPoints().size() < 2) return;
                var it = AreaIterators.sphere(s.getPoints().get(0), s.getPoints().get(1).distance(s.getPoints().get(0)));
                algo.fill(w, it, provider);
            }
            case ELLIPSOID -> {
                if (s.getPoints().size() < 2) return;
                var it = AreaIterators.ellipsoid(s.getPoints().get(0), s.getPoints().get(1));
                algo.fill(w, it, provider);
            }
            case POLYGON -> {
                if (s.getPoints().size() < 3) {
                    p.sendActionBar(net.kyori.adventure.text.Component.text(org.bukkit.ChatColor.RED + "Добавьте точек: минимум 3"));
                    return;
                }
                var it = AreaIterators.polygon(s.getPoints());
                algo.fill(w, it, provider);
            }
            case POLYFRAME -> {
                // каркас полигона: только рёбра и вершины (толщина ~1)
                if (s.getPoints().size() < 2) return;
                var pts = s.getPoints();
                for (int i = 0; i < pts.size(); i++) {
                    var a = pts.get(i);
                    var b = pts.get((i + 1) % pts.size());
                    drawEdge(w, a, b, provider);
                }
            }
        }
    }

    private void drawEdge(World w, org.bukkit.Location a, org.bukkit.Location b, FillAlgorithm.MaterialProvider provider) {
        org.bukkit.util.Vector start = a.toVector();
        org.bukkit.util.Vector end = b.toVector();
        org.bukkit.util.Vector dir = end.clone().subtract(start);
        double len = dir.length();
        if (len <= 0.0001) return;
        dir.multiply(1.0 / len);
        for (double d = 0.0; d <= len; d += 0.5) {
            org.bukkit.util.Vector p = start.clone().add(dir.clone().multiply(d));
            int x = (int) Math.floor(p.getX());
            int y = (int) Math.floor(p.getY());
            int z = (int) Math.floor(p.getZ());
            Material m = provider.nextFor(x, y, z);
            if (m != null) w.getBlockAt(x, y, z).setType(m, false);
        }
    }
}


