package com.neobuildbattle.core.plot;

import com.neobuildbattle.core.NeoBuildBattleCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import com.neobuildbattle.core.schematic.Schematic;
import com.neobuildbattle.core.schematic.SchematicLoader;
import com.neobuildbattle.core.schematic.SchematicPlacer;

import java.util.*;

public final class PlotManager {
    private final NeoBuildBattleCore plugin;
    private final Map<UUID, Plot> ownerToPlot = new HashMap<>();
    private final SchematicLoader schematicLoader;
    private final SchematicPlacer schematicPlacer;
    private Schematic lobbySchematic;
    private Schematic plotSchematic; // reserved for future use

    public PlotManager(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
        this.schematicLoader = new SchematicLoader(plugin);
        this.schematicPlacer = new SchematicPlacer(plugin);
        this.lobbySchematic = schematicLoader.load("schematics/lobby.schematic");
        this.plotSchematic = schematicLoader.load("schematics/plot.schematic");
    }

    public void allocatePlots(List<Player> players) {
        ownerToPlot.clear();
        FileConfiguration c = plugin.getConfig();
        String worldName = c.getString("arena-origin.world", c.getString("worlds.game", "world"));
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;
        int buildArea = c.getInt("build-area-size", 65);
        int size = buildArea + 6; // walls 2 each side + ring 1 each side
        int gap = c.getInt("plot-gap", 5);
        int y = (int) Math.round(c.getDouble("arena-origin.y", 64.0));
        int originX = (int) Math.round(c.getDouble("arena-origin.x", 100.0));
        int originZ = (int) Math.round(c.getDouble("arena-origin.z", 100.0));
        int matrix = Math.max(1, c.getInt("matrix-size", 4));
        var floorMaterial = org.bukkit.Material.matchMaterial(c.getString("plot-floor-material", "WHITE_CONCRETE"));
        var wallMaterial = org.bukkit.Material.matchMaterial(c.getString("plot-wall-material", "WHITE_CONCRETE"));
        int wallHeight = Math.max(1, c.getInt("plot-wall-height", 50));

        int idx = 0;
        for (Player p : players) {
            int row = idx / matrix;
            int col = idx % matrix;
            int minX = originX + col * (size + gap);
            int minZ = originZ + row * (size + gap);
            Plot plot = new Plot(p.getUniqueId(), world, minX, minZ, size, y);
            ownerToPlot.put(p.getUniqueId(), plot);
            // Clear interior volume inside ring to ensure empty plot (air)
            int innerStart = 3; // after two walls and one ring
            int innerEnd = size - 4; // inclusive
            for (int yy = y; yy <= y + wallHeight + 2; yy++) {
                for (int xx = innerStart; xx <= innerEnd; xx++) {
                    for (int zz = innerStart; zz <= innerEnd; zz++) {
                        world.getBlockAt(minX + xx, yy, minZ + zz).setType(org.bukkit.Material.AIR, false);
                    }
                }
            }

            // Generate outer walls (2-thick) up to configured height
            if (wallMaterial != null) {
                for (int h = 1; h <= wallHeight; h++) {
                    for (int x = 0; x < size; x++) {
                        // z sides
                        world.getBlockAt(minX + x, y + h, minZ + 0).setType(wallMaterial, false);
                        world.getBlockAt(minX + x, y + h, minZ + 1).setType(wallMaterial, false);
                        world.getBlockAt(minX + x, y + h, minZ + size - 1).setType(wallMaterial, false);
                        world.getBlockAt(minX + x, y + h, minZ + size - 2).setType(wallMaterial, false);
                    }
                    for (int z = 0; z < size; z++) {
                        // x sides
                        world.getBlockAt(minX + 0, y + h, minZ + z).setType(wallMaterial, false);
                        world.getBlockAt(minX + 1, y + h, minZ + z).setType(wallMaterial, false);
                        world.getBlockAt(minX + size - 1, y + h, minZ + z).setType(wallMaterial, false);
                        world.getBlockAt(minX + size - 2, y + h, minZ + z).setType(wallMaterial, false);
                    }
                }
            }

            // 'x' ring (no-build) one block thick around build area at base Y
            var ringMaterial = org.bukkit.Material.matchMaterial(c.getString("plot-ring-material", "LIGHT_GRAY_CONCRETE"));
            if (ringMaterial != null) {
                // reuse innerStart/innerEnd
                // top & bottom lines
                for (int x = innerStart; x <= innerEnd; x++) {
                    world.getBlockAt(minX + x, y, minZ + innerStart - 1).setType(ringMaterial, false);
                    world.getBlockAt(minX + x, y, minZ + innerEnd + 1).setType(ringMaterial, false);
                }
                // left & right lines
                for (int z = innerStart; z <= innerEnd; z++) {
                    world.getBlockAt(minX + innerStart - 1, y, minZ + z).setType(ringMaterial, false);
                    world.getBlockAt(minX + innerEnd + 1, y, minZ + z).setType(ringMaterial, false);
                }
                // fill the 4 corner blocks of the ring to avoid air gaps
                world.getBlockAt(minX + innerStart - 1, y, minZ + innerStart - 1).setType(ringMaterial, false);
                world.getBlockAt(minX + innerStart - 1, y, minZ + innerEnd + 1).setType(ringMaterial, false);
                world.getBlockAt(minX + innerEnd + 1, y, minZ + innerStart - 1).setType(ringMaterial, false);
                world.getBlockAt(minX + innerEnd + 1, y, minZ + innerEnd + 1).setType(ringMaterial, false);
            }

            // Generate build area floor (65x65) starting at +3 offset
            if (floorMaterial != null) {
                for (int x = 0; x < buildArea; x++) {
                    for (int z = 0; z < buildArea; z++) {
                        world.getBlockAt(minX + 3 + x, y, minZ + 3 + z).setType(floorMaterial, false);
                    }
                }
            }

            // Place an invisible barrier layer one block below the floor to prevent falling entities/blocks
            for (int x = 0; x < buildArea; x++) {
                for (int z = 0; z < buildArea; z++) {
                    world.getBlockAt(minX + 3 + x, y - 1, minZ + 3 + z).setType(org.bukkit.Material.BARRIER, false);
                }
            }
            idx++;
        }
    }

    public Plot getPlotByOwner(UUID id) {
        return ownerToPlot.get(id);
    }

    public Set<UUID> getAllOwners() {
        return ownerToPlot.keySet();
    }

    public void resetArenaAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Thread.sleep(250L);
            } catch (InterruptedException ignored) { }
            Bukkit.getScheduler().runTask(plugin, () -> {
                // wipe all owned plots to AIR including walls and ring
                for (Plot plot : new ArrayList<>(ownerToPlot.values())) {
                    World world = plot.getSpawnLocation().getWorld();
                    if (world == null) continue;
                    int minX = plot.getMinX();
                    int minZ = plot.getMinZ();
                    int maxX = plot.getMaxX();
                    int maxZ = plot.getMaxZ();
                    int baseY = (int) Math.floor(plot.getSpawnLocation().getY()) - 1;
                    int height = Math.max(50, plugin.getConfig().getInt("plot-wall-height", 50));
                    for (int y = baseY; y <= baseY + height + 2; y++) {
                        for (int x = minX; x <= maxX; x++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                world.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR, false);
                            }
                        }
                    }
                }
                ownerToPlot.clear();
            });
        });
    }

    public void placeLobbyAt(Location base) {
        if (lobbySchematic != null && base.getWorld() != null) {
            schematicPlacer.placeSync(lobbySchematic, base);
        }
    }
}


