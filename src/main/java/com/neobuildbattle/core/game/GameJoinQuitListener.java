package com.neobuildbattle.core.game;

import com.neobuildbattle.core.NeoBuildBattleCore;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class GameJoinQuitListener implements Listener {
    private final NeoBuildBattleCore plugin;

    public GameJoinQuitListener(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // If game is running, non-participant becomes spectator and join is not announced
        GameState s = plugin.getGameManager().getState();
        boolean gameRunning = s != GameState.WAITING && s != GameState.COUNTDOWN;
        if (gameRunning) {
            e.setJoinMessage(null);
            plugin.getSpectatorManager().makeSpectator(e.getPlayer());
            return;
        }
        int online = Bukkit.getOnlinePlayers().size();
        int max = plugin.getConfig().getInt("max-players", 16);
        plugin.getMessages().broadcast("join", plugin.getMessages().map(
                "player", e.getPlayer().getName(),
                "players_online", online,
                "max_players", max
        ));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        int online = Math.max(0, Bukkit.getOnlinePlayers().size() - 1);
        int max = plugin.getConfig().getInt("max-players", 16);
        plugin.getMessages().broadcast("quit", plugin.getMessages().map(
                "player", e.getPlayer().getName(),
                "players_online", online,
                "max_players", max
        ));
    }
}


