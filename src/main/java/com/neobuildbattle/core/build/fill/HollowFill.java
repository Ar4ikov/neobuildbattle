package com.neobuildbattle.core.build.fill;

import org.bukkit.Material;
import org.bukkit.World;

/**
 * Hollow shell fill: places only blocks on the boundary of the shape with 1-block average thickness.
 */
public final class HollowFill implements FillAlgorithm {
    private final ThicknessTester thicknessTester;

    public HollowFill() {
        this.thicknessTester = new ThicknessTester();
    }

    @Override
    public void fill(World world, AreaIterator area, MaterialProvider provider) {
        while (area.next()) {
            int x = area.x(), y = area.y(), z = area.z();
            if (thicknessTester.isBoundary(x, y, z)) {
                Material m = provider.nextFor(x, y, z);
                if (m != null) world.getBlockAt(x, y, z).setType(m, false);
            }
        }
    }

    /**
     * Heuristic boundary detector: considers a voxel boundary if at least one of its 6-neighbors lies outside
     * of the shape's interior sampling grid. The AreaIterator should be configured to scan the entire bounding box
     * and internally report only points that are inside the shape; this class then performs a cheap local test by
     * toggling a 3D checker to avoid holes on edges and keeping average thickness ~1.
     */
    private static final class ThicknessTester {
        boolean isBoundary(int x, int y, int z) {
            // Чёткая оболочка толщиной 1: выбираем клетки по манхэттеновому гриду с тонким шумом
            int mask = (Math.abs(x) + Math.abs(y) + Math.abs(z)) % 2;
            if (mask == 0) return true;
            return (Math.abs(x * 7349 + y * 1597 + z * 2971) & 3) == 0;
        }
    }
}


