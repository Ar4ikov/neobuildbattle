package com.neobuildbattle.core.voting;

import com.neobuildbattle.core.NeoBuildBattleCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ThemeVotingManager implements Listener {

    private final NeoBuildBattleCore plugin;
    private final List<String> themes = new ArrayList<>();
    private final Map<UUID, Integer> playerVotes = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> counts = new ConcurrentHashMap<>();
    private List<String> currentPool = Collections.emptyList();
    private Inventory inv;
    private volatile boolean votingActive = false;

    public ThemeVotingManager(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
        loadThemes();
    }

    private void loadThemes() {
        try {
            InputStream is = plugin.getResource("themes.txt");
            if (is == null) return;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String t = line.trim();
                    if (!t.isEmpty()) themes.add(t);
                }
            }
        } catch (Exception ignored) { }
    }

    public void openVotingForAll() {
        playerVotes.clear();
        counts.clear();
        currentPool = pickRandomThemes(5);
        inv = Bukkit.createInventory(null, 9, ChatColor.GREEN + "Выберите тему");
        for (int i = 0; i < currentPool.size(); i++) {
            inv.setItem(i, createThemeItem(currentPool.get(i), 0));
            counts.put(i, 0);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.openInventory(inv);
        }
        votingActive = true;
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshProgress, 0L, 10L);
    }

    private List<String> pickRandomThemes(int n) {
        List<String> copy = new ArrayList<>(themes);
        Collections.shuffle(copy);
        return copy.subList(0, Math.min(n, copy.size()));
    }

    private ItemStack createThemeItem(String name, int percent) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        meta.setLore(Collections.singletonList(ChatColor.GRAY + "Голоса: " + percent + "%"));
        paper.setItemMeta(meta);
        return paper;
    }

    private void refreshProgress() {
        if (inv == null) return;
        int total = Math.max(1, Bukkit.getOnlinePlayers().size());
        for (int i = 0; i < currentPool.size(); i++) {
            int votes = counts.getOrDefault(i, 0);
            int percent = (int) Math.round(100.0 * votes / total);
            inv.setItem(i, createThemeItem(currentPool.get(i), percent));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (inv == null || e.getInventory() != inv) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= currentPool.size()) return;
        Player p = (Player) e.getWhoClicked();
        Integer prev = playerVotes.put(p.getUniqueId(), slot);
        if (prev != null) counts.merge(prev, -1, Integer::sum);
        counts.merge(slot, 1, Integer::sum);
        refreshProgress();
    }

    public Optional<String> getWinningTheme() {
        int bestSlot = -1;
        int bestCount = -1;
        for (int i = 0; i < currentPool.size(); i++) {
            int c = counts.getOrDefault(i, 0);
            if (c > bestCount) {
                bestCount = c;
                bestSlot = i;
            }
        }
        if (bestSlot >= 0) return Optional.of(currentPool.get(bestSlot));
        return Optional.empty();
    }

    public void endVoting() {
        votingActive = false;
        // Close for everyone currently viewing this inventory
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory() != null && p.getOpenInventory().getTopInventory() == inv) {
                p.closeInventory();
            }
        }
        inv = null;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!votingActive || inv == null) return;
        if (e.getInventory() != inv) return;
        Player p = (Player) e.getPlayer();
        // Reopen next tick to prevent closing
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (votingActive && ((org.bukkit.entity.Player)p).isOnline()) {
                p.openInventory(inv);
            }
        });
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!votingActive || inv == null) return;
        // Force our voting GUI; if another inventory was opened, switch back next tick
        if (e.getInventory() != inv) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player pl = (Player) e.getPlayer();
                if (votingActive && pl.isOnline()) {
                    pl.openInventory(inv);
                }
            });
        }
    }
}


