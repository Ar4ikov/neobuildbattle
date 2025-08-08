package com.neobuildbattle.core.commands.tabs;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AdminTab implements TabCompleter {
    private static final List<String> ROOT = Arrays.asList("forcestart", "state", "end", "debug");
    private static final List<String> STATES = Arrays.asList("WAITING", "COUNTDOWN", "THEME_VOTING", "BUILDING", "EVALUATION", "ENDING");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return match(ROOT, args[0]);
        if (args.length == 2 && "state".equalsIgnoreCase(args[0])) return match(STATES, args[1]);
        return List.of();
    }

    private List<String> match(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : options) if (s.toLowerCase().startsWith(prefix.toLowerCase())) out.add(s);
        return out;
    }
}


