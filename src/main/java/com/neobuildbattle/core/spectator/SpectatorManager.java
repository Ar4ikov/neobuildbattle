package com.neobuildbattle.core.spectator;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.plot.Plot;
import org.bukkit.NamespacedKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

public final class SpectatorManager implements Listener {

    private static final String TELEPORT_GUI_TITLE = "Телепорт к игроку";

    private final NeoBuildBattleCore plugin;
    private final java.util.Set<java.util.UUID> currentSpectators = new java.util.HashSet<>();
    private final NamespacedKey teleportKey;

    public SpectatorManager(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
        this.teleportKey = new NamespacedKey(plugin, "teleport_target");
    }

    public void makeSpectator(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.getInventory().clear();
        // Do NOT use invisibility to avoid model packets; rely solely on per-recipient hide/show
        player.getActivePotionEffects().forEach(pe -> player.removePotionEffect(pe.getType()));
        giveCompass(player);
        currentSpectators.add(player.getUniqueId());
        // Hide spectator from everyone and hide other spectators from him
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            other.hidePlayer(plugin, player);
            if (currentSpectators.contains(other.getUniqueId())) {
                player.hidePlayer(plugin, other);
            }
        }
    }

    private void giveCompass(Player player) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.displayName(Component.text("Телепорт"));
        compass.setItemMeta(meta);
        player.getInventory().setItem(0, compass);
    }

    @EventHandler
    public void onCompassUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.COMPASS) return;
        Player p = e.getPlayer();
        // Only for spectators (non-participants)
        boolean isSpectator = !plugin.getPlayerRegistry().getActivePlayers().contains(p.getUniqueId());
        if (!isSpectator) return;
        openTeleportGui(p);
        e.setCancelled(true);
    }

    private void openTeleportGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(TELEPORT_GUI_TITLE));
        int slot = 0;
        for (UUID ownerId : plugin.getPlotManager().getAllOwners()) {
            if (slot >= inv.getSize()) break;
            OfflinePlayer offline = Bukkit.getOfflinePlayer(ownerId);
            String name = offline.getName() != null ? offline.getName() : ownerId.toString().substring(0, 8);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(offline);
            meta.displayName(Component.text(name));
            // Store owner UUID for reliable teleport
            meta.getPersistentDataContainer().set(teleportKey, org.bukkit.persistence.PersistentDataType.STRING, ownerId.toString());
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onTeleportClick(InventoryClickEvent e) {
        // Identify our items by PersistentDataContainer key
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        String idStr = meta.getPersistentDataContainer().get(teleportKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (idStr == null) return;
        e.setCancelled(true);
        HumanEntity clicker = e.getWhoClicked();
        try {
            UUID ownerId = java.util.UUID.fromString(idStr);
            Plot plot = plugin.getPlotManager().getPlotByOwner(ownerId);
            if (plot != null) {
                clicker.teleport(plot.getViewLocation());
            }
        } catch (IllegalArgumentException ignored) { }
    }

    public void showAll() {
        // Reveal everyone and clear spectator set
        for (Player a : Bukkit.getOnlinePlayers()) {
            for (Player b : Bukkit.getOnlinePlayers()) {
                if (!a.equals(b)) {
                    a.showPlayer(plugin, b);
                }
            }
        }
        currentSpectators.clear();
    }
}


