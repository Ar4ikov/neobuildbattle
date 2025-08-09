package com.neobuildbattle.core.build.click;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.build.gui.BuildGuiHolder;
import com.neobuildbattle.core.build.gui.GuiType;
import com.neobuildbattle.core.build.pattern.BlockPattern;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public final class PatternClickHandler implements GuiClickHandler {
    private final NeoBuildBattleCore plugin = NeoBuildBattleCore.getInstance();

    @Override
    public void handle(Player player, int rawSlot, InventoryClickEvent event) {
        Inventory inv = event.getView().getTopInventory();
        if (!(inv.getHolder() instanceof BuildGuiHolder holder) || holder.type != GuiType.PATTERN) return;

        BlockPattern pattern = plugin.getAdvancedToolsManager().getOrCreatePattern(player);
        if (isInner(rawSlot)) {
            event.setCancelled(true);
            ItemStack cursor = event.getCursor();
            ItemStack current = event.getCurrentItem();
            int delta = 0;
            boolean left = event.getClick() == ClickType.LEFT;
            boolean right = event.getClick() == ClickType.RIGHT;
            if (!left && !right) return;
            // prefer cursor-driven adjustments
            if (cursor != null && cursor.getType() != Material.AIR) {
                int amt = Math.max(1, cursor.getAmount());
                delta = left ? amt : -amt;
                if (isGradientToken(cursor)) {
                    pattern.setGradientWeight(Math.max(0, pattern.getGradientWeight() + delta));
                } else if (cursor.getType().isBlock() || cursor.getType() == Material.BARRIER) {
                    Material target = cursor.getType() == Material.BARRIER ? Material.AIR : cursor.getType();
                    // Фильтр неполных блоков
                    if (target != Material.AIR) {
                        String n = target.name();
                        if (n.endsWith("_SLAB") || n.endsWith("_STAIRS") || n.contains("WALL") || n.contains("FENCE") || n.contains("PANE")) return;
                    }
                    int cur = pattern.getWeightsView().getOrDefault(target, 0);
                    pattern.setWeight(target, Math.max(0, cur + delta));
                }
            } else if (current != null) {
                // empty cursor: adjust by 1 using current slot material
                delta = left ? 1 : -1;
                Material target;
                if (current.getType() == Material.BARRIER) target = Material.AIR; else target = current.getType();
                if (current.getType() == Material.PAPER && isGradientToken(current)) {
                    pattern.setGradientWeight(Math.max(0, pattern.getGradientWeight() + delta));
                } else if (target == Material.AIR || target.isBlock()) {
                    if (target != Material.AIR) {
                        String n = target.name();
                        if (n.endsWith("_SLAB") || n.endsWith("_STAIRS") || n.contains("WALL") || n.contains("FENCE") || n.contains("PANE")) return;
                    }
                    int cur = pattern.getWeightsView().getOrDefault(target, 0);
                    pattern.setWeight(target, Math.max(0, cur + delta));
                }
            }
            rerender(inv, pattern);
            return;
        }
        // bottom summary clicked -> no-op
        event.setCancelled(true);
    }

    private boolean isInner(int raw) {
        int r = raw / 9; int c = raw % 9;
        return r >= 1 && r <= 4 && c >= 1 && c <= 7;
    }

    private void rerender(Inventory inv, BlockPattern pattern) {
        // Clear inner
        for (int r = 1; r <= 4; r++) {
            for (int c = 1; c <= 7; c++) {
                inv.setItem(r * 9 + c, null);
            }
        }
        int idx = 0;
        for (Map.Entry<Material, Integer> e : pattern.getWeightsView().entrySet()) {
            if (idx >= 28) break;
            int slot = (1 + (idx / 7)) * 9 + (1 + (idx % 7));
            Material mat = e.getKey();
            ItemStack it;
            if (mat == Material.AIR) {
                it = new ItemStack(Material.BARRIER);
            } else {
                it = new ItemStack(mat);
            }
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                String name = (mat == Material.AIR ? ChatColor.GRAY + "Воздух" : ChatColor.WHITE + neat(mat));
                meta.setDisplayName(name + ChatColor.GRAY + " x" + e.getValue());
                it.setItemMeta(meta);
            }
            inv.setItem(slot, it);
            idx++;
        }
        // Air barrier lives permanently at slot 53 via PatternGui
        plugin.getPatternGui().updateSummary(inv, pattern);
    }

    private boolean isGradientToken(ItemStack it) {
        if (it.getType() != Material.PAPER) return false;
        var meta = it.getItemMeta(); if (meta == null) return false;
        String name = meta.getDisplayName();
        if (name == null) return false;
        String p1 = ChatColor.BLUE + "Градиент: ";
        String p2 = ChatColor.BLUE + "Градиент";
        return name.startsWith(p1) || name.startsWith(p2);
    }

    private String neat(Material m) { return m.name().toLowerCase().replace('_', ' '); }
}


