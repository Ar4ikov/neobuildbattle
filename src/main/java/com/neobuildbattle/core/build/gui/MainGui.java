package com.neobuildbattle.core.build.gui;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.build.BuildToolsManager;
import com.neobuildbattle.core.build.ui.ItemFactory;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Optional;

public final class MainGui {
    private MainGui() {}

    public static Inventory create(Player player) {
        Inventory inv = Bukkit.createInventory(new BuildGuiHolder(GuiType.MAIN), 54, ChatColor.DARK_AQUA + "Инструменты строителя");
        BuildToolsManager.fillBackground(inv);
        Material defaultFloor = Optional.ofNullable(Material.matchMaterial(NeoBuildBattleCore.getInstance().getConfig().getString("plot-floor-material", "WHITE_CONCRETE")))
                .orElse(Material.WHITE_CONCRETE);
        inv.setItem(10, ItemFactory.named(defaultFloor, ChatColor.GREEN + "Смена пола", List.of(ChatColor.GRAY + "Клик — применить, перетащите блок — заменить")));
        inv.setItem(11, ItemFactory.named(Material.IRON_PICKAXE, ChatColor.YELLOW + "Инструменты", List.of(ChatColor.GRAY + "Открыть")));
        inv.setItem(12, ItemFactory.named(Material.CLOCK, ChatColor.GOLD + "Время суток", List.of(ChatColor.GRAY + "Выбрать")));
        inv.setItem(14, ItemFactory.named(Material.WATER_BUCKET, ChatColor.AQUA + "Погода", List.of(ChatColor.GRAY + "Выбрать")));
        inv.setItem(15, ItemFactory.named(Material.FIREWORK_ROCKET, ChatColor.LIGHT_PURPLE + "Партиклы", List.of(ChatColor.GRAY + "Открыть")));
        inv.setItem(16, ItemFactory.named(Material.PAINTING, ChatColor.BLUE + "Градиенты", List.of(ChatColor.GRAY + "Открыть")));
        return inv;
    }
}


