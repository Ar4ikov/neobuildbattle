package com.neobuildbattle.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class Messages {
    private final Plugin plugin;
    private YamlConfiguration config;
    private String prefix;

    public Messages(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        java.io.File f = new java.io.File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(f);
        this.prefix = config.getString("prefix", "");
    }

    private String raw(String key) {
        return config.getString(key, "");
    }

    private String colorize(String s) {
        return s.replace('&', '§');
    }

    public Component format(String key, Map<String, String> vars) {
        String base = raw(key);
        if (base == null) base = "";
        String withPrefix = base.replace("{prefix}", prefix);
        String result = withPrefix;
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                result = result.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return LegacyComponentSerializer.legacySection().deserialize(colorize(result));
    }

    public void broadcast(String key, Map<String, String> vars) {
        Component c = format(key, vars);
        Bukkit.getServer().sendMessage(c);
    }

    public void to(Player player, String key, Map<String, String> vars) {
        player.sendMessage(format(key, vars));
    }

    public Map<String, String> map(Object... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), String.valueOf(kv[i + 1]));
        }
        return m;
    }
}


