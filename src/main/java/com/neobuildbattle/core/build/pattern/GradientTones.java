package com.neobuildbattle.core.build.pattern;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents up to 8 tones around a base material, with noise-based diffusion between tones.
 */
public final class GradientTones {
    private final List<Material> ordered; // from darkest (1) to lightest (8)

    public GradientTones(List<Material> ordered) {
        this.ordered = new ArrayList<>(ordered);
    }

    public Material pickWithNoise(int x, int y, int z) {
        if (ordered.isEmpty()) return Material.AIR;
        // Compute a pseudo-tone index by 3D noise with slight diffusion
        double n = simplex(x * 0.08, y * 0.08, z * 0.08);
        double t = (n * 0.5 + 0.5) * (ordered.size() - 1);
        int idx = (int) Math.round(t);
        idx = Math.max(0, Math.min(ordered.size() - 1, idx));
        return ordered.get(idx);
    }

    public Material pickByHeightWithNoise(int x, int y, int z, double t01) {
        if (ordered.isEmpty()) return Material.AIR;
        double n = simplex(x * 0.07, y * 0.07, z * 0.07);
        double bias = Math.max(0.0, Math.min(1.0, t01));
        double mix = 0.75 * bias + 0.25 * (n * 0.5 + 0.5); // mostly height-driven with a bit of spray noise
        int idx = (int) Math.round(mix * (ordered.size() - 1));
        idx = Math.max(0, Math.min(ordered.size() - 1, idx));
        return ordered.get(idx);
    }

    // Simple 3D hash noise (placeholder for simplex/perlin)
    private double simplex(double x, double y, double z) {
        long xi = Double.doubleToLongBits(x * 73856093);
        long yi = Double.doubleToLongBits(y * 19349663);
        long zi = Double.doubleToLongBits(z * 83492791);
        long h = xi ^ yi ^ zi;
        h ^= (h << 13);
        h ^= (h >> 7);
        h ^= (h << 17);
        return ((h & 0xFFFFFF) / (double) 0xFFFFFF) * 2 - 1; // [-1,1]
    }
}


