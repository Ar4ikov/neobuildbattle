package com.neobuildbattle.core.protect;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.game.GameState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
// removed unused imports
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.Material;
// removed unused imports
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockBurnEvent;

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
    public void onDrop(PlayerDropItemEvent e) { e.setCancelled(true); }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        // Disable pickup to avoid item clutter and scoring exploits
        e.setCancelled(true);
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

    // Disable physics for falling blocks and other unstable blocks; keep only fluid flow
    @EventHandler
    public void onPhysics(BlockPhysicsEvent e) {
        Material m = e.getBlock().getType();
        // Отключаем общую физику обновлений (кроме редстоуна и жидкостей) — чтобы растения/листва не рушились
        if (!isRedstoneBlock(m) && m != Material.WATER && m != Material.LAVA) e.setCancelled(true);
    }

    // Allow fluid flow for water/lava; BlockFromToEvent covers spreading
    @EventHandler
    public void onFluid(BlockFromToEvent e) {
        Material m = e.getBlock().getType();
        if (m == Material.WATER || m == Material.LAVA || m == Material.KELP || m == Material.SEAGRASS) {
            return;
        }
        // allow only fluids, cancel other from-to physics
        e.setCancelled(true);
    }

    // Отключаем распад листвы, рост/распространение/формирование растений и т.п.
    @EventHandler public void onLeaves(LeavesDecayEvent e) { e.setCancelled(true); }
    @EventHandler public void onFade(BlockFadeEvent e) { e.setCancelled(true); }
    @EventHandler public void onForm(BlockFormEvent e) { e.setCancelled(true); }
    @EventHandler public void onGrow(BlockGrowEvent e) { e.setCancelled(true); }
    @EventHandler public void onSpread(BlockSpreadEvent e) { e.setCancelled(true); }
    @EventHandler public void onFertilize(BlockFertilizeEvent e) { e.setCancelled(true); }

    private boolean isRedstoneBlock(Material m) {
        String n = m.name();
        return n.startsWith("REDSTONE_") || n.contains("REPEATER") || n.contains("COMPARATOR") || n.equals("LEVER") || n.contains("PISTON") || n.contains("OBSERVER") || n.contains("TRIPWIRE") || n.contains("TARGET");
    }

    // 2) Cancel natural mob spawning (allow only SPAWNER_EGG)
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return;
        e.setCancelled(true);
    }

    // 3) Disable mob griefing (e.g., creeper explosions damaging blocks)
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().clear();
    }

    // Prevent block explosions (e.g., TNT placed as block)
    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().clear();
        e.setYield(0f);
    }

    // Disarm TNT and other primed explosives entirely
    @EventHandler
    public void onExplosionPrime(ExplosionPrimeEvent e) {
        e.setCancelled(true);
    }

    // Prevent any entity from modifying blocks (ender men, wither, ravager, falling blocks placing, etc.)
    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        e.setCancelled(true);
    }

    // Delete any item entities spawned in the world (block drops, deaths, etc.)
    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e) {
        e.setCancelled(true);
    }

    // Also catch non-player item drops (armor stands, mobs, etc.)
    @EventHandler
    public void onEntityDropItem(EntityDropItemEvent e) { e.setCancelled(true); }

    // Prevent block break drops turning into item entities
    @EventHandler
    public void onBlockDrop(BlockDropItemEvent e) {
        e.getItems().forEach(item -> item.remove());
        e.setCancelled(true);
    }

    // Stop fire spread/ignition/burn to protect builds
    @EventHandler
    public void onIgnite(BlockIgniteEvent e) { e.setCancelled(true); }

    @EventHandler
    public void onBurn(BlockBurnEvent e) { e.setCancelled(true); }
}


