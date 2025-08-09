package com.neobuildbattle.core;

import com.neobuildbattle.core.game.GameManager;
import com.neobuildbattle.core.build.BuildToolsManager;
import com.neobuildbattle.core.lobby.LobbyManager;
import com.neobuildbattle.core.player.PlayerRegistry;
import com.neobuildbattle.core.plot.PlotManager;
import com.neobuildbattle.core.commands.AdminCommands;
import com.neobuildbattle.core.commands.SetupCommands;
import com.neobuildbattle.core.spectator.SpectatorManager;
import com.neobuildbattle.core.voting.ThemeVotingManager;
import com.neobuildbattle.core.voting.ScoreListener;
import com.neobuildbattle.core.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

public final class NeoBuildBattleCore extends JavaPlugin {

    private static NeoBuildBattleCore instance;

    private LobbyManager lobbyManager;
    private PlayerRegistry playerRegistry;
    private PlotManager plotManager;
    private ThemeVotingManager themeVotingManager;
    private GameManager gameManager;
    private ScoreListener scoreListener;
    private SpectatorManager spectatorManager;
    private Messages messages;
    private BuildToolsManager buildToolsManager;

    public static NeoBuildBattleCore getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("themes.txt", false);
        saveResource("schematics/lobby.schematic", false);
        saveResource("schematics/plot.schematic", false);

        // Load config into memory
        getConfig();

        this.lobbyManager = new LobbyManager(this);
        this.playerRegistry = new PlayerRegistry();
        this.plotManager = new PlotManager(this);
        this.themeVotingManager = new ThemeVotingManager(this);
        this.gameManager = new GameManager(this, lobbyManager, playerRegistry, plotManager, themeVotingManager);
        this.scoreListener = new ScoreListener();
        this.spectatorManager = new SpectatorManager(this);
        this.messages = new Messages(this);
        this.buildToolsManager = new BuildToolsManager(this);

        Bukkit.getPluginManager().registerEvents(gameManager, this);
        Bukkit.getPluginManager().registerEvents(themeVotingManager, this);
        Bukkit.getPluginManager().registerEvents(scoreListener, this);
        Bukkit.getPluginManager().registerEvents(spectatorManager, this);
        Bukkit.getPluginManager().registerEvents(new com.neobuildbattle.core.game.GameJoinQuitListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.neobuildbattle.core.protect.ProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.neobuildbattle.core.world.WorldLifecycleListener(this), this);
        Bukkit.getPluginManager().registerEvents(buildToolsManager, this);

        // Commands
        getCommand("neobb").setExecutor(new AdminCommands(this));
        getCommand("neobbsetup").setExecutor(new SetupCommands(this));

        // Place lobby platform if not present
        lobbyManager.placeLobbyPlatform();

        // Warn if worlds are not void-generated (documenting expected setup)
        ensureVoidWorld(getConfig().getString("worlds.lobby", "world"));
        ensureVoidWorld(getConfig().getString("worlds.game", "world"));

        getLogger().info("NeoBuildBattleCore enabled");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        getLogger().info("NeoBuildBattleCore disabled");
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public PlayerRegistry getPlayerRegistry() {
        return playerRegistry;
    }

    public PlotManager getPlotManager() {
        return plotManager;
    }

    public ThemeVotingManager getThemeVotingManager() {
        return themeVotingManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public ScoreListener getScoreListener() {
        return scoreListener;
    }

    public SpectatorManager getSpectatorManager() {
        return spectatorManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public BuildToolsManager getBuildToolsManager() {
        return buildToolsManager;
    }

    @Override
    public void saveDefaultConfig() {
        super.saveDefaultConfig();
        // Also ensure gradients.yml exists on disk for editing
        try {
            saveResource("gradients.yml", false);
        } catch (IllegalArgumentException ignored) {
            // resource may already exist or not packaged; ignore
        }
    }

    private void ensureVoidWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) return;
        if (Bukkit.getWorld(worldName) != null) return;
        // Try to load or create the world AFTER startup, relying on bukkit.yml generator mapping
        Bukkit.getScheduler().runTask(this, () -> {
            if (Bukkit.getWorld(worldName) != null) return;
            try {
                getLogger().info("Loading/creating world '" + worldName + "' using bukkit.yml generator mapping...");
                WorldCreator creator = new WorldCreator(worldName);
                Bukkit.createWorld(creator);
            } catch (Throwable t) {
                getLogger().warning("Failed to load/create world '" + worldName + "': " + t.getMessage() +
                        ". Ensure server/bukkit.yml has: worlds." + worldName + ".generator: VoidWorldGenerator (or NeoBuildBattleCore) and try again.");
            }
        });
    }

    @Override
    public org.bukkit.generator.ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        // Expose our void generator under plugin name for bukkit.yml mapping
        return new com.neobuildbattle.core.world.VoidChunkGenerator(this);
    }
}


