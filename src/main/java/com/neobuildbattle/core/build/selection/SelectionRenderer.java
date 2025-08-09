package com.neobuildbattle.core.build.selection;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Renders selection edges and vertices using dense HAPPY_VILLAGER particles, visible only to the player.
 */
public final class SelectionRenderer {

    private SelectionRenderer() {}

    public static void render(Player player, Selection selection) {
        if (selection == null || selection.isEmpty()) return;
        if (player == null) return;
        List<Location> pts = selection.getPoints();
        SelectionMode mode = selection.getMode();
        switch (mode) {
            case CUBOID -> renderCuboid(player, pts);
            case SPHERE -> renderSphere(player, pts);
            case ELLIPSOID -> renderEllipsoid(player, pts);
            case POLYGON -> renderPolygon(player, pts);
        }
    }

    private static void renderCuboid(Player p, List<Location> pts) {
        if (pts.size() < 1) return;
        Location a = pts.get(0);
        Location b = pts.size() >= 2 ? pts.get(1) : a;
        World w = p.getWorld();
        Vector min = new Vector(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        Vector max = new Vector(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        // draw 12 edges
        drawLine(p, w, new Vector(min.getX(), min.getY(), min.getZ()), new Vector(max.getX(), min.getY(), min.getZ()));
        drawLine(p, w, new Vector(min.getX(), min.getY(), max.getZ()), new Vector(max.getX(), min.getY(), max.getZ()));
        drawLine(p, w, new Vector(min.getX(), max.getY(), min.getZ()), new Vector(max.getX(), max.getY(), min.getZ()));
        drawLine(p, w, new Vector(min.getX(), max.getY(), max.getZ()), new Vector(max.getX(), max.getY(), max.getZ()));
        drawLine(p, w, new Vector(min.getX(), min.getY(), min.getZ()), new Vector(min.getX(), max.getY(), min.getZ()));
        drawLine(p, w, new Vector(max.getX(), min.getY(), min.getZ()), new Vector(max.getX(), max.getY(), min.getZ()));
        drawLine(p, w, new Vector(min.getX(), min.getY(), max.getZ()), new Vector(min.getX(), max.getY(), max.getZ()));
        drawLine(p, w, new Vector(max.getX(), min.getY(), max.getZ()), new Vector(max.getX(), max.getY(), max.getZ()));
        drawLine(p, w, new Vector(min.getX(), min.getY(), min.getZ()), new Vector(min.getX(), min.getY(), max.getZ()));
        drawLine(p, w, new Vector(max.getX(), min.getY(), min.getZ()), new Vector(max.getX(), min.getY(), max.getZ()));
        drawLine(p, w, new Vector(min.getX(), max.getY(), min.getZ()), new Vector(min.getX(), max.getY(), max.getZ()));
        drawLine(p, w, new Vector(max.getX(), max.getY(), min.getZ()), new Vector(max.getX(), max.getY(), max.getZ()));
    }

    private static void renderSphere(Player p, List<Location> pts) {
        if (pts.size() < 2) return;
        Location center = pts.get(0);
        Location edge = pts.get(1);
        double r = center.distance(edge);
        if (r <= 0.2) r = 0.2;
        World w = p.getWorld();
        // Draw three orthogonal circles
        drawCircle(p, w, center, r, Axis.XY);
        drawCircle(p, w, center, r, Axis.XZ);
        drawCircle(p, w, center, r, Axis.YZ);
    }

    private static void renderEllipsoid(Player p, List<Location> pts) {
        if (pts.size() < 2) return;
        Location a = pts.get(0);
        Location b = pts.get(1);
        Location center = a.clone().add(b).multiply(0.5);
        double rx = Math.max(0.2, Math.abs(a.getX() - b.getX()) / 2.0);
        double ry = Math.max(0.2, Math.abs(a.getY() - b.getY()) / 2.0);
        double rz = Math.max(0.2, Math.abs(a.getZ() - b.getZ()) / 2.0);
        World w = p.getWorld();
        drawEllipse(p, w, center, rx, ry, Axis.XY);
        drawEllipse(p, w, center, rx, rz, Axis.XZ);
        drawEllipse(p, w, center, ry, rz, Axis.YZ);
    }

    private static void renderPolygon(Player p, List<Location> pts) {
        if (pts.size() < 2) return;
        World w = p.getWorld();
        for (int i = 0; i < pts.size(); i++) {
            Location a = pts.get(i);
            Location b = pts.get((i + 1) % pts.size());
            drawLine(p, w, a.toVector(), b.toVector());
        }
    }

    private enum Axis { XY, XZ, YZ }

    private static void drawCircle(Player p, World w, Location center, double r, Axis axis) {
        int steps = (int) Math.max(24, r * 32); // dense
        for (int i = 0; i < steps; i++) {
            double a = (2 * Math.PI) * (i / (double) steps);
            double x = Math.cos(a) * r;
            double y = Math.sin(a) * r;
            Location loc = center.clone();
            switch (axis) {
                case XY -> loc.add(x, y, 0);
                case XZ -> loc.add(x, 0, y);
                case YZ -> loc.add(0, x, y);
            }
            p.spawnParticle(Particle.HAPPY_VILLAGER, loc, 1, 0, 0, 0, 0);
        }
    }

    private static void drawEllipse(Player p, World w, Location center, double r1, double r2, Axis axis) {
        int steps = (int) Math.max(24, Math.max(r1, r2) * 32);
        for (int i = 0; i < steps; i++) {
            double a = (2 * Math.PI) * (i / (double) steps);
            double x = Math.cos(a) * r1;
            double y = Math.sin(a) * r2;
            Location loc = center.clone();
            switch (axis) {
                case XY -> loc.add(x, y, 0);
                case XZ -> loc.add(x, 0, y);
                case YZ -> loc.add(0, x, y);
            }
            p.spawnParticle(Particle.HAPPY_VILLAGER, loc, 1, 0, 0, 0, 0);
        }
    }

    private static void drawLine(Player p, World w, Vector start, Vector end) {
        Vector dir = end.clone().subtract(start);
        double len = dir.length();
        if (len <= 0.0001) return;
        dir.multiply(1.0 / len);
        double step = 0.25; // dense line
        for (double d = 0; d <= len; d += step) {
            Vector pos = start.clone().add(dir.clone().multiply(d));
            Location loc = new Location(w, pos.getX(), pos.getY(), pos.getZ());
            p.spawnParticle(Particle.HAPPY_VILLAGER, loc, 1, 0, 0, 0, 0);
        }
    }
}


