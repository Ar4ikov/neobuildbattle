package com.neobuildbattle.core.protect;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.game.GameState;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class ProtectionListener implements Listener {
    private final NeoBuildBattleCore plugin;

    public ProtectionListener(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPvp(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        Entity damager = e.getDamager();
        if (victim instanceof Player) {
            if (damager instanceof Player) {
                e.setCancelled(true);
                return;
            }
            if (damager instanceof Projectile proj && proj.getShooter() instanceof Player) {
                e.setCancelled(true);
            }
        }
    }

    private boolean inVotingOrSpectator(Player p) {
        GameState s = plugin.getGameManager().getState();
        return s == GameState.THEME_VOTING || s == GameState.EVALUATION || p.getGameMode() == GameMode.SPECTATOR;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (inVotingOrSpectator(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && inVotingOrSpectator(p)) {
            e.setCancelled(true);
        }
    }
}


