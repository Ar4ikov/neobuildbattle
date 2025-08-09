package com.neobuildbattle.core.build.particles;

import com.neobuildbattle.core.build.gui.BuildGuiHolder;
import com.neobuildbattle.core.build.gui.GuiType;
import com.neobuildbattle.core.build.ui.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ParticlesGui {
    private ParticlesGui() {}

    public static Inventory render(org.bukkit.NamespacedKey particleTypeKey, org.bukkit.NamespacedKey particleEraserKey) {
        Inventory inv = Bukkit.createInventory(new BuildGuiHolder(GuiType.PARTICLES), 54, ChatColor.LIGHT_PURPLE + "Партиклы");
        place(inv, 20, Particle.HAPPY_VILLAGER, Material.EMERALD, ChatColor.GREEN + "Жители", particleTypeKey);
        place(inv, 21, Particle.HEART, Material.POPPY, ChatColor.RED + "Сердечки", particleTypeKey);
        place(inv, 22, Particle.FLAME, Material.BLAZE_POWDER, ChatColor.GOLD + "Огонь", particleTypeKey);
        place(inv, 23, Particle.LARGE_SMOKE, Material.CAMPFIRE, ChatColor.GRAY + "Дым", particleTypeKey);
        place(inv, 24, Particle.POOF, Material.TNT, ChatColor.DARK_RED + "Взрывы", particleTypeKey);
        place(inv, 29, Particle.WITCH, Material.POTION, ChatColor.DARK_PURPLE + "Ведьма", particleTypeKey);
        place(inv, 30, Particle.SOUL, Material.SOUL_SOIL, ChatColor.AQUA + "Души", particleTypeKey);
        place(inv, 31, Particle.SOUL_FIRE_FLAME, Material.SOUL_TORCH, ChatColor.BLUE + "Синий огонь", particleTypeKey);
        ItemStack eraser = ItemFactory.named(Material.MILK_BUCKET, ChatColor.WHITE + "Удаление точки", java.util.List.of(ChatColor.GRAY + "Кликните в мире по точке, чтобы удалить"));
        ItemMeta meta = eraser.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(particleEraserKey, PersistentDataType.INTEGER, 1);
            eraser.setItemMeta(meta);
        }
        inv.setItem(53, eraser);
        return inv;
    }

    private static void place(Inventory inv, int slot, Particle p, Material icon, String name, org.bukkit.NamespacedKey key) {
        ItemStack item = ItemFactory.named(icon, name, java.util.List.of(ChatColor.GRAY + "ЛКМ в руке — поставить точку"));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(key, PersistentDataType.STRING, p.name());
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }
}


