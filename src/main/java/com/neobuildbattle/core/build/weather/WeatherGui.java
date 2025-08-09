package com.neobuildbattle.core.build.weather;

import com.neobuildbattle.core.build.gui.BuildGuiHolder;
import com.neobuildbattle.core.build.gui.GuiType;
import com.neobuildbattle.core.build.ui.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.WeatherType;
import org.bukkit.inventory.Inventory;

public final class WeatherGui {
    private WeatherGui() {}

    public static Inventory render() {
        Inventory inv = Bukkit.createInventory(new BuildGuiHolder(GuiType.WEATHER), 27, ChatColor.AQUA + "Погода");
        inv.setItem(11, ItemFactory.named(Material.SUNFLOWER, ChatColor.YELLOW + "Ясно", null));
        inv.setItem(13, ItemFactory.named(Material.WATER_BUCKET, ChatColor.BLUE + "Дождь", null));
        inv.setItem(15, ItemFactory.named(Material.LIGHTNING_ROD, ChatColor.DARK_BLUE + "Шторм", null));
        return inv;
    }

    public static WeatherType weatherForSlot(int slot) {
        return switch (slot) {
            case 11 -> WeatherType.CLEAR;
            case 13 -> WeatherType.DOWNFALL;
            case 15 -> WeatherType.DOWNFALL;
            default -> null;
        };
    }

    public static boolean isStorm(int slot) {
        return slot == 15;
    }
}


