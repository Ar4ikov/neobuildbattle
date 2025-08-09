package com.neobuildbattle.core.build.click;

import com.neobuildbattle.core.build.BuildToolsManager;
import com.neobuildbattle.core.build.weather.WeatherGui;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class WeatherClickHandler implements GuiClickHandler {
    private final BuildToolsManager mgr;
    public WeatherClickHandler(BuildToolsManager mgr) { this.mgr = mgr; }

    @Override
    public void handle(Player player, int rawSlot, InventoryClickEvent event) {
        var wt = WeatherGui.weatherForSlot(rawSlot);
        if (wt == null) return;
        boolean storm = WeatherGui.isStorm(rawSlot);
        mgr.applyWeather(player, wt, storm);
    }
}


