package com.neobuildbattle.core.spectator;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.plot.Plot;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public final class SpectatorManager implements Listener {

    private static final String TELEPORT_GUI_TITLE = "Телепорт к игроку";

    private final NeoBuildBattleCore plugin;

    public SpectatorManager(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
    }

    public void makeSpectator(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.getInventory().clear();
        // Infinite invisibility without particles
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, true, false, false));
        giveCompass(player);
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
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onTeleportClick(InventoryClickEvent e) {
        if (!(e.getView().title() instanceof Component component) ||
                !Component.text(TELEPORT_GUI_TITLE).equals(component)) {
            return;
        }
        e.setCancelled(true);
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() != Material.PLAYER_HEAD) return;
        HumanEntity clicker = e.getWhoClicked();
        ItemMeta meta = e.getCurrentItem().getItemMeta();
        String name = meta.hasDisplayName() ? Component.text().content(meta.displayName().toString()).build().content() : null;
        // Fallback: try by slot owner name in skull meta
        if (name == null || name.isEmpty()) {
            if (meta instanceof SkullMeta skullMeta && skullMeta.getOwningPlayer() != null) {
                name = skullMeta.getOwningPlayer().getName();
            }
        }
        if (name == null || name.isEmpty()) return;
        // Teleport to the plot of selected player by name
        UUID ownerId = null;
        for (UUID id : plugin.getPlotManager().getAllOwners()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) {
                ownerId = id;
                break;
            }
        }
        if (ownerId == null) return;
        Plot plot = plugin.getPlotManager().getPlotByOwner(ownerId);
        if (plot == null) return;
        clicker.teleport(plot.getViewLocation());
    }
}


