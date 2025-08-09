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
        inv.setItem(22, ItemFactory.named(Material.BARRIER, ChatColor.RED + "В разработке", List.of(ChatColor.GRAY + "Скоро")));
        return inv;
    }
}


