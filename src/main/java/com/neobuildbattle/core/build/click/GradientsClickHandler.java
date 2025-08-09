package com.neobuildbattle.core.build.click;

import com.neobuildbattle.core.build.BuildToolsManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class GradientsClickHandler implements GuiClickHandler {
    private final BuildToolsManager mgr;
    public GradientsClickHandler(BuildToolsManager mgr) { this.mgr = mgr; }

    @Override
    public void handle(Player player, int rawSlot, InventoryClickEvent event) {
        var st = mgr.getOrCreateState(player);
        int page = st.gradientPage;
        // grab button per row
        if (rawSlot < 45 && rawSlot % 9 == 8) {
            int gIndex = page + (rawSlot / 9);
            List<List<Material>> gradients = mgr.getGradients();
            if (gIndex >= 0 && gIndex < gradients.size()) {
                List<Material> mats = gradients.get(gIndex);
                for (int i = 0; i < Math.min(8, mats.size()); i++) {
                    player.getInventory().setItem(i, new ItemStack(mats.get(i)));
                }
            }
            return;
        }
        // pagination
        if (rawSlot == 47 && page > 0) {
            st.gradientPage = page - 1;
            mgr.openGradientsGui(player);
        } else if (rawSlot == 51 && page < mgr.getGradients().size()) {
            st.gradientPage = page + 1;
            mgr.openGradientsGui(player);
        }
    }
}


