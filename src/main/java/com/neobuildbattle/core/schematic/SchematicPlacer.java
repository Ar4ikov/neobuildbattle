package com.neobuildbattle.core.schematic;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class SchematicPlacer {
    private final Plugin plugin;

    public SchematicPlacer(Plugin plugin) {
        this.plugin = plugin;
    }

    public void placeSync(Schematic schematic, World world, int baseX, int baseY, int baseZ) {
        for (Schematic.BlockDef b : schematic.getBlocks()) {
            Block block = world.getBlockAt(baseX + b.x, baseY + b.y, baseZ + b.z);
            block.setType(b.material, false);
        }
    }

    public void placeSync(Schematic schematic, Location base) {
        placeSync(schematic, base.getWorld(), base.getBlockX(), base.getBlockY(), base.getBlockZ());
    }
}


