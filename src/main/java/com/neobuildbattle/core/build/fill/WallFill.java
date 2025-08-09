package com.neobuildbattle.core.build.fill;

import org.bukkit.Material;
import org.bukkit.World;

/**
 * Заполняет только вертикальные стены (периметр) выделения. Толщина 1, без дыр.
 */
public final class WallFill implements FillAlgorithm {
    @Override
    public void fill(World world, AreaIterator area, MaterialProvider provider) {
        while (area.next()) {
            int x = area.x(), y = area.y(), z = area.z();
            if (isWall(x, y, z, area)) {
                Material m = provider.nextFor(x, y, z);
                if (m != null) world.getBlockAt(x, y, z).setType(m, false);
            }
        }
    }

    private boolean isWall(int x, int y, int z, AreaIterator it) {
        // Стены: ячейка на границе по XZ (есть сосед вне формы по XZ); исключаем пол и потолок автоматически,
        // так как проверяем только XZ-соседей.
        boolean leftOut = !it.isInside(x - 1, y, z);
        boolean rightOut = !it.isInside(x + 1, y, z);
        boolean frontOut = !it.isInside(x, y, z - 1);
        boolean backOut = !it.isInside(x, y, z + 1);
        return leftOut || rightOut || frontOut || backOut;
    }
}


