package com.neobuildbattle.core.world;

import com.neobuildbattle.core.NeoBuildBattleCore;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Applies spawn settings to newly created/loaded worlds to ensure
 * void worlds have a safe default spawn.
 */
public final class WorldLifecycleListener implements Listener {

    private final NeoBuildBattleCore plugin;
    private final VoidGenSupport voidGenSupport;

    public WorldLifecycleListener(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
        this.voidGenSupport = new VoidGenSupport(plugin);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        // Apply only to our configured lobby/game worlds
        String lobbyName = plugin.getConfig().getString("worlds.lobby", "world");
        String gameName = plugin.getConfig().getString("worlds.game", "world");
        boolean isLobby = world.getName().equalsIgnoreCase(lobbyName);
        boolean isGame = world.getName().equalsIgnoreCase(gameName);
        if (!isLobby && !isGame) {
            return;
        }

        // 1) Ensure spawn is set (prefer VoidWorldGenerator config if available)
        {
            var externalSpawn = voidGenSupport.readExternalSpawn(world);
            if (externalSpawn.isPresent()) {
                world.setSpawnLocation(externalSpawn.get());
            } else {
                double sx = plugin.getConfig().getDouble("void-world.spawn.x", 0.5);
                double sy = plugin.getConfig().getDouble("void-world.spawn.y", 64.0);
                double sz = plugin.getConfig().getDouble("void-world.spawn.z", 0.5);
                world.setSpawnLocation(new Location(world, sx, sy, sz));
            }
        }

        // 2) For lobby world: place lobby schematic once and snap spawn to its center
        if (isLobby) {
            java.io.File marker = new java.io.File(world.getWorldFolder(), "neobb_lobby_placed.flag");
            if (!marker.exists()) {
                try {
                    // Determine desired spawn (from config or world spawn) to center the lobby around
                    Location desiredSpawn = world.getSpawnLocation();
                    // Get schematic sizes
                    var plotManager = plugin.getPlotManager();
                    var lobbySchematicField = com.neobuildbattle.core.plot.PlotManager.class.getDeclaredField("lobbySchematic");
                    lobbySchematicField.setAccessible(true);
                    var lobbySchematic = (com.neobuildbattle.core.schematic.Schematic) lobbySchematicField.get(plotManager);
                    if (lobbySchematic != null) {
                        int sizeX = lobbySchematic.getSizeX();
                        int sizeZ = lobbySchematic.getSizeZ();
                        int baseX = desiredSpawn.getBlockX() - (sizeX / 2);
                        int baseZ = desiredSpawn.getBlockZ() - (sizeZ / 2);
                        int baseY = desiredSpawn.getBlockY() - 1; // floor under feet at y-1
                        // Place lobby synchronously
                        plotManager.placeLobbyAt(new Location(world, baseX, baseY, baseZ));
                        // Set world spawn exactly at center of the schematic
                        double centerX = baseX + (sizeX / 2.0);
                        double centerZ = baseZ + (sizeZ / 2.0);
                        double spawnY = baseY + 1.0; // standing on floor
                        Location center = new Location(world, centerX + 0.5, spawnY, centerZ + 0.5);
                        world.setSpawnLocation(center);
                        // Also update plugin config lobby-spawn to keep LobbyManager in sync
                        var cfg = plugin.getConfig();
                        cfg.set("lobby-spawn.world", world.getName());
                        cfg.set("lobby-spawn.x", center.getX());
                        cfg.set("lobby-spawn.y", center.getY());
                        cfg.set("lobby-spawn.z", center.getZ());
                        cfg.set("lobby-spawn.yaw", 0.0);
                        cfg.set("lobby-spawn.pitch", 0.0);
                        plugin.saveConfig();
                    }
                    // Create marker to avoid duplicate placement
                    marker.createNewFile();
                } catch (Throwable t) {
                    plugin.getLogger().warning("Failed to place lobby schematic on first world load: " + t.getMessage());
                }
            }
        }
    }
}


