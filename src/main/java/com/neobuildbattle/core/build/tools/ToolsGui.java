package com.neobuildbattle.core.build.tools;

import com.neobuildbattle.core.build.gui.BuildGuiHolder;
import com.neobuildbattle.core.build.gui.GuiType;
import com.neobuildbattle.core.build.ui.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import java.util.List;

public final class ToolsGui {
    private ToolsGui() {}

    public static Inventory render() {
        Inventory inv = Bukkit.createInventory(new BuildGuiHolder(GuiType.TOOLS), 54, ChatColor.YELLOW + "Инструменты");
        // Clear and set a soft frame background
        com.neobuildbattle.core.build.BuildToolsManager.fillBackground(inv);

        // Row 1 (centered): selection + fills group
        inv.setItem(10, ItemFactory.named(Material.IRON_AXE, ChatColor.AQUA + "Топор выделения", List.of(ChatColor.GRAY + "Shift+ЛКМ/ПКМ — смена режима")));
        inv.setItem(12, ItemFactory.named(Material.ORANGE_CONCRETE, ChatColor.GOLD + "Сплошная заливка", List.of(ChatColor.GRAY + "Заполнить выделение")));
        inv.setItem(13, ItemFactory.named(Material.HONEYCOMB_BLOCK, ChatColor.YELLOW + "Заливка с полостью", List.of(ChatColor.GRAY + "Только грани")));
        inv.setItem(14, ItemFactory.named(Material.BRICKS, ChatColor.YELLOW + "Заливка стен", List.of(ChatColor.GRAY + "Только внешние стены")));
        inv.setItem(16, ItemFactory.named(Material.CHEST, ChatColor.GOLD + "Редактор паттерна", List.of(ChatColor.GRAY + "Смешанные блоки и градиенты")));

        // Row 2: clipboard group
        inv.setItem(28, ItemFactory.named(Material.PAPER, ChatColor.GREEN + "Копировать", List.of(ChatColor.GRAY + "Копия по выделению", ChatColor.GRAY + "Точка копии — якорь")));
        inv.setItem(29, ItemFactory.named(Material.BOOK, ChatColor.GREEN + "Вставить", List.of(ChatColor.GRAY + "Вставить в точке якоря")));
        inv.setItem(31, ItemFactory.named(Material.GLASS, ChatColor.AQUA + "Вставлять воздух", List.of(ChatColor.GRAY + "Переключить")));
        inv.setItem(33, ItemFactory.named(Material.GOLDEN_PICKAXE, ChatColor.GOLD + "Замена блоков", List.of(ChatColor.GRAY + "ЛКМ — заменить по паттерну", ChatColor.GRAY + "ПКМ — маска замен", ChatColor.GRAY + "Shift+ПКМ — очистить маску")));

        // Row 3: transform group
        inv.setItem(37, ItemFactory.named(Material.COMPASS, ChatColor.YELLOW + "Повернуть 90° CW", List.of(ChatColor.GRAY + "Вокруг якоря")));
        inv.setItem(38, ItemFactory.named(Material.RECOVERY_COMPASS, ChatColor.YELLOW + "Повернуть 90° CCW", List.of(ChatColor.GRAY + "Вокруг якоря")));
        inv.setItem(40, ItemFactory.named(Material.MAP, ChatColor.AQUA + "Зеркало по горизонтали", List.of(ChatColor.GRAY + "Относительно якоря")));
        inv.setItem(41, ItemFactory.named(Material.FILLED_MAP, ChatColor.AQUA + "Зеркало по вертикали", List.of(ChatColor.GRAY + "Относительно якоря")));

        // Row 4: gradient tools
        inv.setItem(49, ItemFactory.named(Material.PAINTING, ChatColor.BLUE + "Пипетка градиента", List.of(ChatColor.GRAY + "Клик по блоку — токен")));

        return inv;
    }
}


