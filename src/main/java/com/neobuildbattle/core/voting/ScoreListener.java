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
        // Only участники голосуют; для остальных просто игнорируем, не отменяя, чтобы компас спектатора работал
        if (!com.neobuildbattle.core.NeoBuildBattleCore.getInstance().getPlayerRegistry().getActivePlayers().contains(p.getUniqueId())) return;
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

    public void clearVotingItems(Player p) {
        // Remove score items from hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it == null) continue;
            switch (it.getType()) {
                case RED_TERRACOTTA, ORANGE_TERRACOTTA, YELLOW_TERRACOTTA, LIME_TERRACOTTA, GREEN_TERRACOTTA, EMERALD_BLOCK -> p.getInventory().setItem(i, null);
                default -> {}
            }
        }
    }
}


