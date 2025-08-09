package com.neobuildbattle.core.build.selection;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable selection state per player. Holds shape mode and control points.
 */
public final class Selection {
    private final SelectionMode mode;
    private final List<Location> points;

    public Selection(SelectionMode mode, List<Location> points) {
        this.mode = mode;
        this.points = points == null ? List.of() : List.copyOf(points);
    }

    public SelectionMode getMode() { return mode; }
    public List<Location> getPoints() { return points; }

    public boolean isEmpty() { return points.isEmpty(); }

    public Selection withMode(SelectionMode newMode) {
        return new Selection(newMode, Collections.emptyList());
    }

    public Selection withAddedPoint(Location loc) {
        List<Location> copy = new ArrayList<>(points);
        copy.add(loc.clone());
        return new Selection(mode, copy);
    }

    public Selection withFirstPoint(Location loc) {
        List<Location> copy = new ArrayList<>(points);
        if (copy.isEmpty()) copy.add(loc.clone()); else copy.set(0, loc.clone());
        return new Selection(mode, copy);
    }

    public Selection withSecondPoint(Location loc) {
        List<Location> copy = new ArrayList<>(points);
        if (copy.size() < 1) copy.add(loc.clone());
        if (copy.size() == 1) copy.add(loc.clone()); else copy.set(1, loc.clone());
        return new Selection(mode, copy);
    }

    public Vector getMin() {
        if (points.isEmpty()) return null;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        for (Location l : points) {
            minX = Math.min(minX, l.getBlockX());
            minY = Math.min(minY, l.getBlockY());
            minZ = Math.min(minZ, l.getBlockZ());
        }
        return new Vector(minX, minY, minZ);
    }

    public Vector getMax() {
        if (points.isEmpty()) return null;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Location l : points) {
            maxX = Math.max(maxX, l.getBlockX());
            maxY = Math.max(maxY, l.getBlockY());
            maxZ = Math.max(maxZ, l.getBlockZ());
        }
        return new Vector(maxX, maxY, maxZ);
    }
}


