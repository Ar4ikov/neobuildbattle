package com.neobuildbattle.core.build.biome;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.build.gui.BuildGuiHolder;
import com.neobuildbattle.core.build.gui.GuiType;
import com.neobuildbattle.core.build.ui.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class BiomeGui {

    public static record Option(Biome biome, Material icon, String title) {}

    public static List<Option> loadOptions(NeoBuildBattleCore plugin) {
        List<Option> result = new ArrayList<>();
        try {
            File f = new File(plugin.getDataFolder(), "biomes.yml");
            if (!f.exists()) plugin.saveResource("biomes.yml", false);
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            var list = cfg.getMapList("biomes");
            for (var map : list) {
                String biomeName = String.valueOf(map.get("biome"));
                String iconName = String.valueOf(map.get("icon"));
                String title = String.valueOf(map.get("name"));
                Biome biome = Biome.valueOf(biomeName.toUpperCase());
                Material icon = Material.matchMaterial(iconName);
                if (biome != null && icon != null) {
                    result.add(new Option(biome, icon, ChatColor.RESET + title));
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to load biomes.yml: " + t.getMessage());
        }
        return result;
    }

    public static Inventory render(List<Option> options) {
        Inventory inv = Bukkit.createInventory(new BuildGuiHolder(GuiType.BIOMES), 54, ChatColor.GREEN + "Биомы");
        // background fill handled by caller if needed
        int idx = 10;
        for (Option opt : options) {
            if (idx >= 44) break;
            inv.setItem(idx, ItemFactory.named(opt.icon(), opt.title(), null));
            idx += (idx % 9 == 7) ? 3 : 1;
        }
        return inv;
    }
}


