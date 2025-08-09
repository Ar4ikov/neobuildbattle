package com.neobuildbattle.core.build.click;

import com.neobuildbattle.core.build.BuildToolsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class MainClickHandler implements GuiClickHandler {
    private final BuildToolsManager mgr;
    public MainClickHandler(BuildToolsManager mgr) { this.mgr = mgr; }

    @Override
    public void handle(Player player, int rawSlot, InventoryClickEvent event) {
        if (rawSlot == 10) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && mgr.canUseAsFloor(cursor.getType())) {
                mgr.applyFloor(player, cursor.getType());
                mgr.openMainGui(player);
            } else {
                ItemStack current = event.getCurrentItem();
                if (current != null && mgr.canUseAsFloor(current.getType())) {
                    mgr.applyFloor(player, current.getType());
                    mgr.openMainGui(player);
                }
            }
        } else if (rawSlot == 11) {
            mgr.openToolsGui(player);
        } else if (rawSlot == 12) {
            mgr.openTimeGui(player);
        } else if (rawSlot == 13) {
            mgr.openBiomesGui(player);
        } else if (rawSlot == 14) {
            mgr.openWeatherGui(player);
        } else if (rawSlot == 15) {
            mgr.openParticlesGui(player);
        } else if (rawSlot == 16) {
            mgr.openGradientsGui(player);
        }
    }
}


