package com.neobuildbattle.core.build.time;

import com.neobuildbattle.core.build.gui.BuildGuiHolder;
import com.neobuildbattle.core.build.gui.GuiType;
import com.neobuildbattle.core.build.ui.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public final class TimeGui {
    private TimeGui() {}

    private static final Map<Integer, Integer> SLOT_TO_TICKS = Map.of(
            11, 18000, // 0:00
            12, 21000, // 3:00
            13, 0,     // 6:00
            14, 3000,  // 9:00
            15, 6000,  // 12:00
            21, 9000,  // 15:00 (shifted left)
            22, 12000, // 18:00
            23, 15000  // 21:00
    );

    public static Inventory render() {
        Inventory inv = Bukkit.createInventory(new BuildGuiHolder(GuiType.TIME), 36, ChatColor.GOLD + "Выбор времени");
        int[] slots = {11, 12, 13, 14, 15, 21, 22, 23};
        String[] labels = {"0:00", "3:00", "6:00", "9:00", "12:00", "15:00", "18:00", "21:00"};
        for (int i = 0; i < slots.length; i++) {
            inv.setItem(slots[i], ItemFactory.named(Material.CLOCK, ChatColor.YELLOW + labels[i], null));
        }
        return inv;
    }

    public static Integer timeTicksForSlot(int slot) {
        return SLOT_TO_TICKS.get(slot);
    }
}


