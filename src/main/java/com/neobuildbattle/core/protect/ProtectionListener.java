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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

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
        boolean isSpectator = !plugin.getPlayerRegistry().getActivePlayers().contains(p.getUniqueId());
        return s == GameState.THEME_VOTING || s == GameState.EVALUATION || isSpectator;
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

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!inVotingOrSpectator(p)) return;
        // Разрешаем только использование нашего компаса у спектаторов; остальное блокируем
        if (e.getItem() != null && e.getItem().getType() == org.bukkit.Material.COMPASS) {
            // Если это участник — компас им обычно не выдаётся; если выдан, не блокируем
            return;
        }
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_BLOCK || a == Action.LEFT_CLICK_BLOCK || a == Action.PHYSICAL) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (inVotingOrSpectator(p)) {
            e.setCancelled(true);
        }
    }
}


