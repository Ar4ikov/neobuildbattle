package com.neobuildbattle.core.build.fill.area;

import com.neobuildbattle.core.build.fill.FillAlgorithm;
import org.bukkit.util.Vector;

/**
 * Utility AreaIterator implementations for common selection shapes.
 */
public final class AreaIterators {
    private AreaIterators() {}

    public static FillAlgorithm.AreaIterator cuboid(Vector min, Vector max) {
        return new FillAlgorithm.AreaIterator() {
            int x = (int) Math.floor(min.getX()) - 1;
            int y = (int) Math.floor(min.getY());
            int z = (int) Math.floor(min.getZ());
            final int maxX = (int) Math.floor(max.getX());
            final int maxY = (int) Math.floor(max.getY());
            final int maxZ = (int) Math.floor(max.getZ());
            @Override public boolean next() {
                if (++x > maxX) { x = (int) Math.floor(min.getX()); if (++z > maxZ) { z = (int) Math.floor(min.getZ()); if (++y > maxY) return false; } }
                return true;
            }
            @Override public int x() { return x; }
            @Override public int y() { return y; }
            @Override public int z() { return z; }
            @Override public boolean isInside(int xi, int yi, int zi) {
                return xi >= (int) Math.floor(min.getX()) && xi <= maxX && zi >= (int) Math.floor(min.getZ()) && zi <= maxZ && yi >= (int) Math.floor(min.getY()) && yi <= maxY;
            }
        };
    }

    public static FillAlgorithm.AreaIterator sphere(org.bukkit.Location center, double radius) {
        final int cx = center.getBlockX();
        final int cy = center.getBlockY();
        final int cz = center.getBlockZ();
        final int r = (int) Math.ceil(radius);
        return new FillAlgorithm.AreaIterator() {
            int x = cx - r - 1, y = cy - r, z = cz - r;
            @Override public boolean next() {
                do {
                    if (++x > cx + r) { x = cx - r; if (++z > cz + r) { z = cz - r; if (++y > cy + r) return false; } }
                } while (!inside(x, y, z));
                return true;
            }
            private boolean inside(int x, int y, int z) {
                double dx = x + 0.5 - cx, dy = y + 0.5 - cy, dz = z + 0.5 - cz;
                return (dx*dx + dy*dy + dz*dz) <= radius * radius + 1e-6;
            }
            @Override public int x() { return x; }
            @Override public int y() { return y; }
            @Override public int z() { return z; }
            @Override public boolean isInside(int xi, int yi, int zi) { return inside(xi, yi, zi); }
        };
    }

    public static FillAlgorithm.AreaIterator ellipsoid(org.bukkit.Location a, org.bukkit.Location b) {
        final double cx = (a.getX() + b.getX()) * 0.5;
        final double cy = (a.getY() + b.getY()) * 0.5;
        final double cz = (a.getZ() + b.getZ()) * 0.5;
        final double rx = Math.max(0.5, Math.abs(a.getX() - b.getX()) * 0.5);
        final double ry = Math.max(0.5, Math.abs(a.getY() - b.getY()) * 0.5);
        final double rz = Math.max(0.5, Math.abs(a.getZ() - b.getZ()) * 0.5);
        final int minX = (int) Math.floor(Math.min(a.getX(), b.getX()));
        final int maxX = (int) Math.floor(Math.max(a.getX(), b.getX()));
        final int minY = (int) Math.floor(Math.min(a.getY(), b.getY()));
        final int maxY = (int) Math.floor(Math.max(a.getY(), b.getY()));
        final int minZ = (int) Math.floor(Math.min(a.getZ(), b.getZ()));
        final int maxZ = (int) Math.floor(Math.max(a.getZ(), b.getZ()));
        return new FillAlgorithm.AreaIterator() {
            int x = minX - 1, y = minY, z = minZ;
            @Override public boolean next() {
                do {
                    if (++x > maxX) { x = minX; if (++z > maxZ) { z = minZ; if (++y > maxY) return false; } }
                } while (!inside(x, y, z));
                return true;
            }
            private boolean inside(int x, int y, int z) {
                double dx = (x + 0.5 - cx) / rx;
                double dy = (y + 0.5 - cy) / ry;
                double dz = (z + 0.5 - cz) / rz;
                return (dx*dx + dy*dy + dz*dz) <= 1.0 + 1e-6;
            }
            @Override public int x() { return x; }
            @Override public int y() { return y; }
            @Override public int z() { return z; }
            @Override public boolean isInside(int xi, int yi, int zi) { return inside(xi, yi, zi); }
        };
    }

    public static FillAlgorithm.AreaIterator polygon(java.util.List<org.bukkit.Location> points) {
        if (points == null || points.size() < 3) {
            return new FillAlgorithm.AreaIterator() { public boolean next(){return false;} public int x(){return 0;} public int y(){return 0;} public int z(){return 0;} public boolean isInside(int a,int b,int c){return false;} };
        }
        // Assume polygon on XZ plane; use min/max Y from points as vertical range
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int n = points.size();
        int[] px = new int[n];
        int[] pz = new int[n];
        for (int i = 0; i < n; i++) {
            var l = points.get(i);
            px[i] = l.getBlockX();
            pz[i] = l.getBlockZ();
            minX = Math.min(minX, px[i]);
            maxX = Math.max(maxX, px[i]);
            minZ = Math.min(minZ, pz[i]);
            maxZ = Math.max(maxZ, pz[i]);
            minY = Math.min(minY, l.getBlockY());
            maxY = Math.max(maxY, l.getBlockY());
        }
        final int fMinX = minX, fMaxX = maxX, fMinZ = minZ, fMaxZ = maxZ, fMinY = minY, fMaxY = maxY;
        return new FillAlgorithm.AreaIterator() {
            int x = fMinX - 1, y = fMinY, z = fMinZ;
            @Override public boolean next() {
                do {
                    if (++x > fMaxX) { x = fMinX; if (++z > fMaxZ) { z = fMinZ; if (++y > fMaxY) return false; } }
                } while (!insidePolygon(x, z));
                return true;
            }
            private boolean insidePolygon(int x, int z) {
                // even-odd rule
                boolean inside = false;
                for (int i = 0, j = n - 1; i < n; j = i++) {
                    int xi = px[i], zi = pz[i];
                    int xj = px[j], zj = pz[j];
                    boolean intersect = ((zi > z) != (zj > z)) && (x < (long)(xj - xi) * (z - zi) / (double)(zj - zi) + xi);
                    if (intersect) inside = !inside;
                }
                return inside;
            }
            @Override public int x() { return x; }
            @Override public int y() { return y; }
            @Override public int z() { return z; }
            @Override public boolean isInside(int xi, int yi, int zi) { return xi >= fMinX && xi <= fMaxX && zi >= fMinZ && zi <= fMaxZ && yi >= fMinY && yi <= fMaxY && insidePolygon(xi, zi); }
        };
    }
}


