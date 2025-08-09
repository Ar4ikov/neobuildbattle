package com.neobuildbattle.core.util;

import com.neobuildbattle.core.NeoBuildBattleCore;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class Sounds {
    private Sounds() {}

    private static FileConfiguration cfg() {
        return NeoBuildBattleCore.getInstance().getConfig();
    }

    private static boolean allEnabled() {
        return cfg().getBoolean("sounds.enabled", true);
    }

    private static Sound parseSound(String name, Sound def) {
        if (name == null) return def;
        try { return Sound.valueOf(name.trim().toUpperCase()); } catch (Throwable t) { return def; }
    }

    public static void playUiClick(Player player) {
        if (!allEnabled()) return;
        String base = "sounds.ui_click";
        Sound s = parseSound(cfg().getString(base + ".sound", "ENTITY_EXPERIENCE_ORB_PICKUP"), Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        float vol = (float) cfg().getDouble(base + ".volume", 0.6);
        float pitch = (float) cfg().getDouble(base + ".pitch", 1.2);
        safePlay(player, s, vol, pitch);
    }

    public static void playPhaseCompleteToAll() {
        if (!allEnabled()) return;
        String base = "sounds.phase_complete";
        Sound s = parseSound(cfg().getString(base + ".sound", "ENTITY_PLAYER_LEVELUP"), Sound.ENTITY_PLAYER_LEVELUP);
        float vol = (float) cfg().getDouble(base + ".volume", 0.9);
        float pitch = (float) cfg().getDouble(base + ".pitch", 1.0);
        for (Player p : Bukkit.getOnlinePlayers()) safePlay(p, s, vol, pitch);
    }

    public static void playCountdownTickToAll(int secondsLeft) {
        if (!allEnabled()) return;
        String base = "sounds.countdown_tick";
        Sound s = parseSound(cfg().getString(base + ".sound", "ENTITY_EXPERIENCE_ORB_PICKUP"), Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        float vol = (float) cfg().getDouble(base + ".volume", 0.6);
        float start = (float) cfg().getDouble(base + ".pitch_start", 0.9);
        float step = (float) cfg().getDouble(base + ".pitch_step", 0.05);
        // simple dynamic pitch: increase as it approaches 0
        float pitch = Math.max(0.5f, Math.min(2.0f, start + step * Math.max(0, 5 - Math.min(5, secondsLeft))));
        for (Player p : Bukkit.getOnlinePlayers()) safePlay(p, s, vol, pitch);
    }

    public static void playBuildTickToAll(boolean alt) {
        if (!allEnabled()) return;
        String base = "sounds.build_tick";
        boolean alternate = cfg().getBoolean(base + ".alternate", true);
        String key = alternate && alt ? ".sound_b" : ".sound_a";
        Sound s = parseSound(cfg().getString(base + key, alternate && alt ? "BLOCK_NOTE_BLOCK_SNARE" : "BLOCK_NOTE_BLOCK_BASEDRUM"),
                alternate && alt ? Sound.BLOCK_NOTE_BLOCK_SNARE : Sound.BLOCK_NOTE_BLOCK_BASEDRUM);
        float vol = (float) cfg().getDouble(base + ".volume", 0.8);
        float pitch = (float) cfg().getDouble(base + (alternate && alt ? ".pitch_b" : ".pitch_a"), alternate && alt ? 1.0f : 0.8f);
        for (Player p : Bukkit.getOnlinePlayers()) safePlay(p, s, vol, pitch);
    }

    private static void safePlay(Player p, Sound s, float vol, float pitch) {
        try { p.playSound(p.getLocation(), s, vol, pitch); } catch (Throwable ignored) {}
    }
}


