package com.neobuildbattle.core.commands;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.game.GameManager;
import com.neobuildbattle.core.game.GameState;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AdminCommands implements CommandExecutor {
    private final NeoBuildBattleCore plugin;

    public AdminCommands(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("neobb.admin")) {
            sender.sendMessage("No permission");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/neobb <forcestart|state <name>|end|debug|reloadguis>");
            return true;
        }
        String sub = args[0].toLowerCase();
        GameManager gm = plugin.getGameManager();
        switch (sub) {
            case "forcestart" -> {
                sender.sendMessage("Forcing start");
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        var method = gm.getClass().getDeclaredMethod("runThemeVoting");
                        method.setAccessible(true);
                        method.invoke(gm);
                    } catch (Exception e) {
                        sender.sendMessage("Failed: " + e.getMessage());
                    }
                });
            }
            case "state" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /neobb state <WAITING|COUNTDOWN|THEME_VOTING|BUILDING|EVALUATION|ENDING>");
                    return true;
                }
                try {
                    GameState st = GameState.valueOf(args[1].toUpperCase());
                    sender.sendMessage("Setting state: " + st);
                    // Provide safe transitions
                    switch (st) {
                        case WAITING -> {
                            try {
                                var m = gm.getClass().getDeclaredMethod("finishGame");
                                m.setAccessible(true);
                                m.invoke(gm);
                            } catch (Exception ignored) { }
                        }
                        case COUNTDOWN -> {
                            var m = gm.getClass().getDeclaredMethod("startCountdown");
                            m.setAccessible(true);
                            m.invoke(gm);
                        }
                        case THEME_VOTING -> {
                            var m = gm.getClass().getDeclaredMethod("runThemeVoting");
                            m.setAccessible(true);
                            m.invoke(gm);
                        }
                        case BUILDING -> {
                            var m = gm.getClass().getDeclaredMethod("startBuildingPhase");
                            m.setAccessible(true);
                            m.invoke(gm);
                        }
                        case EVALUATION -> {
                            var m = gm.getClass().getDeclaredMethod("startEvaluationPhase");
                            m.setAccessible(true);
                            m.invoke(gm);
                        }
                        case ENDING -> {
                            var m = gm.getClass().getDeclaredMethod("finishGame");
                            m.setAccessible(true);
                            m.invoke(gm);
                        }
                    }
                } catch (Exception e) {
                    sender.sendMessage("Invalid state or transition failed");
                }
            }
            case "end" -> {
                try {
                    var method = gm.getClass().getDeclaredMethod("finishGame");
                    method.setAccessible(true);
                    method.invoke(gm);
                    sender.sendMessage("Game finished");
                } catch (Exception e) {
                    sender.sendMessage("Failed: " + e.getMessage());
                }
            }
            case "debug" -> sender.sendMessage(Component.text("State: ").append(Component.text(plugin.getGameManager().toString())));
            case "reloadguis" -> {
                plugin.getBuildToolsManager().reloadGradients();
                sender.sendMessage("Gradients reloaded");
                // Ensure biomes.yml exists but do not overwrite user edits
                try {
                    java.io.File f = new java.io.File(plugin.getDataFolder(), "biomes.yml");
                    if (!f.exists()) plugin.saveResource("biomes.yml", false);
                } catch (IllegalArgumentException ignored) {}
                sender.sendMessage("Biomes config checked. Reopen menus to see changes.");
            }
            default -> sender.sendMessage("Unknown subcommand");
        }
        return true;
    }
}


