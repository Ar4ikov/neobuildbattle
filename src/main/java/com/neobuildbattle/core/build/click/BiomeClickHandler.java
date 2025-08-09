package com.neobuildbattle.core.build.click;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.build.BuildToolsManager;
import com.neobuildbattle.core.build.biome.BiomeGui;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public final class BiomeClickHandler implements GuiClickHandler {
    private final NeoBuildBattleCore plugin;
    private final BuildToolsManager mgr;

    public BiomeClickHandler(NeoBuildBattleCore plugin, BuildToolsManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    @Override
    public void handle(Player player, int rawSlot, InventoryClickEvent event) {
        List<BiomeGui.Option> options = BiomeGui.loadOptions(plugin);
        int idx = 10;
        for (BiomeGui.Option opt : options) {
            if (idx >= 44) break;
            if (rawSlot == idx) {
                Biome biome = opt.biome();
                mgr.applyBiome(player, biome);
                return;
            }
            idx += (idx % 9 == 7) ? 3 : 1;
        }
    }
}


