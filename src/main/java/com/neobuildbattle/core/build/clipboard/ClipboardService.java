package com.neobuildbattle.core.build.clipboard;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.plot.Plot;
import com.neobuildbattle.core.plot.PlotManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player clipboard and transforms.
 */
public final class ClipboardService {
    private final NeoBuildBattleCore plugin;
    private final PlotManager plotManager;
    private final Map<UUID, Clipboard> buffers = new HashMap<>();

    public ClipboardService(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
        this.plotManager = plugin.getPlotManager();
    }

    public void copyFromSelection(Player p, Location a, Location b) {
        if (a == null || b == null) return;
        World w = a.getWorld();
        if (w == null || !w.equals(b.getWorld())) return;
        int minX = Math.min(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        int maxX = Math.max(a.getBlockX(), b.getBlockX());
        int maxY = Math.max(a.getBlockY(), b.getBlockY());
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        int sx = maxX - minX + 1;
        int sy = maxY - minY + 1;
        int sz = maxZ - minZ + 1;
        Clipboard clip = new Clipboard(sx, sy, sz);
        // Anchor is the copy point (first selection point 'a') so that copy point == paste point == rotation pivot
        int ax = a.getBlockX() - minX;
        int ay = a.getBlockY() - minY;
        int az = a.getBlockZ() - minZ;
        clip.setAnchor(ax, ay, az);
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    Block bukkitBlock = w.getBlockAt(minX + x, minY + y, minZ + z);
                    clip.set(x, y, z, bukkitBlock.getBlockData());
                }
            }
        }
        buffers.put(p.getUniqueId(), clip);
        p.sendActionBar(net.kyori.adventure.text.Component.text(org.bukkit.ChatColor.GREEN + "Скопировано: " + sx + "x" + sy + "x" + sz));
    }

    public void rotateYaw(Player p, boolean clockwise) {
        Clipboard clip = buffers.get(p.getUniqueId());
        if (clip == null) return;
        int sx = clip.getSizeX();
        int sy = clip.getSizeY();
        int sz = clip.getSizeZ();
        Clipboard out = new Clipboard(sz, sy, sx); // X'<-Z, Z'<-X
        int ax = clip.getAnchorX();
        int ay = clip.getAnchorY();
        int az = clip.getAnchorZ();

        // Compute new anchor position so that rotation is around pivot correctly
        int newAx, newAz;
        if (clockwise) {
            newAx = az;
            newAz = (sx - 1) - ax;
        } else {
            newAx = (sz - 1) - az;
            newAz = ax;
        }

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int dx = x - ax;
                    int dz = z - az;
                    int rx = clockwise ? dz : -dz;
                    int rz = clockwise ? -dx : dx;
                    int nx = newAx + rx;
                    int nz = newAz + rz;
                    if (nx < 0 || nx >= sz || nz < 0 || nz >= sx) continue;
                    BlockData data = rotateBlockDataYaw(clip.get(x, y, z), clockwise);
                    out.set(nx, y, nz, data);
                }
            }
        }
        out.setPasteAir(clip.isPasteAir());
        out.setAnchor(newAx, ay, newAz);
        buffers.put(p.getUniqueId(), out);
        com.neobuildbattle.core.util.Sounds.playUiClick(p);
    }

    public void mirrorHorizontal(Player p) {
        Clipboard clip = buffers.get(p.getUniqueId());
        if (clip == null) return;
        int sx = clip.getSizeX();
        int sy = clip.getSizeY();
        int sz = clip.getSizeZ();
        Clipboard out = new Clipboard(sx, sy, sz);
        int ax = clip.getAnchorX();
        int ay = clip.getAnchorY();
        int az = clip.getAnchorZ();
        int newAx = (sx - 1) - ax;
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int mx = (sx - 1) - x; // reverse X within bounds
                    BlockData data = mirrorBlockDataX(clip.get(x, y, z));
                    out.set(mx, y, z, data);
                }
            }
        }
        out.setPasteAir(clip.isPasteAir());
        out.setAnchor(newAx, ay, az);
        buffers.put(p.getUniqueId(), out);
        com.neobuildbattle.core.util.Sounds.playUiClick(p);
    }

    public void mirrorVertical(Player p) {
        Clipboard clip = buffers.get(p.getUniqueId());
        if (clip == null) return;
        int sx = clip.getSizeX();
        int sy = clip.getSizeY();
        int sz = clip.getSizeZ();
        Clipboard out = new Clipboard(sx, sy, sz);
        int ax = clip.getAnchorX();
        int ay = clip.getAnchorY();
        int az = clip.getAnchorZ();
        int newAy = (sy - 1) - ay;
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int my = (sy - 1) - y;
                    BlockData data = mirrorBlockDataY(clip.get(x, y, z));
                    out.set(x, my, z, data);
                }
            }
        }
        out.setPasteAir(clip.isPasteAir());
        out.setAnchor(ax, newAy, az);
        buffers.put(p.getUniqueId(), out);
        com.neobuildbattle.core.util.Sounds.playUiClick(p);
    }

    public void setPasteAir(Player p, boolean pasteAir) {
        Clipboard clip = buffers.get(p.getUniqueId());
        if (clip != null) {
            clip.setPasteAir(pasteAir);
            com.neobuildbattle.core.util.Sounds.playUiClick(p);
        }
    }

    public boolean isPasteAir(Player p) {
        Clipboard clip = buffers.get(p.getUniqueId());
        return clip != null && clip.isPasteAir();
    }

    public void pasteAtFeet(Player p) {
        Clipboard clip = buffers.get(p.getUniqueId());
        if (clip == null) return;
        Plot plot = plotManager.getPlotByOwner(p.getUniqueId());
        if (plot == null) return;
        World w = p.getWorld();
        Location base = p.getLocation();
        // place so that clipboard anchor aligns to feet
        int baseX = base.getBlockX() - clip.getAnchorX();
        int baseY = base.getBlockY() - clip.getAnchorY();
        int baseZ = base.getBlockZ() - clip.getAnchorZ();
        int innerMinX = plot.getMinX() + 3;
        int innerMinZ = plot.getMinZ() + 3;
        int innerMaxX = plot.getMaxX() - 3;
        int innerMaxZ = plot.getMaxZ() - 3;
        int maxBuild = plugin.getConfig().getInt("max-build-height", 40);
        int minY = plot.getSpawnLocation().getBlockY() - 1;
        int maxY = minY + maxBuild;
        for (int y = 0; y < clip.getSizeY(); y++) {
            for (int z = 0; z < clip.getSizeZ(); z++) {
                for (int x = 0; x < clip.getSizeX(); x++) {
                    int wx = baseX + x;
                    int wy = baseY + y;
                    int wz = baseZ + z;
                    if (wx < innerMinX || wx > innerMaxX || wz < innerMinZ || wz > innerMaxZ) continue;
                    if (wy < minY || wy > maxY) continue;
                    BlockData data = clip.get(x, y, z);
                    if (data == null) continue;
                    if (!clip.isPasteAir() && data.getMaterial() == Material.AIR) continue;
                    w.getBlockAt(wx, wy, wz).setBlockData(data, false);
                }
            }
        }
    }

    // ---------- BlockData transforms ----------
    private BlockData rotateBlockDataYaw(BlockData data, boolean clockwise) {
        if (data == null) return null;
        try {
            if (data instanceof org.bukkit.block.data.Directional dir) {
                org.bukkit.block.BlockFace f = dir.getFacing();
                org.bukkit.block.BlockFace rf = rotateFaceYaw(f, clockwise);
                if (rf != f) { dir.setFacing(rf); return dir; }
            }
            if (data instanceof org.bukkit.block.data.Orientable ori) {
                java.util.Set<org.bukkit.Axis> axes = ori.getAxes();
                if (axes.contains(org.bukkit.Axis.X) && axes.contains(org.bukkit.Axis.Z)) {
                    // both allowed; prefer swapping primary axis
                    // no direct setter for dual axes; leave as is
                } else if (axes.contains(org.bukkit.Axis.X)) {
                    ori.setAxis(org.bukkit.Axis.Z); return ori;
                } else if (axes.contains(org.bukkit.Axis.Z)) {
                    ori.setAxis(org.bukkit.Axis.X); return ori;
                }
            }
            if (data instanceof org.bukkit.block.data.Rotatable rot) {
                org.bukkit.block.BlockFace f = rot.getRotation();
                if (f != null) { rot.setRotation(rotateFaceYaw(f, clockwise)); return rot; }
            }
        } catch (Throwable ignored) {}
        return data;
    }

    private BlockData mirrorBlockDataX(BlockData data) {
        if (data == null) return null;
        try {
            if (data instanceof org.bukkit.block.data.Directional dir) {
                org.bukkit.block.BlockFace f = dir.getFacing();
                if (f == org.bukkit.block.BlockFace.EAST) dir.setFacing(org.bukkit.block.BlockFace.WEST);
                else if (f == org.bukkit.block.BlockFace.WEST) dir.setFacing(org.bukkit.block.BlockFace.EAST);
                return dir;
            }
            if (data instanceof org.bukkit.block.data.Rotatable rot) {
                org.bukkit.block.BlockFace f = rot.getRotation();
                if (f != null) {
                    switch (f) {
                        case EAST -> rot.setRotation(org.bukkit.block.BlockFace.WEST);
                        case WEST -> rot.setRotation(org.bukkit.block.BlockFace.EAST);
                        default -> {}
                    }
                }
                return rot;
            }
        } catch (Throwable ignored) {}
        return data;
    }

    private BlockData mirrorBlockDataY(BlockData data) {
        if (data == null) return null;
        try {
            if (data instanceof org.bukkit.block.data.Directional dir) {
                org.bukkit.block.BlockFace f = dir.getFacing();
                if (f == org.bukkit.block.BlockFace.UP) dir.setFacing(org.bukkit.block.BlockFace.DOWN);
                else if (f == org.bukkit.block.BlockFace.DOWN) dir.setFacing(org.bukkit.block.BlockFace.UP);
                return dir;
            }
        } catch (Throwable ignored) {}
        return data;
    }

    private org.bukkit.block.BlockFace rotateFaceYaw(org.bukkit.block.BlockFace face, boolean clockwise) {
        switch (face) {
            case NORTH: return clockwise ? org.bukkit.block.BlockFace.EAST : org.bukkit.block.BlockFace.WEST;
            case EAST:  return clockwise ? org.bukkit.block.BlockFace.SOUTH : org.bukkit.block.BlockFace.NORTH;
            case SOUTH: return clockwise ? org.bukkit.block.BlockFace.WEST : org.bukkit.block.BlockFace.EAST;
            case WEST:  return clockwise ? org.bukkit.block.BlockFace.NORTH : org.bukkit.block.BlockFace.SOUTH;
            default:    return face;
        }
    }
}


