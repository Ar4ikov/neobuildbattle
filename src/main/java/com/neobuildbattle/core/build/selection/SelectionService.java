package com.neobuildbattle.core.build.selection;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.plot.Plot;
import com.neobuildbattle.core.plot.PlotManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Provides per-player selection state and interaction for the advanced axe tool.
 */
public final class SelectionService implements Listener {
    private final NeoBuildBattleCore plugin;
    private final PlotManager plotManager;
    private final NamespacedKey axeKey;

    private final Map<UUID, Selection> selections = new HashMap<>();
    // render ticked globally; no per-player throttling needed

    public SelectionService(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
        this.plotManager = plugin.getPlotManager();
        this.axeKey = new NamespacedKey(plugin, "advanced_axe");
        // periodic render
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::tickRender, 5L, 5L);
    }

    public ItemStack createAxeItem() {
        ItemStack axe = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = axe.getItemMeta();
        meta.setDisplayName(org.bukkit.ChatColor.AQUA + "Топор выделения (Shift+Клик — режим)");
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(axeKey, PersistentDataType.INTEGER, 1);
        axe.setItemMeta(meta);
        return axe;
    }

    public boolean isAxe(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(axeKey, PersistentDataType.INTEGER);
    }

    public Selection getSelection(Player p) {
        return selections.getOrDefault(p.getUniqueId(), new Selection(SelectionMode.CUBOID, java.util.List.of()));
    }

    public void clearSelection(Player p) {
        selections.remove(p.getUniqueId());
    }

    private void setSelection(Player p, Selection sel) {
        selections.put(p.getUniqueId(), sel);
    }

    // Interaction: LMB sets first point (with ray up to 80), RMB sets second (cuboid/ellipsoid/sphere) or adds point (polygon)
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return; // main hand only
        ItemStack item = e.getItem();
        if (!isAxe(item)) return;
        Player p = e.getPlayer();
        // Mode change via Shift + click on axe itself
        boolean shift = p.isSneaking();
        Action action = e.getAction();
        if (shift && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            cycleMode(p, action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
            e.setCancelled(true);
            return;
        }

        // Normal selection actions
        if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
            Location hit = findTargetLocation(p, 80.0);
            if (hit != null) {
                if (isWithinBuildOrBoundary(p, hit)) {
                    Selection cur = getSelection(p);
                    SelectionMode m = cur.getMode();
                    if (m == SelectionMode.POLYGON) {
                        setSelection(p, cur.withAddedPoint(hit));
                    } else {
                        setSelection(p, cur.withFirstPoint(hit));
                    }
                    e.setCancelled(true);
                }
            }
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            Location hit = findTargetLocation(p, 80.0);
            if (hit != null) {
                if (isWithinBuildOrBoundary(p, hit)) {
                    Selection cur = getSelection(p);
                    SelectionMode m = cur.getMode();
                    if (m == SelectionMode.POLYGON) {
                        setSelection(p, cur.withAddedPoint(hit));
                    } else {
                        setSelection(p, cur.withSecondPoint(hit));
                    }
                    e.setCancelled(true);
                }
            }
        }
    }

    private void cycleMode(Player p, boolean forward) {
        Selection cur = getSelection(p);
        SelectionMode[] values = SelectionMode.values();
        int idx = cur.getMode().ordinal();
        idx = forward ? (idx + 1) % values.length : (idx - 1 + values.length) % values.length;
        Selection next = new Selection(values[idx], java.util.List.of()); // reset points on switch
        setSelection(p, next);
        com.neobuildbattle.core.util.Sounds.playUiClick(p);
        p.sendActionBar(net.kyori.adventure.text.Component.text(org.bukkit.ChatColor.YELLOW + "Режим: " + values[idx].name()));
    }

    private void tickRender() {
        for (UUID id : selections.keySet()) {
            Player p = org.bukkit.Bukkit.getPlayer(id);
            if (p == null) continue;
            Selection sel = selections.get(id);
            // render densely every tick loop
            SelectionRenderer.render(p, sel);
        }
    }

    private Location findTargetLocation(Player p, double max) {
        World w = p.getWorld();
        RayTraceResult r = w.rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), max, org.bukkit.FluidCollisionMode.NEVER);
        if (r != null && r.getHitPosition() != null) {
            Vector v = r.getHitPosition();
            return new Location(w, Math.floor(v.getX()) + 0.5, Math.floor(v.getY()), Math.floor(v.getZ()) + 0.5);
        }
        return null;
    }

    private boolean isWithinBuildOrBoundary(Player p, Location loc) {
        Plot plot = plotManager.getPlotByOwner(p.getUniqueId());
        if (plot == null) return false;
        // Allow clicks on walls/ring to mark boundary but clamp placement to inner build area later.
        // Here we only check world equality and rough bounding box of entire plot size.
        if (!loc.getWorld().equals(plot.getSpawnLocation().getWorld())) return false;
        int minX = plot.getMinX();
        int minZ = plot.getMinZ();
        int maxX = plot.getMaxX();
        int maxZ = plot.getMaxZ();
        return loc.getBlockX() >= minX && loc.getBlockX() <= maxX && loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }
}


