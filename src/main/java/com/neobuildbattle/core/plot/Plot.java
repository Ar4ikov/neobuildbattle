package com.neobuildbattle.core.plot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public final class Plot {
    private final UUID ownerId;
    private final World world;
    private final int minX;
    private final int minZ;
    private final int size;
    private final int y;
    private final int maxX;
    private final int maxZ;

    public Plot(UUID ownerId, World world, int minX, int minZ, int size, int y) {
        this.ownerId = ownerId;
        this.world = world;
        this.minX = minX;
        this.minZ = minZ;
        this.size = size;
        this.y = y;
        this.maxX = minX + size - 1;
        this.maxZ = minZ + size - 1;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Location getSpawnLocation() {
        // spawn in the center of build area (offset +3)
        return new Location(world, minX + 3 + (size - 6) / 2.0 + 0.5, y + 1.0, minZ + 3 + (size - 6) / 2.0 + 0.5);
    }

    public Location getViewLocation() {
        return new Location(world, minX + size / 2.0 + 0.5, y + 3.0, minZ + size / 2.0 + 0.5);
    }

    public void giveScoreItems(Player viewer) {
        for (int i = 1; i <= 6; i++) {
            Material mat = switch (i) {
                case 1 -> Material.RED_TERRACOTTA;
                case 2 -> Material.ORANGE_TERRACOTTA;
                case 3 -> Material.YELLOW_TERRACOTTA;
                case 4 -> Material.LIME_TERRACOTTA;
                case 5 -> Material.GREEN_TERRACOTTA;
                default -> Material.EMERALD_BLOCK;
            };
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("Оценка " + i);
            item.setItemMeta(meta);
            viewer.getInventory().setItem(i - 1, item);
        }
    }

    public boolean contains(int x, int z) {
        // Only allow build in inner 65x65 (size-6) region
        int innerMinX = minX + 3;
        int innerMinZ = minZ + 3;
        int innerMaxX = maxX - 3;
        int innerMaxZ = maxZ - 3;
        return x >= innerMinX && z >= innerMinZ && x <= innerMaxX && z <= innerMaxZ;
    }

    public boolean withinMaxHeight(int yBlock, int baseY, int maxBuild) {
        return yBlock >= baseY && yBlock <= baseY + maxBuild;
    }

    public boolean containsLocation(Location location) {
        if (!location.getWorld().equals(world)) return false;
        return contains(location.getBlockX(), location.getBlockZ());
    }

    public int getMinX() { return minX; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxZ() { return maxZ; }
    public int getSize() { return size; }
}


