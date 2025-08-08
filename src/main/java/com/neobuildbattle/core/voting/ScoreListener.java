package com.neobuildbattle.core.voting;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScoreListener implements Listener {
    private final Map<UUID, Integer> lastScore = new ConcurrentHashMap<>();

    @EventHandler
    public void onClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null) return;
        Material m = item.getType();
        int score = switch (m) {
            case RED_TERRACOTTA -> 1;
            case ORANGE_TERRACOTTA -> 2;
            case YELLOW_TERRACOTTA -> 3;
            case LIME_TERRACOTTA -> 4;
            case GREEN_TERRACOTTA -> 5;
            case EMERALD_BLOCK -> 6;
            default -> 0;
        };
        if (score > 0) {
            lastScore.put(p.getUniqueId(), score);
            p.sendActionBar(Component.text("Ваш голос: " + score));
            e.setCancelled(true);
        }
    }

    public int consumeScore(UUID voterId) {
        return lastScore.getOrDefault(voterId, 0);
    }

    public void reset() {
        lastScore.clear();
    }
}


