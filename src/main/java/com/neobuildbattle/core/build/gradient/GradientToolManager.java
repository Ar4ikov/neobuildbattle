package com.neobuildbattle.core.build.gradient;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.build.pattern.BlockPattern;
import com.neobuildbattle.core.build.pattern.GradientTones;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Picks a gradient around a block tone using predefined tone matrix and stores it in NBT item, also feeds BlockPattern.
 */
public final class GradientToolManager implements Listener {
    private final NeoBuildBattleCore plugin;
    private final NamespacedKey pickerKey;

    // Precomputed tonal matrix: material -> 1..8 tone index mapping; and neighbors
    private final Map<Material, Integer> toneIndex = new EnumMap<>(Material.class);
    private final Map<Material, List<Material>> toneBands = new EnumMap<>(Material.class);

    public GradientToolManager(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
        this.pickerKey = new NamespacedKey(plugin, "gradient_picker");
        loadToneMatrix();
    }

    public ItemStack createPickerItem() {
        ItemStack it = new ItemStack(Material.PAINTING);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ChatColor.BLUE + "Пипетка градиента");
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(pickerKey, PersistentDataType.INTEGER, 1);
        it.setItemMeta(meta);
        return it;
    }

    public NamespacedKey getPickerKey() { return pickerKey; }

    private void loadToneMatrix() {
        // Minimal illustrative mapping; in a full version, load from a YAML tone matrix resource
        putTone(Material.BLACK_CONCRETE, 1);
        putTone(Material.GRAY_CONCRETE, 4);
        putTone(Material.LIGHT_GRAY_CONCRETE, 6);
        putTone(Material.WHITE_CONCRETE, 8);
        putTone(Material.STONE, 5);
        putTone(Material.COBBLESTONE, 4);
        putTone(Material.DIORITE, 7);
        putTone(Material.ANDESITE, 5);
        putTone(Material.GRANITE, 5);
    }

    private void putTone(Material m, int tone) {
        toneIndex.put(m, tone);
        toneBands.put(m, computeBand(m, tone));
    }

    private List<Material> computeBand(Material base, int tone) {
        // For base version: select adjacent materials with nearby tones if present in map
        List<Material> list = new ArrayList<>();
        for (Map.Entry<Material, Integer> e : toneIndex.entrySet()) {
            int t = e.getValue();
            if (Math.abs(t - tone) <= 7) list.add(e.getKey());
        }
        if (list.isEmpty()) list.add(base);
        // ensure <=8
        if (list.size() > 8) return list.subList(0, 8);
        return list;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack it = e.getItem();
        if (it == null) return;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(pickerKey, PersistentDataType.INTEGER)) return;
        e.setCancelled(true);
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        Material m = b.getType();
        List<Material> band = computeGradientBandFor(m);
        GradientTones tones = new GradientTones(band);
        // push into player's pattern as gradient with weight 1 by default
        var pattern = plugin.getAdvancedToolsManager().getOrCreatePattern(e.getPlayer());
        pattern.setGradient(tones);
        pattern.setGradientWeight(pattern.getGradientWeight() + 1);
        e.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.BLUE + "Градиент выбран по " + m.name()));
        // also add an inventory token with NBT storing the chosen gradient base
        ItemStack token = new ItemStack(Material.PAPER);
        ItemMeta tm = token.getItemMeta();
        tm.setDisplayName(ChatColor.BLUE + "Градиент: " + m.name());
        tm.getPersistentDataContainer().set(pickerKey, PersistentDataType.INTEGER, 2);
        token.setItemMeta(tm);
        e.getPlayer().getInventory().addItem(token);
    }

    // Compute tones based on perceived luminance of material textures (approx via hardcoded heuristic per family)
    private List<Material> computeGradientBandFor(Material base) {
        // prefer cached tone index if available
        try {
            var idx = plugin.getBlockToneIndex();
            if (idx != null) {
                List<Material> nn = new ArrayList<>();
                nn.add(base);
                nn.addAll(idx.nearest(base, 7));
                return nn;
            }
        } catch (Throwable ignored) {}
        // If known band exists, prefer it
        List<Material> preset = toneBands.get(base);
        if (preset != null && !preset.isEmpty()) return preset;
        // fallback: generate a simple 6-8 tone band sorted by estimated luminance among similar family blocks
        List<Material> candidates = new ArrayList<>();
        for (Material m : Material.values()) {
            if (!m.isBlock()) continue;
            String n = m.name();
            // take stone-like family as baseline; extend with more families as needed
            if (n.contains("STONE") || n.contains("ANDESITE") || n.contains("DIORITE") || n.contains("GRANITE") || n.contains("DEEPSLATE") || n.contains("BLACKSTONE")) {
                candidates.add(m);
            }
        }
        candidates.sort(java.util.Comparator.comparingDouble(this::estimateLuminance));
        int idx = Math.max(0, candidates.indexOf(base));
        int from = Math.max(0, idx - 3);
        int to = Math.min(candidates.size(), from + 8);
        return candidates.subList(from, to);
    }

    private double estimateLuminance(Material m) {
        String n = m.name();
        // very rough keyword-based luminance (placeholder for texture sampling)
        if (n.contains("BLACK") || n.contains("DEEP") || n.contains("DARK")) return 0.1;
        if (n.contains("GRAY") || n.contains("COBBLE") || n.contains("STONE") || n.contains("DEEPSLATE") || n.contains("BLACKSTONE")) return 0.45;
        if (n.contains("LIGHT") || n.contains("WHITE") || n.contains("QUARTZ")) return 0.9;
        if (n.contains("RED") || n.contains("BROWN") || n.contains("BRICK")) return 0.35;
        return 0.6;
    }
}


