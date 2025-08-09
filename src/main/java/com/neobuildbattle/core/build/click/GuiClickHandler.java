package com.neobuildbattle.core.build.click;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public interface GuiClickHandler {
    void handle(Player player, int rawSlot, InventoryClickEvent event);
}


