package com.neobuildbattle.core.build.pattern;

import org.bukkit.Material;

import java.util.*;

/**
 * Probabilistic pattern of materials. Supports weighted random and gradient noise.
 */
public final class BlockPattern {
    private final Map<Material, Integer> weights = new LinkedHashMap<>();
    private GradientTones gradientTones; // optional
    private int gradientWeight = 0;

    public void setWeight(Material m, int weight) {
        if (weight <= 0) weights.remove(m); else weights.put(m, weight);
    }

    public void clear() { weights.clear(); gradientTones = null; }

    public void setGradient(GradientTones tones) { this.gradientTones = tones; }

    public void setGradientWeight(int w) { this.gradientWeight = Math.max(0, w); }

    public int getGradientWeight() { return gradientWeight; }

    public boolean hasGradient() { return gradientTones != null; }

    public Material pick(int x, int y, int z) {
        int total = gradientWeight;
        for (int w : weights.values()) total += w;
        if (total <= 0) return Material.AIR;
        int r = Math.abs(hashXYZ(x, y, z)) % total;
        if (r < gradientWeight && gradientTones != null) {
            return gradientTones.pickWithNoise(x, y, z);
        } else {
            r -= gradientWeight;
            for (Map.Entry<Material, Integer> e : weights.entrySet()) {
                int w = e.getValue();
                if (r < w) return e.getKey();
                r -= w;
            }
        }
        return gradientTones != null ? gradientTones.pickWithNoise(x, y, z) : weights.keySet().iterator().next();
    }

    public Material pickForHeight(int x, int y, int z, int minY, int maxY) {
        int total = gradientWeight;
        for (int w : weights.values()) total += w;
        if (total <= 0) return Material.AIR;
        // vertical gradient 0..1 (top->bottom direction configurable here; currently top->bottom increasing)
        double t = 0.0;
        if (maxY > minY) t = (y - minY) / (double) (maxY - minY);
        int r = Math.abs(hashXYZ(x, y, z)) % total;
        // allocate part of gradientWeight modulated by t to push tones smoothly by height
        if (gradientTones != null && gradientWeight > 0) {
            // noise + bias by height
            return gradientTones.pickByHeightWithNoise(x, y, z, t);
        }
        // fallback to plain pattern
        for (Map.Entry<Material, Integer> e : weights.entrySet()) {
            int w = e.getValue();
            if (r < w) return e.getKey();
            r -= w;
        }
        return Material.AIR;
    }

    private int hashXYZ(int x, int y, int z) {
        int h = 1469598101;
        h ^= x * 374761393; h *= 16777619;
        h ^= y * 668265263; h *= 16777619;
        h ^= z * 2147483647; h *= 16777619;
        return h;
    }

    public Map<Material, Integer> getWeightsView() {
        return Collections.unmodifiableMap(weights);
    }
}


