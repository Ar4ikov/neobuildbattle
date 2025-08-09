package com.neobuildbattle.core.build.gradient;

import com.neobuildbattle.core.NeoBuildBattleCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/**
 * Builds and caches a tone/color index for full blocks. Stored as block_tones.yml in data folder.
 * If file is missing or corrupt, regenerates.
 */
public final class BlockToneIndex {
    public static final class Tone {
        public final double hue;      // 0..1
        public final double sat;      // 0..1
        public final double lum;      // 0..1
        public Tone(double h, double s, double l) { this.hue = h; this.sat = s; this.lum = l; }
    }

    private final Map<Material, Tone> tones = new EnumMap<>(Material.class);
    private final Set<Material> fullBlocks = EnumSet.noneOf(Material.class);
    private final Map<Material, java.util.List<Material>> gradients = new EnumMap<>(Material.class);

    public BlockToneIndex() {}

    public void loadOrBuild(NeoBuildBattleCore plugin) {
        try {
            File file = new File(plugin.getDataFolder(), "block_tones.yml");
            if (file.exists()) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
                for (String key : y.getKeys(false)) {
                    Material m = Material.matchMaterial(key);
                    if (m == null) continue;
                    double h = y.getDouble(key + ".h", -1);
                    double s = y.getDouble(key + ".s", -1);
                    double l = y.getDouble(key + ".l", -1);
                    boolean full = y.getBoolean(key + ".full", false);
                    if (h >= 0 && s >= 0 && l >= 0) tones.put(m, new Tone(h, s, l));
                    if (full) fullBlocks.add(m);
                    java.util.List<String> gl = y.getStringList(key + ".gradient");
                    if (gl != null && !gl.isEmpty()) {
                        java.util.List<Material> mats = new java.util.ArrayList<>();
                        for (String n : gl) { Material mm = Material.matchMaterial(n); if (mm != null) mats.add(mm); }
                        if (!mats.isEmpty()) gradients.put(m, mats);
                    }
                }
                if (!tones.isEmpty()) return; // ok
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Tone index corrupted: " + t.getMessage());
        }
        build(plugin);
    }

    private void build(NeoBuildBattleCore plugin) {
        tones.clear();
        fullBlocks.clear();
        // Determine full blocks strictly (full cube in all projections) using name filters + solidity
        for (Material m : Material.values()) {
            if (!m.isBlock()) continue;
            if (m.isAir()) continue;
            if (!m.isSolid()) continue;
            if (!isFullCubeByName(m.name())) continue;
            fullBlocks.add(m);
        }
        // Try to read textures from dataFolder/resourcepack or classpath/remote fallback
        File rp = new File(plugin.getDataFolder(), "resourcepack/assets/minecraft/textures/block");
        File cacheDir = new File(plugin.getDataFolder(), "cache/textures/block");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        String mcVer = detectMcVersion();
        plugin.getLogger().info("BlockToneIndex: using MC assets version=" + mcVer + "; cacheDir=" + cacheDir.getPath());
        int found = 0, fetched = 0, cached = 0, heur = 0;
        for (Material m : fullBlocks) {
            Tone t = null;
            String base = m.name().toLowerCase(Locale.ROOT);
            try {
                // Local resourcepack
                File imgFile = new File(rp, base + ".png");
                if (!imgFile.exists() && base.endsWith("_block")) {
                    imgFile = new File(rp, base.substring(0, base.length() - 6) + ".png");
                }
                if (imgFile.exists()) {
                    BufferedImage img = ImageIO.read(imgFile);
                    if (img != null) { t = computeTone(img); found++; }
                }
                // Classpath (if plugin bundles any)
                if (t == null) {
                    try (var in = NeoBuildBattleCore.getInstance().getResource("assets/minecraft/textures/block/" + base + ".png")) {
                        if (in != null) {
                            BufferedImage img = ImageIO.read(in);
                            if (img != null) { t = computeTone(img); found++; }
                        }
                    } catch (Throwable ignored2) {}
                }
                // Cache or remote fallback (vanilla repository mirror)
                if (t == null) {
                    // first, cache
                    File cachedFile = new File(cacheDir, base + ".png");
                    if (!cachedFile.exists() && base.endsWith("_block")) cachedFile = new File(cacheDir, base.substring(0, base.length() - 6) + ".png");
                    if (cachedFile.exists()) {
                        try { BufferedImage img = ImageIO.read(cachedFile); if (img != null) { t = computeTone(img); cached++; } } catch (Throwable ignored3) {}
                    }
                    if (t == null) {
                        BufferedImage img = tryFetchVanillaTexture(plugin, base, mcVer, cachedFile);
                        if (img == null && base.endsWith("_block")) {
                            cachedFile = new File(cacheDir, base.substring(0, base.length() - 6) + ".png");
                            img = tryFetchVanillaTexture(plugin, base.substring(0, base.length() - 6), mcVer, cachedFile);
                        }
                        if (img != null) { t = computeTone(img); fetched++; }
                    }
                }
            } catch (Throwable ignored) {}
            if (t == null) { t = heuristicTone(m); heur++; }
            tones.put(m, t);
        }
        plugin.getLogger().info("BlockToneIndex: textures processed: local=" + found + ", cache=" + cached + ", remote=" + fetched + ", heuristic=" + heur + ", total=" + tones.size());
        // Build gradients for all full blocks (exactly 8 items)
        int gcount = 0;
        for (Material m : fullBlocks) {
            gradients.put(m, computeGradientFor(m));
            gcount++;
        }
        plugin.getLogger().info("BlockToneIndex: gradients computed=" + gcount);
        // Save
        try {
            File file = new File(plugin.getDataFolder(), "block_tones.yml");
            YamlConfiguration y = new YamlConfiguration();
            for (Material m : tones.keySet()) {
                Tone t = tones.get(m);
                y.set(m.name() + ".h", t.hue);
                y.set(m.name() + ".s", t.sat);
                y.set(m.name() + ".l", t.lum);
                y.set(m.name() + ".full", fullBlocks.contains(m));
                java.util.List<Material> grad = gradients.get(m);
                if (grad != null && !grad.isEmpty()) {
                    java.util.List<String> names = new java.util.ArrayList<>();
                    for (Material gm : grad) names.add(gm.name());
                    y.set(m.name() + ".gradient", names);
                }
            }
            y.save(file);
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to save block_tones.yml: " + t.getMessage());
        }
    }

    private Tone computeTone(BufferedImage img) {
        long rSum = 0, gSum = 0, bSum = 0; int c = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >> 24) & 0xFF; if (a < 16) continue;
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                rSum += r; gSum += g; bSum += b; c++;
            }
        }
        if (c == 0) return new Tone(0.0, 0.0, 0.5);
        double r = rSum / (double) c / 255.0;
        double g = gSum / (double) c / 255.0;
        double b = bSum / (double) c / 255.0;
        return rgbToHsl(r, g, b);
    }

    private Tone heuristicTone(Material m) {
        String n = m.name();
        if (n.contains("BLACK") || n.contains("DEEP") || n.contains("DARK")) return new Tone(0.0, 0.0, 0.12);
        if (n.contains("WHITE") || n.contains("LIGHT") || n.contains("QUARTZ")) return new Tone(0.0, 0.0, 0.9);
        if (n.contains("RED")) return new Tone(0.02, 0.7, 0.4);
        if (n.contains("BLUE")) return new Tone(0.6, 0.6, 0.35);
        if (n.contains("GREEN")) return new Tone(0.33, 0.6, 0.4);
        if (n.contains("BROWN") || n.contains("GRANITE")) return new Tone(0.05, 0.6, 0.35);
        if (n.contains("GRAY") || n.contains("STONE") || n.contains("COBBLE")) return new Tone(0.0, 0.0, 0.5);
        return new Tone(0.0, 0.0, 0.6);
    }

    private boolean isFullCubeByName(String n) {
        // reject any known non-full/utility/plant/redstone/aquatic/interactive blocks
        if (n.equals("LIGHT") || n.equals("STRUCTURE_BLOCK") || n.equals("JIGSAW") || n.equals("BARRIER") || n.equals("STRUCTURE_VOID") || n.equals("COMMAND_BLOCK")) return false;
        if (n.endsWith("_SLAB") || n.endsWith("_STAIRS") || n.contains("WALL") || n.contains("FENCE") || n.contains("PANE") || n.contains("DOOR") || n.contains("TRAPDOOR") || n.contains("BED") || n.contains("SIGN") || n.contains("TORCH") || n.contains("LANTERN") || n.contains("SCULK_SHRIEKER") || n.contains("SCULK_SENSOR")) return false;
        if (n.contains("BANNER") || n.contains("PRESSURE_PLATE") || n.contains("BUTTON") || n.contains("CARPET") || n.contains("HEAD") || n.contains("SKULL") || n.contains("CHAIN") || n.contains("BARREL") || n.contains("CAMPFIRE") || n.contains("CAULDRON") || n.contains("LECTERN") || n.contains("CANDLE")) return false;
        // exclude redstone components except full blocks (lamp, block)
        if (n.startsWith("REDSTONE_") && !n.equals("REDSTONE_BLOCK") && !n.equals("REDSTONE_LAMP")) return false;
        if (n.contains("REPEATER") || n.contains("COMPARATOR") || n.equals("LEVER") || n.contains("TRIPWIRE")) return false;
        // minerals/fluids/plants
        if (n.contains("ORE") || n.contains("ANCIENT_DEBRIS")) return false;
        if (n.equals("WATER") || n.equals("LAVA") || n.contains("BUBBLE_COLUMN")) return false;
        if (n.contains("KELP") || n.contains("SEAGRASS") || n.contains("SEA_PICKLE") || n.contains("CORAL") || n.contains("BAMBOO") || n.contains("SUGAR_CANE") || n.contains("LILY") || n.contains("MOSS") || n.contains("VINE") || n.contains("FLOWER") || n.contains("SAPLING") || n.contains("LEAVES")) return false;
        // glazed terracotta & shulkers excluded
        if (n.contains("GLAZED_TERRACOTTA")) return false;
        if (n.contains("SHULKER_BOX")) return false;
        // anvils, farmland, lightning rods etc.
        if (n.contains("ANVIL") || n.contains("FARMLAND") || n.contains("LIGHTNING_ROD") || n.contains("GRINDSTONE") || n.contains("LADDER") || n.contains("SCULK_CATALYST") || n.contains("BELL") || n.contains("HANGING_SIGN")) return false;
        return true;
    }

    private Tone rgbToHsl(double r, double g, double b) {
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double h, s, l = (max + min) / 2.0;
        if (max == min) { h = s = 0.0; }
        else {
            double d = max - min;
            s = l > 0.5 ? d / (2.0 - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6.0 : 0.0);
            else if (max == g) h = (b - r) / d + 2.0; else h = (r - g) / d + 4.0;
            h /= 6.0;
        }
        return new Tone(h, s, l);
    }

    private BufferedImage tryFetchVanillaTexture(NeoBuildBattleCore plugin, String name, String mcVer, File cacheTarget) {
        try {
            // GitHub mirror of Minecraft assets; version uses server's MC version
            String url = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/" + mcVer + "/assets/minecraft/textures/block/" + name + ".png";
            java.net.URL u = new java.net.URL(url);
            java.net.URLConnection c = u.openConnection();
            c.setConnectTimeout(2500); c.setReadTimeout(2500);
            try (var in = c.getInputStream()) {
                BufferedImage img = ImageIO.read(in);
                if (img != null && cacheTarget != null) {
                    try { ImageIO.write(img, "png", cacheTarget); } catch (Throwable ignored) {}
                }
                return img;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private String detectMcVersion() {
        try {
            String v = Bukkit.getBukkitVersion(); // e.g. 1.21.6-R0.1-SNAPSHOT
            if (v == null || v.isBlank()) return "1.21.6";
            int dash = v.indexOf('-');
            if (dash > 0) v = v.substring(0, dash);
            return v;
        } catch (Throwable t) {
            return "1.21.6";
        }
    }

    public List<Material> nearest(Material base, int k) {
        Tone tb = tones.get(base);
        if (tb == null) return List.of(base);
        List<Material> cands = new ArrayList<>(fullBlocks);
        cands.remove(base);
        cands.sort(Comparator.comparingDouble(m -> dist(tb, tones.get(m))));
        List<Material> out = new ArrayList<>();
        for (int i = 0; i < Math.min(k, cands.size()); i++) out.add(cands.get(i));
        return out;
    }

    // Return up to maxCount materials from same hue family (within hueTol), sorted by luminance and centered around base luminance
    public List<Material> hueBand(Material base, int maxCount, double hueTol) {
        Tone tb = tones.get(base);
        if (tb == null) return List.of(base);
        List<Material> band = new ArrayList<>();
        for (Material m : fullBlocks) {
            Tone t = tones.get(m);
            if (t == null) continue;
            double dh = Math.min(Math.abs(tb.hue - t.hue), 1.0 - Math.abs(tb.hue - t.hue));
            if (dh <= hueTol) band.add(m);
        }
        if (!band.contains(base)) band.add(base);
        band.sort(Comparator.comparingDouble(m -> tones.get(m).lum));
        // choose around base luminance
        int idx = 0; double best = 1e9;
        for (int i = 0; i < band.size(); i++) {
            double dl = Math.abs(tones.get(band.get(i)).lum - tb.lum);
            if (dl < best) { best = dl; idx = i; }
        }
        List<Material> out = new ArrayList<>();
        out.add(band.get(idx));
        int left = idx - 1, right = idx + 1;
        while (out.size() < Math.min(maxCount, band.size()) && (left >= 0 || right < band.size())) {
            if (right < band.size()) out.add(band.get(right++));
            if (out.size() >= maxCount) break;
            if (left >= 0) out.add(band.get(left--));
        }
        return out;
    }

    public List<Material> gradientFor(Material base) {
        List<Material> g = gradients.get(base);
        if (g != null && g.size() == 8) return g;
        g = computeGradientFor(base);
        gradients.put(base, g);
        return g;
    }

    private List<Material> computeGradientFor(Material base) {
        // family by hue; expand tolerance until 8 or max
        double tol = 0.05;
        List<Material> band = new ArrayList<>();
        while (band.size() < 8 && tol <= 0.25) {
            band = hueBand(base, Integer.MAX_VALUE, tol);
            // Filter by glass family rule
            boolean glass = base.name().contains("GLASS");
            if (glass) band.removeIf(m -> !m.name().contains("GLASS")); else band.removeIf(m -> m.name().contains("GLASS"));
            // Sort by luminance and center around base
            Tone tb = tones.get(base);
            band.sort(Comparator.comparingDouble(m -> tones.get(m).lum));
            List<Material> pick = new ArrayList<>();
            int idx = 0; double best = 1e9;
            for (int i = 0; i < band.size(); i++) {
                double dl = Math.abs(tones.get(band.get(i)).lum - tb.lum);
                if (dl < best) { best = dl; idx = i; }
            }
            pick.add(band.get(Math.min(idx, Math.max(0, band.size()-1))));
            int left = idx - 1, right = idx + 1;
            while (pick.size() < 8 && (left >= 0 || right < band.size())) {
                if (right < band.size()) pick.add(band.get(right++));
                if (pick.size() < 8 && left >= 0) pick.add(band.get(left--));
            }
            if (pick.size() >= 8) {
                return pick.subList(0, 8);
            }
            tol += 0.03;
        }
        // Not enough: pad with base
        List<Material> result = new ArrayList<>();
        result.add(base);
        while (result.size() < 8) result.add(base);
        return result;
    }

    private double dist(Tone a, Tone b) {
        if (b == null) return 1e9;
        double dh = Math.min(Math.abs(a.hue - b.hue), 1.0 - Math.abs(a.hue - b.hue));
        double ds = Math.abs(a.sat - b.sat);
        double dl = Math.abs(a.lum - b.lum);
        return dh * 2.0 + ds * 0.5 + dl * 1.0;
    }
}


