package com.neobuildbattle.core.lobby;

import com.neobuildbattle.core.NeoBuildBattleCore;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import com.neobuildbattle.core.plot.PlotManager;

public final class LobbyManager {
    private final NeoBuildBattleCore plugin;

    public LobbyManager(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
    }

    public void sendToLobby(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        Location spawn = getLobbySpawn();
        if (spawn != null) {
            player.teleport(spawn);
        }
    }

    private Location getLobbySpawn() {
        FileConfiguration c = plugin.getConfig();
        String worldName = c.getString("lobby-spawn.world", c.getString("worlds.lobby", "world"));
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        double x = c.getDouble("lobby-spawn.x", 0.5);
        double y = c.getDouble("lobby-spawn.y", 64.0);
        double z = c.getDouble("lobby-spawn.z", 0.5);
        float yaw = (float) c.getDouble("lobby-spawn.yaw", 0.0);
        float pitch = (float) c.getDouble("lobby-spawn.pitch", 0.0);
        Location loc = new Location(w, x, y, z, yaw, pitch);
        return loc;
    }

    public void placeLobbyPlatform() {
        Location spawn = getLobbySpawn();
        if (spawn != null) {
            int size = plugin.getConfig().getInt("lobby-platform.size", 7);
            var mat = org.bukkit.Material.matchMaterial(plugin.getConfig().getString("lobby-platform.material", "QUARTZ_BLOCK"));
            if (mat == null) return;
            int half = size / 2;
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    spawn.getWorld().getBlockAt(spawn.getBlockX() + x, spawn.getBlockY() - 1, spawn.getBlockZ() + z)
                            .setType(mat, false);
                }
            }
        }
    }
}


