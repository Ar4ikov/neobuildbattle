package com.neobuildbattle.core.build.pattern;

import org.bukkit.Material;

import java.util.*;

/**
 * Probabilistic pattern of materials. Supports weighted random and gradient noise.
 */
public final class BlockPattern {
    private final Map<Material, Integer> weights = new LinkedHashMap<>();
    // Multiple gradients, keyed by base material used to generate tones
    private final Map<Material, Integer> gradientWeights = new LinkedHashMap<>();
    private final Map<Material, GradientTones> gradientTonesByBase = new LinkedHashMap<>();
    // Per-invocation salt to vary randomness between operations
    private int saltX = 0, saltY = 0, saltZ = 0;

    public void setWeight(Material m, int weight) {
        if (weight <= 0) weights.remove(m); else weights.put(m, weight);
    }

    public void clear() {
        weights.clear();
        gradientWeights.clear();
        gradientTonesByBase.clear();
    }

    public void addOrUpdateGradient(Material base, GradientTones tones, int deltaWeight) {
        if (base == null || tones == null) return;
        int newW = Math.max(0, gradientWeights.getOrDefault(base, 0) + deltaWeight);
        if (newW <= 0) {
            gradientWeights.remove(base);
            gradientTonesByBase.remove(base);
        } else {
            gradientWeights.put(base, newW);
            gradientTonesByBase.put(base, tones);
        }
    }

    public void setGradientWeight(Material base, int weight) {
        if (base == null) return;
        int w = Math.max(0, weight);
        if (w == 0) {
            gradientWeights.remove(base);
            gradientTonesByBase.remove(base);
        } else {
            gradientWeights.put(base, w);
        }
    }

    public int getGradientWeight(Material base) { return Math.max(0, gradientWeights.getOrDefault(base, 0)); }

    public int getTotalGradientWeight() {
        int sum = 0; for (int w : gradientWeights.values()) sum += Math.max(0, w); return sum;
    }

    public Map<Material, Integer> getGradientWeightsView() { return Collections.unmodifiableMap(gradientWeights); }

    public boolean hasGradients() { return !gradientWeights.isEmpty(); }

    // Change noise seed/salt so the pattern layout differs per invocation
    public void reseedRandom() {
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        // keep salts within reasonable ranges to avoid overflow
        saltX = rnd.nextInt(1_000_000);
        saltY = rnd.nextInt(1_000_000);
        saltZ = rnd.nextInt(1_000_000);
    }

    public Material pick(int x, int y, int z) {
        int total = getTotalGradientWeight();
        for (int w : weights.values()) total += w;
        if (total <= 0) return Material.AIR;
        int r = Math.abs(hashXYZ(x, y, z)) % total;
        int gsum = getTotalGradientWeight();
        if (r < gsum && !gradientWeights.isEmpty()) {
            // Choose which gradient band by weight
            for (Map.Entry<Material, Integer> e : gradientWeights.entrySet()) {
                int w = Math.max(0, e.getValue());
                if (r < w) {
                    GradientTones tones = gradientTonesByBase.get(e.getKey());
                    return tones != null ? tones.pickWithNoise(x + saltX, y + saltY, z + saltZ) : Material.AIR;
                }
                r -= w;
            }
        } else {
            r -= gsum;
            for (Map.Entry<Material, Integer> e : weights.entrySet()) {
                int w = e.getValue();
                if (r < w) return e.getKey();
                r -= w;
            }
        }
        // Fallback
        if (!gradientWeights.isEmpty()) {
            GradientTones any = gradientTonesByBase.values().stream().findFirst().orElse(null);
            return any != null ? any.pickWithNoise(x + saltX, y + saltY, z + saltZ) : Material.AIR;
        }
        return weights.isEmpty() ? Material.AIR : weights.keySet().iterator().next();
    }

    public Material pickForHeight(int x, int y, int z, int minY, int maxY) {
        int total = getTotalGradientWeight();
        for (int w : weights.values()) total += w;
        if (total <= 0) return Material.AIR;
        // vertical gradient 0..1 (top->bottom direction configurable here; currently top->bottom increasing)
        double t = 0.0;
        if (maxY > minY) t = (y - minY) / (double) (maxY - minY);
        int r = Math.abs(hashXYZ(x, y, z)) % total;
        int gsum = getTotalGradientWeight();
        if (!gradientWeights.isEmpty() && gsum > 0) {
            for (Map.Entry<Material, Integer> e : gradientWeights.entrySet()) {
                int w = Math.max(0, e.getValue());
                if (r < w) {
                    GradientTones tones = gradientTonesByBase.get(e.getKey());
                    return tones != null ? tones.pickByHeightWithNoise(x + saltX, y + saltY, z + saltZ, t) : Material.AIR;
                }
                r -= w;
            }
        } else {
            // fallback to plain pattern
        }
        for (Map.Entry<Material, Integer> e : weights.entrySet()) {
            int w = e.getValue();
            if (r < w) return e.getKey();
            r -= w;
        }
        return Material.AIR;
    }

    private int hashXYZ(int x, int y, int z) {
        // 64-bit SplitMix hash of 3D coordinates to avoid parity/checker artifacts
        long a = (long) (x + saltX) * 0x9E3779B97F4A7C15L;
        long b = (long) (y + saltY) * 0xC2B2AE3D27D4EB4FL;
        long c = (long) (z + saltZ) * 0x165667B19E3779F9L;
        long seed = a ^ Long.rotateLeft(b, 21) ^ Long.rotateLeft(c, 42);
        seed += 0x9E3779B97F4A7C15L;
        seed = (seed ^ (seed >>> 30)) * 0xBF58476D1CE4E5B9L;
        seed = (seed ^ (seed >>> 27)) * 0x94D049BB133111EBL;
        seed = seed ^ (seed >>> 31);
        int mixed = (int) (seed ^ (seed >>> 32));
        return mixed == Integer.MIN_VALUE ? 0 : Math.abs(mixed);
    }

    public Map<Material, Integer> getWeightsView() {
        return Collections.unmodifiableMap(weights);
    }
}


