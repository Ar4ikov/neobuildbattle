package com.neobuildbattle.core.build.click;

import com.neobuildbattle.core.build.BuildToolsManager;
import com.neobuildbattle.core.build.time.TimeGui;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class TimeClickHandler implements GuiClickHandler {
    private final BuildToolsManager mgr;
    public TimeClickHandler(BuildToolsManager mgr) { this.mgr = mgr; }

    @Override
    public void handle(Player player, int rawSlot, InventoryClickEvent event) {
        Integer ticks = TimeGui.timeTicksForSlot(rawSlot);
        if (ticks == null) return;
        mgr.applyTime(player, ticks);
    }
}


