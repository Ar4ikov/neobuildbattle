package com.neobuildbattle.core.build.fill;

import org.bukkit.Material;
import org.bukkit.World;

/**
 * Strategy for filling a selection with blocks.
 */
public interface FillAlgorithm {
    void fill(World world, AreaIterator area, MaterialProvider provider);

    interface AreaIterator {
        boolean next();
        int x();
        int y();
        int z();
        // Returns true if the given coordinate belongs to the selection shape
        boolean isInside(int x, int y, int z);
    }

    interface MaterialProvider {
        Material nextFor(int x, int y, int z);
    }
}


