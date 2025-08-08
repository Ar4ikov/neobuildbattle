package com.neobuildbattle.core.commands;

import com.neobuildbattle.core.NeoBuildBattleCore;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SetupCommands implements CommandExecutor {
    private final NeoBuildBattleCore plugin;

    public SetupCommands(NeoBuildBattleCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only player");
            return true;
        }
        if (!sender.hasPermission("neobb.setup")) {
            sender.sendMessage("No permission");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/neobbsetup <setlobbyspawn|setarenaorigin|setarenaworld>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "setlobbyspawn" -> {
                Location l = player.getLocation();
                plugin.getConfig().set("lobby-spawn.world", l.getWorld().getName());
                plugin.getConfig().set("lobby-spawn.x", l.getX());
                plugin.getConfig().set("lobby-spawn.y", l.getY());
                plugin.getConfig().set("lobby-spawn.z", l.getZ());
                plugin.getConfig().set("lobby-spawn.yaw", l.getYaw());
                plugin.getConfig().set("lobby-spawn.pitch", l.getPitch());
                plugin.saveConfig();
                sender.sendMessage("Lobby spawn saved.");
            }
            case "setarenaorigin" -> {
                Location l = player.getLocation();
                plugin.getConfig().set("arena-origin.world", l.getWorld().getName());
                plugin.getConfig().set("arena-origin.x", l.getX());
                plugin.getConfig().set("arena-origin.y", l.getY());
                plugin.getConfig().set("arena-origin.z", l.getZ());
                plugin.saveConfig();
                sender.sendMessage("Arena origin saved.");
            }
            case "setarenaworld" -> {
                World w = player.getWorld();
                plugin.getConfig().set("worlds.game", w.getName());
                plugin.saveConfig();
                sender.sendMessage("Arena world saved.");
            }
            default -> sender.sendMessage("Unknown subcommand");
        }
        return true;
    }
}


