package com.neobuildbattle.core.schematic;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Extremely simple schematic loader for custom text-based .schematic resources.
 * Format:
 *   # sizeX sizeY sizeZ
 *   x y z MATERIAL
 * or cuboid fill lines:
 *   fill x1 y1 z1 x2 y2 z2 MATERIAL
 * Lines starting with # are comments.
 */
public final class SchematicLoader {
    private final Plugin plugin;

    public SchematicLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public Schematic load(String resourcePath) {
        try (InputStream is = plugin.getResource(resourcePath)) {
            if (is == null) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                int sizeX = 0, sizeY = 0, sizeZ = 0;
                List<Schematic.BlockDef> blocks = new ArrayList<>();
                while ((line = br.readLine()) != null) {
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#")) continue;
                    if (s.startsWith("size ")) {
                        String[] p = s.split("\\s+");
                        sizeX = Integer.parseInt(p[1]);
                        sizeY = Integer.parseInt(p[2]);
                        sizeZ = Integer.parseInt(p[3]);
                        continue;
                    }
                    if (s.startsWith("fill ")) {
                        String[] parts = s.split("\\s+");
                        int x1 = Integer.parseInt(parts[1]);
                        int y1 = Integer.parseInt(parts[2]);
                        int z1 = Integer.parseInt(parts[3]);
                        int x2 = Integer.parseInt(parts[4]);
                        int y2 = Integer.parseInt(parts[5]);
                        int z2 = Integer.parseInt(parts[6]);
                        Material mat = Material.matchMaterial(parts[7]);
                        if (mat != null && mat.isBlock()) {
                            int minx = Math.min(x1, x2), maxx = Math.max(x1, x2);
                            int miny = Math.min(y1, y2), maxy = Math.max(y1, y2);
                            int minz = Math.min(z1, z2), maxz = Math.max(z1, z2);
                            for (int xx = minx; xx <= maxx; xx++) {
                                for (int yy = miny; yy <= maxy; yy++) {
                                    for (int zz = minz; zz <= maxz; zz++) {
                                        blocks.add(new Schematic.BlockDef(xx, yy, zz, mat));
                                    }
                                }
                            }
                        }
                    } else {
                        String[] parts = s.split("\\s+");
                        int x = Integer.parseInt(parts[0]);
                        int y = Integer.parseInt(parts[1]);
                        int z = Integer.parseInt(parts[2]);
                        Material mat = Material.matchMaterial(parts[3]);
                        if (mat != null && mat.isBlock()) {
                            blocks.add(new Schematic.BlockDef(x, y, z, mat));
                        }
                    }
                }
                return new Schematic(sizeX, sizeY, sizeZ, blocks);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load schematic: " + resourcePath + " : " + e.getMessage());
            return null;
        }
    }
}


