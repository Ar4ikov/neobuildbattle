package com.neobuildbattle.core.build.click;

import com.neobuildbattle.core.build.BuildToolsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ParticlesClickHandler implements GuiClickHandler {
    private final BuildToolsManager mgr;
    public ParticlesClickHandler(BuildToolsManager mgr) { this.mgr = mgr; }

    @Override
    public void handle(Player player, int rawSlot, InventoryClickEvent event) {
        int topSize = player.getOpenInventory().getTopInventory().getSize();
        if (rawSlot < 0 || rawSlot >= topSize) return;
        ItemStack clicked = player.getOpenInventory().getTopInventory().getItem(rawSlot);
        if (clicked == null) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        var pdc = meta.getPersistentDataContainer();
        if (pdc.has(mgr.getParticleTypeKey(), PersistentDataType.STRING) || pdc.has(mgr.getParticleEraserKey(), PersistentDataType.INTEGER)) {
            player.getInventory().addItem(clicked.clone());
        }
    }
}


