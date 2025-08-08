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
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        playerRegistry.remove(e.getPlayer().getUniqueId());
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
        int max = plugin.getConfig().getInt("max-players", 16);
        int threshold = (int) Math.ceil(max * 0.5);
        if (Bukkit.getOnlinePlayers().size() >= threshold) {
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
            int t = time.getAndDecrement();
            if (t <= 0) {
                Bukkit.getScheduler().cancelTask(countdownTask);
                runThemeVoting();
            } else {
                plugin.getMessages().broadcast("countdown_tick", plugin.getMessages().map("seconds", t));
            }
        }, 0L, 20L);
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            themeVotingManager.endVoting();
            themeVotingManager.getWinningTheme().ifPresent(theme -> plugin.getMessages().broadcast("theme_chosen",
                    plugin.getMessages().map("theme", theme)));
            startBuildingPhase();
        }, voteSeconds * 20L);
    }

    private void startBuildingPhase() {
        if (state != GameState.THEME_VOTING) return;
        state = GameState.BUILDING;

        List<Player> participants = new ArrayList<>(Bukkit.getOnlinePlayers());
        plotManager.allocatePlots(participants);
        for (Player p : participants) {
            Plot plot = plotManager.getPlotByOwner(p.getUniqueId());
            if (plot != null) {
                p.getInventory().clear();
                p.setGameMode(GameMode.CREATIVE);
                p.setAllowFlight(true);
                p.teleport(plot.getSpawnLocation());
            }
        }

        int buildSeconds = plugin.getConfig().getInt("build-seconds", 420);
        plugin.getMessages().broadcast("building_start", plugin.getMessages().map("minutes", buildSeconds / 60));
        Bukkit.getScheduler().runTaskLater(plugin, this::startEvaluationPhase, buildSeconds * 20L);
    }

    private void startEvaluationPhase() {
        if (state != GameState.BUILDING) return;
        state = GameState.EVALUATION;
        plugin.getMessages().broadcast("evaluation_start", null);

        // Reset scores storage
        votingScoreboard.clear();

        List<UUID> owners = new ArrayList<>(plotManager.getAllOwners());
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
        if (winnerName != null) {
            plugin.getMessages().broadcast("winner", plugin.getMessages().map("player", winnerName));
        } else {
            plugin.getMessages().broadcast("no_winner", null);
        }
        // Teleport everyone to winner's plot and start fireworks + leaderboard
        Plot winnerPlot = winner != null ? plotManager.getPlotByOwner(winner) : null;
        if (winnerPlot != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.teleport(winnerPlot.getViewLocation());
            }
        }
        // Leaderboard: top 3 broadcast and personal place for others
        var top = votingScoreboard.getTop(3);
        if (!top.isEmpty()) {
            Bukkit.broadcast(net.kyori.adventure.text.Component.text("ТОП-3:"));
            int rank = 1;
            for (var e : top) {
                String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
                Bukkit.broadcast(net.kyori.adventure.text.Component.text(rank + ". " + (name == null ? e.getKey() : name) + " - " + e.getValue()));
                rank++;
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                UUID id = p.getUniqueId();
                boolean inTop = top.stream().anyMatch(en -> en.getKey().equals(id));
                if (!inTop) {
                    int place = votingScoreboard.getPlace(id);
                    int score = votingScoreboard.getScore(id);
                    p.sendMessage("Ваше место: " + place + ", очки: " + score);
                }
            }
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
                p.getInventory().clear();
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(false);
                p.getActivePotionEffects().forEach(pe -> p.removePotionEffect(pe.getType()));
                lobbyManager.sendToLobby(p);
            }
            // Reveal all hidden players and reset spectator tracking
            NeoBuildBattleCore.getInstance().getSpectatorManager().showAll();
            plotManager.resetArenaAsync();
            state = GameState.WAITING;
            // auto-try next round
            Bukkit.getScheduler().runTaskLater(plugin, this::tryStartCountdown, 40L);
        }, 200L);
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


