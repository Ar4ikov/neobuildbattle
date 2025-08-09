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
        // anchor on player's feet within the selection bounds if possible
        int ax = Math.max(0, Math.min(sx - 1, p.getLocation().getBlockX() - minX));
        int ay = Math.max(0, Math.min(sy - 1, p.getLocation().getBlockY() - minY));
        int az = Math.max(0, Math.min(sz - 1, p.getLocation().getBlockZ() - minZ));
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
        // rotate around Y (swap X/Z)
        int sx = clip.getSizeX();
        int sy = clip.getSizeY();
        int sz = clip.getSizeZ();
        Clipboard out = new Clipboard(sz, sy, sx);
        // rotate around anchor
        int ax = clip.getAnchorX();
        int ay = clip.getAnchorY();
        int az = clip.getAnchorZ();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int rx = x - ax;
                    int rz = z - az;
                    int nx = clockwise ? (az + rz) : (az - rz);
                    int nz = clockwise ? (ax - rx) : (ax + rx);
                    BlockData data = clip.get(x, y, z);
                    if (nx >= 0 && nx < sz && nz >= 0 && nz < sx) out.set(nx, y, nz, data);
                }
            }
        }
        out.setPasteAir(clip.isPasteAir());
        // new anchor mapping
        out.setAnchor(az, ay, ax);
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
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int rx = x - ax;
                    int mx = ax - rx; // reflect around anchor axis
                    out.set(mx, y, z, clip.get(x, y, z));
                }
            }
        }
        out.setPasteAir(clip.isPasteAir());
        out.setAnchor(ax, ay, az);
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
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    int ry = y - ay;
                    int my = ay - ry;
                    out.set(x, my, z, clip.get(x, y, z));
                }
            }
        }
        out.setPasteAir(clip.isPasteAir());
        out.setAnchor(ax, ay, az);
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
}


