package com.neobuildbattle.core.build.pattern;

import com.neobuildbattle.core.build.gui.BuildGuiHolder;
import com.neobuildbattle.core.build.gui.GuiType;
import com.neobuildbattle.core.build.ui.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 6x9 chest with 4x7 inner area bordered by gray panes. Bottom middle shows computed pattern summary.
 */
public final class PatternGui {
    public Inventory create(Player p, BlockPattern pattern) {
        Inventory inv = Bukkit.createInventory(new BuildGuiHolder(GuiType.PATTERN), 54, ChatColor.YELLOW + "Паттерн заполнения");
        // Border
        ItemStack pane = ItemFactory.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "", null);
        for (int r = 1; r <= 4; r++) {
            for (int c = 1; c <= 7; c++) {
                int slot = r * 9 + c;
                inv.setItem(slot, new ItemStack(Material.AIR));
            }
        }
        // Outline border with panes (полная рамка)
        for (int i = 0; i < 9; i++) { inv.setItem(0 + i, pane); inv.setItem(45 + i, pane); }
        for (int r = 0; r < 6; r++) { inv.setItem(r * 9 + 0, pane); inv.setItem(r * 9 + 8, pane); }
        // Place permanent Air-barrier control at bottom right (slot 53)
        ItemStack airIcon = ItemFactory.named(Material.BARRIER, ChatColor.GRAY + "Воздух", List.of(ChatColor.GRAY + "Перетащите в сетку, чтобы добавить"));
        inv.setItem(53, airIcon);

        // Summary paper at bottom middle (slot 49) - will be updated below
        inv.setItem(49, ItemFactory.named(Material.PAPER, ChatColor.GREEN + "Сводка", List.of(ChatColor.GRAY + "Перетаскивайте блоки внутрь", ChatColor.GRAY + "Барьер — воздух")));

        // Заполнить текущие элементы паттерна в сетку 4x7
        int idx = 0;
        if (pattern.getGradientWeight() > 0 && idx < 28) {
            int slot = (1 + (idx / 7)) * 9 + (1 + (idx % 7));
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.BLUE + "Градиент" + ChatColor.GRAY + " x" + pattern.getGradientWeight());
                paper.setItemMeta(meta);
            }
            inv.setItem(slot, paper);
            idx++;
        }
        for (var e : pattern.getWeightsView().entrySet()) {
            if (idx >= 28) break;
            Material mat = e.getKey() == Material.AIR ? Material.BARRIER : e.getKey();
            if (!mat.isBlock()) continue;
            // Игнорируем неполные блоки в исходной записи (подстрахуемся)
            String n = mat.name();
            if (n.endsWith("_SLAB") || n.endsWith("_STAIRS") || n.contains("WALL") || n.contains("FENCE") || n.contains("PANE")) continue;
            int slot = (1 + (idx / 7)) * 9 + (1 + (idx % 7));
            ItemStack it = new ItemStack(mat);
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                String name = (e.getKey() == Material.AIR ? ChatColor.GRAY + "Воздух" : ChatColor.WHITE + neat(e.getKey()));
                meta.setDisplayName(name + ChatColor.GRAY + " x" + e.getValue());
                it.setItemMeta(meta);
            }
            inv.setItem(slot, it);
            idx++;
        }
        // Also refresh summary to include percentages and gradient line
        updateSummary(inv, pattern);
        return inv;
    }

    public void updateSummary(Inventory inv, BlockPattern pattern) {
        // Build dynamic lore with percentages
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Распределение:");
        int total = pattern.getGradientWeight();
        for (var e : pattern.getWeightsView().entrySet()) total += Math.max(0, e.getValue());
        if (pattern.getGradientWeight() > 0) lore.add(ChatColor.BLUE + "Градиент: " + percent(pattern.getGradientWeight(), total));
        for (var e : pattern.getWeightsView().entrySet()) {
            int w = Math.max(0, e.getValue());
            if (w <= 0) continue;
            lore.add(ChatColor.WHITE + neat(e.getKey()) + ChatColor.GRAY + ": " + percent(w, total));
        }
        ItemStack paper = inv.getItem(49);
        if (paper == null || paper.getType() == Material.AIR) paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Сводка паттерна");
            meta.setLore(lore);
            paper.setItemMeta(meta);
        }
        inv.setItem(49, paper);
    }

    private String percent(int part, int sum) {
        if (sum <= 0) return "0%";
        int p = Math.max(0, Math.min(100, (int) Math.round(part * 100.0 / sum)));
        return p + "%";
    }

    private String neat(Material m) { return m.name().toLowerCase().replace('_', ' '); }
}


