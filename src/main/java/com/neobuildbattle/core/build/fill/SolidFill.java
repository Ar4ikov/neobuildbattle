package com.neobuildbattle.core.build.fill;

import org.bukkit.Material;
import org.bukkit.World;

public final class SolidFill implements FillAlgorithm {
    @Override
    public void fill(World world, AreaIterator area, MaterialProvider provider) {
        while (area.next()) {
            int x = area.x(), y = area.y(), z = area.z();
            Material m = provider.nextFor(x, y, z);
            if (m != null) world.getBlockAt(x, y, z).setType(m, false);
        }
    }
}


