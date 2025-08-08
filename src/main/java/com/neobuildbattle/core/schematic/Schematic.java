package com.neobuildbattle.core.schematic;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public final class Schematic {
    public static final class BlockDef {
        public final int x;
        public final int y;
        public final int z;
        public final Material material;

        public BlockDef(int x, int y, int z, Material material) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
        }
    }

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<BlockDef> blocks;

    public Schematic(int sizeX, int sizeY, int sizeZ, List<BlockDef> blocks) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = new ArrayList<>(blocks);
    }

    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }
    public List<BlockDef> getBlocks() { return blocks; }
}


