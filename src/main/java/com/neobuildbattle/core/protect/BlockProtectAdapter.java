package com.neobuildbattle.core.protect;

import com.neobuildbattle.core.plot.Plot;
import org.bukkit.Location;

import java.util.function.Function;

/**
 * Lightweight adapter to integrate with external BlockProtect plugin.
 * Replace with real calls when dependency is available.
 */
public final class BlockProtectAdapter {
    private final Function<Location, Plot> plotResolver;

    public BlockProtectAdapter(Function<Location, Plot> plotResolver) {
        this.plotResolver = plotResolver;
    }

    public boolean canBuild(Location location, Plot playerPlot) {
        Plot at = plotResolver.apply(location);
        return at != null && playerPlot != null && at.equals(playerPlot);
    }
}


