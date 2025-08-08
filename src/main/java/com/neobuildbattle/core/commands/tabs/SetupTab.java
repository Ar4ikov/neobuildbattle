package com.neobuildbattle.core.commands.tabs;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class SetupTab implements TabCompleter {
    private static final List<String> ROOT = Arrays.asList("setlobbyspawn", "setarenaorigin", "setarenaworld");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            return ROOT.stream().filter(s -> s.startsWith(p)).collect(Collectors.toList());
        }
        return List.of();
    }
}


