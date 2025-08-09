package com.neobuildbattle.core.build.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class BuildGuiHolder implements InventoryHolder {
    public final GuiType type;
    public BuildGuiHolder(GuiType type) { this.type = type; }
    @Override public Inventory getInventory() { return Bukkit.createInventory(null, 9); }
}


