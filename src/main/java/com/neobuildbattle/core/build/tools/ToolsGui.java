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
        // Row 2: selection axe, solid fill, hollow fill
        inv.setItem(20, ItemFactory.named(Material.IRON_AXE, ChatColor.AQUA + "Топор выделения", List.of(ChatColor.GRAY + "Shift+ЛКМ/ПКМ — режим")));
        inv.setItem(21, ItemFactory.named(Material.ORANGE_CONCRETE, ChatColor.GOLD + "Сплошная заливка", List.of(ChatColor.GRAY + "Заполнить выделение")));
        inv.setItem(22, ItemFactory.named(Material.HONEYCOMB_BLOCK, ChatColor.YELLOW + "Заливка с полостью", List.of(ChatColor.GRAY + "Только грани")));
        inv.setItem(23, ItemFactory.named(Material.BRICKS, ChatColor.YELLOW + "Заливка стен", List.of(ChatColor.GRAY + "Только стены")));
        inv.setItem(24, ItemFactory.named(Material.PAINTING, ChatColor.BLUE + "Градиент пипетка", List.of(ChatColor.GRAY + "Клик по блоку")));
        // Row 3: copy, paste, toggle paste-air
        inv.setItem(29, ItemFactory.named(Material.PAPER, ChatColor.GREEN + "Копировать", List.of(ChatColor.GRAY + "Скопировать выделение")));
        inv.setItem(30, ItemFactory.named(Material.BOOK, ChatColor.GREEN + "Вставить", List.of(ChatColor.GRAY + "Вставить у ног")));
        inv.setItem(31, ItemFactory.named(Material.GLASS, ChatColor.AQUA + "Вставлять воздух", List.of(ChatColor.GRAY + "Переключить")));
        inv.setItem(32, ItemFactory.named(Material.COMPASS, ChatColor.YELLOW + "Повернуть 90° CW", List.of(ChatColor.GRAY + "Повернуть буфер")));
        inv.setItem(33, ItemFactory.named(Material.RECOVERY_COMPASS, ChatColor.YELLOW + "Повернуть 90° CCW", List.of(ChatColor.GRAY + "Повернуть буфер")));
        inv.setItem(34, ItemFactory.named(Material.MAP, ChatColor.AQUA + "Зеркало по горизонтали", List.of(ChatColor.GRAY + "Относительно точки копирования")));
        inv.setItem(35, ItemFactory.named(Material.CHEST, ChatColor.YELLOW + "Редактор паттерна", List.of(ChatColor.GRAY + "Открыть")));
        return inv;
    }
}


