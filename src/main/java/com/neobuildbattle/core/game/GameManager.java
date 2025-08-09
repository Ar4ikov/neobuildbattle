package com.neobuildbattle.core.game;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.lobby.LobbyManager;
import com.neobuildbattle.core.player.PlayerRegistry;
import com.neobuildbattle.core.plot.Plot;
import com.neobuildbattle.core.plot.PlotManager;
import com.neobuildbattle.core.voting.ThemeVotingManager;
import com.neobuildbattle.core.voting.VotingScoreboard;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class GameManager implements Listener {

    private final NeoBuildBattleCore plugin;
    private final LobbyManager lobbyManager;
    private final PlayerRegistry playerRegistry;
    private final PlotManager plotManager;
    private final ThemeVotingManager themeVotingManager;
    // ScoreListener is provided via plugin singleton; no local instance needed
    private final VotingScoreboard votingScoreboard = new VotingScoreboard();

    private volatile GameState state = GameState.WAITING;
    // Tracks the plot currently being оценка (used for movement clamp in EVALUATION)
    private volatile Plot currentEvaluationPlot = null;
    private int countdownTask = -1;
    private int buildTimerTask = -1;
    private int themeVoteTask = -1;

    public GameManager(NeoBuildBattleCore plugin,
                       LobbyManager lobbyManager,
                       PlayerRegistry playerRegistry,
                       PlotManager plotManager,
                       ThemeVotingManager themeVotingManager) {
        this.plugin = plugin;
        this.lobbyManager = lobbyManager;
        this.playerRegistry = playerRegistry;
        this.plotManager = plotManager;
        this.themeVotingManager = themeVotingManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (state == GameState.WAITING || state == GameState.COUNTDOWN) {
            playerRegistry.add(p.getUniqueId());
            lobbyManager.sendToLobby(p);
            tryStartCountdown();
        } else {
            // Game running: make spectator
            NeoBuildBattleCore.getInstance().getSpectatorManager().makeSpectator(p);
            // Ensure not counted as active participant mid-game
            playerRegistry.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        playerRegistry.remove(e.getPlayer().getUniqueId());
        // If someone leaves during countdown and we no longer meet requirements, cancel countdown
        if (state == GameState.COUNTDOWN && !hasEnoughPlayers()) {
            cancelCountdown(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (state != GameState.BUILDING) {
            e.setCancelled(true);
            return;
        }
        Plot plot = plotManager.getPlotByOwner(e.getPlayer().getUniqueId());
        if (plot == null || !plot.containsLocation(e.getBlockPlaced().getLocation())) {
            e.setCancelled(true);
            return;
        }
        int maxBuild = plugin.getConfig().getInt("max-build-height", 40);
        if (!plot.withinMaxHeight(e.getBlockPlaced().getLocation().getBlockY(), plot.getSpawnLocation().getBlockY() - 1, maxBuild)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (state != GameState.BUILDING) {
            e.setCancelled(true);
            return;
        }
        Plot plot = plotManager.getPlotByOwner(e.getPlayer().getUniqueId());
        if (plot == null || !plot.containsLocation(e.getBlock().getLocation())) {
            e.setCancelled(true);
            return;
        }
        int maxBuild = plugin.getConfig().getInt("max-build-height", 40);
        if (!plot.withinMaxHeight(e.getBlock().getY(), plot.getSpawnLocation().getBlockY() - 1, maxBuild)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        // Prevent escaping current plot during BUILDING and EVALUATION
        if (state != GameState.BUILDING && state != GameState.EVALUATION) return;
        Player player = e.getPlayer();
        Plot plot = (state == GameState.EVALUATION) ? currentEvaluationPlot : plotManager.getPlotByOwner(player.getUniqueId());
        if (plot == null) return;
        // Horizontal bounds based on inner build area plus ring
        int innerMinX = plot.getMinX() + 2; // allow movement up to inner ring outer edge
        int innerMaxX = plot.getMaxX() - 2;
        int innerMinZ = plot.getMinZ() + 2;
        int innerMaxZ = plot.getMaxZ() - 2;
        double nx = e.getTo().getX();
        double nz = e.getTo().getZ();
        double clampedX = Math.max(innerMinX + 0.1, Math.min(innerMaxX + 0.9, nx));
        double clampedZ = Math.max(innerMinZ + 0.1, Math.min(innerMaxZ + 0.9, nz));
        // Height bounds
        int baseY = plot.getSpawnLocation().getBlockY() - 1;
        int maxBuild = plugin.getConfig().getInt("max-build-height", 40);
        int wallHeight = Math.max(1, plugin.getConfig().getInt("plot-wall-height", 50));
        int allowedMaxY = baseY + Math.min(maxBuild + 2, wallHeight);
        double ny = Math.max(baseY, Math.min(allowedMaxY, e.getTo().getY()));
        if (clampedX != nx || clampedZ != nz || ny != e.getTo().getY()) {
            e.setTo(new org.bukkit.Location(e.getTo().getWorld(), clampedX, ny, clampedZ, e.getTo().getYaw(), e.getTo().getPitch()));
        }
    }

    public void tryStartCountdown() {
        if (state != GameState.WAITING) return;
        if (hasEnoughPlayers()) {
            startCountdown();
            plugin.getMessages().broadcast("countdown_start", plugin.getMessages().map(
                    "seconds", plugin.getConfig().getInt("countdown-seconds", 20)
            ));
        }
    }

    private void startCountdown() {
        if (state != GameState.WAITING) return;
        state = GameState.COUNTDOWN;
        int seconds = plugin.getConfig().getInt("countdown-seconds", 20);
        AtomicInteger time = new AtomicInteger(seconds);
        countdownTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            // Abort if players dropped below minimum during countdown
            if (!hasEnoughPlayers()) {
                cancelCountdown(true);
                return;
            }
            int t = time.getAndDecrement();
            if (t <= 0) {
                Bukkit.getScheduler().cancelTask(countdownTask);
                countdownTask = -1;
                // Final sanity check before starting
                if (hasEnoughPlayers()) {
                    com.neobuildbattle.core.util.Sounds.playPhaseCompleteToAll();
                    runThemeVoting();
                } else {
                    cancelCountdown(true);
                }
            } else {
                plugin.getMessages().broadcast("countdown_tick", plugin.getMessages().map("seconds", t));
                com.neobuildbattle.core.util.Sounds.playCountdownTickToAll(t);
            }
        }, 0L, 20L);
    }

    private boolean hasEnoughPlayers() {
        int active = playerRegistry.getActivePlayers().size();
        int max = plugin.getConfig().getInt("max-players", 16);
        int configuredMin = plugin.getConfig().getInt("min-players", -1);
        int threshold = configuredMin > 0 ? configuredMin : (int) Math.ceil(max * 0.5);
        return active >= threshold;
    }

    private void cancelCountdown(boolean announce) {
        if (countdownTask != -1) {
            Bukkit.getScheduler().cancelTask(countdownTask);
            countdownTask = -1;
        }
        state = GameState.WAITING;
        if (announce) {
            plugin.getMessages().broadcast("countdown_cancelled", null);
        }
    }

    private void runThemeVoting() {
        if (state == GameState.ENDING) return;
        state = GameState.THEME_VOTING;
        themeVotingManager.openVotingForAll();
        plugin.getMessages().broadcast("theme_voting_start", null);
        // Allow flight during voting for convenience
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.ADVENTURE);
            p.setAllowFlight(true);
            p.setFlying(true);
        }
        int voteSeconds = plugin.getConfig().getInt("theme-vote-seconds", 10);
        AtomicInteger left = new AtomicInteger(voteSeconds);
        if (themeVoteTask != -1) {
            Bukkit.getScheduler().cancelTask(themeVoteTask);
        }
        themeVoteTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            int t = left.getAndDecrement();
            if (t <= 0) {
                Bukkit.getScheduler().cancelTask(themeVoteTask);
                themeVoteTask = -1;
                themeVotingManager.endVoting();
                themeVotingManager.getWinningTheme().ifPresent(theme -> plugin.getMessages().broadcast("theme_chosen",
                        plugin.getMessages().map("theme", theme)));
                com.neobuildbattle.core.util.Sounds.playPhaseCompleteToAll();
                startBuildingPhase();
                return;
            }
            if (t <= 5) {
                plugin.getMessages().broadcast("theme_voting_countdown", plugin.getMessages().map("seconds", t));
                com.neobuildbattle.core.util.Sounds.playCountdownTickToAll(t);
            }
        }, 0L, 20L);
    }

    private void startBuildingPhase() {
        if (state != GameState.THEME_VOTING) return;
        state = GameState.BUILDING;

        // Use only registered participants (those who were in WAITING/COUNTDOWN), exclude spectators who joined mid-game
        List<Player> participants = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (playerRegistry.getActivePlayers().contains(p.getUniqueId())) {
                participants.add(p);
            }
        }
        plotManager.allocatePlots(participants);
        for (Player p : participants) {
            Plot plot = plotManager.getPlotByOwner(p.getUniqueId());
            if (plot != null) {
                p.getInventory().clear();
                p.setGameMode(GameMode.CREATIVE);
                p.setAllowFlight(true);
                p.teleport(plot.getSpawnLocation());
                // Give build tools item
                NeoBuildBattleCore.getInstance().getBuildToolsManager().giveBuildTool(p);
            }
        }

        int buildSeconds = plugin.getConfig().getInt("build-seconds", 420);
        plugin.getMessages().broadcast("building_start", plugin.getMessages().map("minutes", buildSeconds / 60));
        com.neobuildbattle.core.util.Sounds.playPhaseCompleteToAll();
        startBuildTimer(buildSeconds);
    }

    private void startBuildTimer(int totalSeconds) {
        AtomicInteger left = new AtomicInteger(totalSeconds);
        if (buildTimerTask != -1) Bukkit.getScheduler().cancelTask(buildTimerTask);
        final boolean[] tickAlt = {false};
        buildTimerTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (state != GameState.BUILDING) {
                Bukkit.getScheduler().cancelTask(buildTimerTask);
                buildTimerTask = -1;
                return;
            }
            int t = left.getAndDecrement();
            if (t <= 0) {
                Bukkit.getScheduler().cancelTask(buildTimerTask);
                buildTimerTask = -1;
                com.neobuildbattle.core.util.Sounds.playPhaseCompleteToAll();
                startEvaluationPhase();
                return;
            }
            // Minute reminders
            if (t % 60 == 0) {
                plugin.getMessages().broadcast("building_minute", plugin.getMessages().map("minutes", t / 60));
                com.neobuildbattle.core.util.Sounds.playBuildTickToAll(false);
                return;
            }
            // 30s and 15s warnings
            if (t == 30) {
                plugin.getMessages().broadcast("building_30s", null);
                com.neobuildbattle.core.util.Sounds.playBuildTickToAll(false);
                return;
            }
            if (t == 15) {
                plugin.getMessages().broadcast("building_15s", null);
                com.neobuildbattle.core.util.Sounds.playBuildTickToAll(false);
                return;
            }
            // Last 10 seconds every second
            if (t <= 10) {
                plugin.getMessages().broadcast("building_seconds", plugin.getMessages().map("seconds", t));
                // Alternate between bass drum and snare for tick-tock feel
                tickAlt[0] = !tickAlt[0];
                com.neobuildbattle.core.util.Sounds.playBuildTickToAll(tickAlt[0]);
            }
        }, 20L, 20L);
    }

    private void startEvaluationPhase() {
        if (state != GameState.BUILDING) return;
        state = GameState.EVALUATION;
        plugin.getMessages().broadcast("evaluation_start", null);
        com.neobuildbattle.core.util.Sounds.playPhaseCompleteToAll();

        // Reset scores storage
        votingScoreboard.clear();

        List<UUID> owners = new ArrayList<>(plotManager.getAllOwners());
        // Ensure all registered participants that have plots are included
        for (UUID id : playerRegistry.getActivePlayers()) {
            Plot p = plotManager.getPlotByOwner(id);
            if (p != null && !owners.contains(id)) owners.add(id);
        }
        Collections.shuffle(owners);
        new EvaluationRunner(owners).run();
    }

    private final class EvaluationRunner {
        private final List<UUID> order;
        private int index = 0;

        private EvaluationRunner(List<UUID> order) {
            this.order = order;
        }

        private void run() {
            if (order.isEmpty()) {
                finishGame();
                return;
            }
            next();
        }

        private void next() {
            if (index >= order.size()) {
                finishGame();
                return;
            }
            UUID ownerId = order.get(index++);
            Plot plot = plotManager.getPlotByOwner(ownerId);
            if (plot == null) {
                next();
                return;
            }
            currentEvaluationPlot = plot;

            String ownerName = Bukkit.getOfflinePlayer(ownerId).getName();
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setGameMode(GameMode.ADVENTURE);
                player.setAllowFlight(true);
                player.getInventory().clear();
                boolean isParticipant = playerRegistry.getActivePlayers().contains(player.getUniqueId());
                if (isParticipant && !player.getUniqueId().equals(ownerId)) {
                    plot.giveScoreItems(player);
                }
                player.teleport(plot.getViewLocation());
                net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.Component.text(ownerName == null ? "Игрок" : ownerName),
                        net.kyori.adventure.text.Component.text("Оцените постройку"));
                player.showTitle(title);
            }

            int per = plugin.getConfig().getInt("evaluation-seconds-per-plot", 20);
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Tally votes for this plot
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        if (viewer.getUniqueId().equals(ownerId)) continue;
                        if (!playerRegistry.getActivePlayers().contains(viewer.getUniqueId())) continue; // only participants vote
                        int score = NeoBuildBattleCore.getInstance().getScoreListener().consumeScore(viewer.getUniqueId());
                        if (score > 0) {
                            votingScoreboard.addScore(ownerId, score);
                        }
                    }
                    next();
                }
            }.runTaskLater(plugin, per * 20L);
        }
    }

    private void finishGame() {
        state = GameState.ENDING;
        currentEvaluationPlot = null;
        // Determine winner
        UUID winner = votingScoreboard.getWinner();
        String winnerName = winner != null ? Bukkit.getOfflinePlayer(winner).getName() : null;
        broadcastWinnersFancy(winner);
        com.neobuildbattle.core.util.Sounds.playPhaseCompleteToAll();
        // Teleport everyone to winner's plot and start fireworks + leaderboard
        Plot winnerPlot = winner != null ? plotManager.getPlotByOwner(winner) : null;
        if (winnerPlot != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.teleport(winnerPlot.getViewLocation());
            }
        }
        // Personal results to each player
        for (Player p : Bukkit.getOnlinePlayers()) {
            int place = votingScoreboard.getPlace(p.getUniqueId());
            int score = votingScoreboard.getScore(p.getUniqueId());
            p.sendMessage(plugin.getMessages().format("winners_you_place", plugin.getMessages().map("your_place", place)).append(net.kyori.adventure.text.Component.empty()));
            p.sendMessage(plugin.getMessages().format("winners_you_score", plugin.getMessages().map("your_score", score)).append(net.kyori.adventure.text.Component.empty()));
        }

        // Fireworks task during ending phase (every ~0.7s)
        if (winnerPlot != null) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (state != GameState.ENDING || ticks >= 200) {
                        cancel();
                        return;
                    }
                    org.bukkit.Location center = winnerPlot.getViewLocation();
                    double radius = 7.0;
                    double angle = Math.random() * Math.PI * 2;
                    double dx = Math.cos(angle) * radius;
                    double dz = Math.sin(angle) * radius;
                    org.bukkit.Location loc = center.clone().add(dx, 0.0, dz);
                    org.bukkit.entity.Firework fw = (org.bukkit.entity.Firework) loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.FIREWORK_ROCKET);
                    org.bukkit.inventory.meta.FireworkMeta meta = fw.getFireworkMeta();
                    meta.addEffect(org.bukkit.FireworkEffect.builder()
                            .withColor(org.bukkit.Color.AQUA, org.bukkit.Color.LIME, org.bukkit.Color.WHITE)
                            .withFade(org.bukkit.Color.GREEN)
                            .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                            .trail(true)
                            .flicker(true)
                            .build());
                    meta.setPower(1);
                    fw.setFireworkMeta(meta);
                    ticks += 14;
                }
            }.runTaskTimer(plugin, 0L, 14L);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                // Remove voting items before clearing
                NeoBuildBattleCore.getInstance().getScoreListener().clearVotingItems(p);
                p.getInventory().clear();
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(false);
                p.getActivePotionEffects().forEach(pe -> p.removePotionEffect(pe.getType()));
                lobbyManager.sendToLobby(p);
            }
            // Reset all build-tools related state and overrides
            NeoBuildBattleCore.getInstance().getBuildToolsManager().resetAll();
            // Reveal all hidden players and reset spectator tracking
            NeoBuildBattleCore.getInstance().getSpectatorManager().showAll();
            plotManager.resetArenaAsync();
            state = GameState.WAITING;
            // auto-try next round
            Bukkit.getScheduler().runTaskLater(plugin, this::tryStartCountdown, 40L);
        }, 200L);
    }

    private void broadcastWinnersFancy(UUID winnerId) {
        var top = votingScoreboard.getTop(3);
        Bukkit.broadcast(plugin.getMessages().format("winners_header", null));
        if (winnerId != null) {
            String wName = Bukkit.getOfflinePlayer(winnerId).getName();
            int wScore = votingScoreboard.getScore(winnerId);
            Bukkit.broadcast(plugin.getMessages().format("winners_title", plugin.getMessages().map("winner_name", String.valueOf(wName), "winner_score", wScore)));
        } else {
            Bukkit.broadcast(plugin.getMessages().format("winners_title", plugin.getMessages().map("winner_name", "никто", "winner_score", 0)));
        }
        int place = 1;
        for (var e : top) {
            String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            Bukkit.broadcast(plugin.getMessages().format("winners_place_line", plugin.getMessages().map(
                    "place", place,
                    "name", String.valueOf(name),
                    "score", e.getValue()
            )));
            place++;
        }
        Bukkit.broadcast(plugin.getMessages().format("winners_footer", null));
    }

    public void shutdown() {
        if (countdownTask != -1) {
            Bukkit.getScheduler().cancelTask(countdownTask);
            countdownTask = -1;
        }
    }

    public GameState getState() {
        return state;
    }
}


