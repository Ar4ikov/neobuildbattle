package com.neobuildbattle.core.world;

import com.neobuildbattle.core.NeoBuildBattleCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Optional;

/**
 * Integration helper for the external VoidWorldGenerator plugin.
 * If installed and configured in bukkit.yml, we read its config to respect spawn/biome.
 */
public final class VoidGenSupport {

    private final NeoBuildBattleCore plugin;

    public VoidGenSupport(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        Plugin p = Bukkit.getPluginManager().getPlugin("VoidWorldGenerator");
        return p != null && p.isEnabled();
    }

    /**
     * Reads spawn location from VoidWorldGenerator config if present.
     * Config format (as per README):
     * plugins/VoidWorldGenerator/config.yml with keys spawn.x, spawn.y, spawn.z.
     */
    public Optional<Location> readExternalSpawn(World world) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        Plugin vg = Bukkit.getPluginManager().getPlugin("VoidWorldGenerator");
        if (vg == null) return Optional.empty();
        try {
            File cfgFile = new File(vg.getDataFolder(), "config.yml");
            if (!cfgFile.exists()) return Optional.empty();
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(cfgFile);
            double x = cfg.getDouble("spawn.x", Double.NaN);
            double y = cfg.getDouble("spawn.y", Double.NaN);
            double z = cfg.getDouble("spawn.z", Double.NaN);
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
                return Optional.empty();
            }
            return Optional.of(new Location(world, x, y, z));
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to read VoidWorldGenerator config: " + ex.getMessage());
            return Optional.empty();
        }
    }
}


