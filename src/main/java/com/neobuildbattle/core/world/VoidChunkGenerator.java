package com.neobuildbattle.core.world;

import com.neobuildbattle.core.NeoBuildBattleCore;
import org.bukkit.block.Biome;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/**
 * Void world generator for Minecraft 1.21.6
 * Based on VoidWorldGenerator by HydrolienF
 * https://github.com/HydrolienF/VoidWorldGenerator
 * 
 * Uses modern ChunkGenerator API while maintaining compatibility
 */
public final class VoidChunkGenerator extends ChunkGenerator {

    private final NeoBuildBattleCore plugin;

    public VoidChunkGenerator(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        // Create empty chunk data (void world)
        ChunkData chunk = createChunkData(world);
        
        // Set biome for the entire chunk from config
        Biome configuredBiome = getConfiguredBiome();
        
        // Fill the entire chunk with the configured biome
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y += 4) {
                    biome.setBiome(lx, y, lz, configuredBiome);
                }
            }
        }
        
        return chunk; // Return empty chunk (pure void)
    }

    /**
     * Get biome from config, fallback to THE_VOID if invalid
     * Compatible with 1.21.6 biome system
     */
    private Biome getConfiguredBiome() {
        String biomeName = plugin.getConfig().getString("void-world.biome", "THE_VOID");
        
        // Handle common biome names for void worlds
        switch (biomeName.toUpperCase().replace(' ', '_')) {
            case "THE_VOID":
            case "VOID":
                return Biome.THE_VOID;
            case "PLAINS":
                return Biome.PLAINS;
            case "DESERT":
                return Biome.DESERT;
            case "END_BARRENS":
                return Biome.END_BARRENS;
            case "END_HIGHLANDS":
                return Biome.END_HIGHLANDS;
            case "END_MIDLANDS":
                return Biome.END_MIDLANDS;
            case "SMALL_END_ISLANDS":
                return Biome.SMALL_END_ISLANDS;
            default:
                try {
                    // Try to find biome by name (with fallback for deprecated API)
                    for (Biome b : Biome.values()) {
                        if (b.toString().equalsIgnoreCase(biomeName) || 
                            b.toString().replace('_', ' ').equalsIgnoreCase(biomeName)) {
                            return b;
                        }
                    }
                } catch (Exception ignored) {
                    // Fallback to THE_VOID if enumeration fails
                }
                
                plugin.getLogger().warning("Unknown biome '" + biomeName + "' in config, using THE_VOID");
                return Biome.THE_VOID;
        }
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        // Allow spawning in void world (spawn platform should be built by plugin)
        return true;
    }
}


