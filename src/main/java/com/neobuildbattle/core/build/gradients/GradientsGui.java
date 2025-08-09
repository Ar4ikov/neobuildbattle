package com.neobuildbattle.core.build.gradients;

import com.neobuildbattle.core.build.gui.BuildGuiHolder;
import com.neobuildbattle.core.build.gui.GuiType;
import com.neobuildbattle.core.build.ui.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import java.util.List;

public final class GradientsGui {
    private GradientsGui() {}

    public static Inventory render(List<List<Material>> gradients, int page) {
        Inventory inv = Bukkit.createInventory(new BuildGuiHolder(GuiType.GRADIENTS), 54, ChatColor.BLUE + "Градиенты (" + (page + 1) + "/" + Math.max(1, gradients.size()) + ")");
        int writeIndex = 0;
        int gIndex = page;
        while (writeIndex < 45 && gIndex < gradients.size()) {
            List<Material> mats = gradients.get(gIndex);
            int limit = Math.min(8, mats.size());
            for (int i = 0; i < limit && writeIndex < 45; i++) {
                inv.setItem(writeIndex++, ItemFactory.named(mats.get(i), ChatColor.AQUA + "Блок " + (i + 1), null));
            }
            while ((writeIndex % 9) < 8 && writeIndex < 45) {
                inv.setItem(writeIndex++, ItemFactory.named(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + "", null));
            }
            if (writeIndex < 45) {
                inv.setItem(writeIndex++, ItemFactory.named(Material.PAPER, ChatColor.GREEN + "Взять на хотбар", java.util.List.of(ChatColor.GRAY + "Слоты 1-8")));
            }
            gIndex++;
        }
        if (page > 0) inv.setItem(47, ItemFactory.named(Material.PAPER, ChatColor.YELLOW + "Страница назад", null));
        if (page < gradients.size()) inv.setItem(51, ItemFactory.named(Material.PAPER, ChatColor.YELLOW + "Страница вперед", null));
        return inv;
    }
}


