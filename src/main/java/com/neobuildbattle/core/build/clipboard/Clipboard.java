package com.neobuildbattle.core.build.clipboard;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/**
 * Stores a 3D array of block data relative to an origin (0,0,0).
 */
public final class Clipboard {
    private final int sizeX, sizeY, sizeZ;
    private final BlockData[] blocks; // flattened xyz -> index

    // placement options
    private boolean pasteAir = false; // replace with air if present in clipboard

    // anchor inside clipboard (rotation/mirroring center)
    private int anchorX = 0;
    private int anchorY = 0;
    private int anchorZ = 0;

    public Clipboard(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ;
        this.blocks = new BlockData[sizeX * sizeY * sizeZ];
    }

    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }

    public BlockData get(int x, int y, int z) { return blocks[index(x,y,z)]; }
    public void set(int x, int y, int z, BlockData data) { blocks[index(x,y,z)] = data; }

    private int index(int x, int y, int z) { return (y * sizeZ + z) * sizeX + x; }

    public boolean isPasteAir() { return pasteAir; }
    public void setPasteAir(boolean pasteAir) { this.pasteAir = pasteAir; }

    public int getAnchorX() { return anchorX; }
    public int getAnchorY() { return anchorY; }
    public int getAnchorZ() { return anchorZ; }
    public void setAnchor(int ax, int ay, int az) {
        this.anchorX = Math.max(0, Math.min(sizeX - 1, ax));
        this.anchorY = Math.max(0, Math.min(sizeY - 1, ay));
        this.anchorZ = Math.max(0, Math.min(sizeZ - 1, az));
    }
}


