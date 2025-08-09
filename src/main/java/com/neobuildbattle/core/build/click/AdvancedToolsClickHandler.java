package com.neobuildbattle.core.build.click;

import com.neobuildbattle.core.NeoBuildBattleCore;
import com.neobuildbattle.core.build.tools.AdvancedToolsManager.ToolKind;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class AdvancedToolsClickHandler implements GuiClickHandler {

    @Override
    public void handle(Player player, int rawSlot, InventoryClickEvent event) {
        var plugin = NeoBuildBattleCore.getInstance();
        var adv = plugin.getAdvancedToolsManager();
        var sel = plugin.getSelectionService();
        if (adv == null || sel == null) return;
        switch (rawSlot) {
            case 20 -> { // axe
                ItemStack axe = sel.createAxeItem();
                player.getInventory().addItem(axe);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.AQUA + "Выдан топор выделения"));
            }
            case 21 -> { // solid fill
                ItemStack it = adv.createToolItem(ToolKind.FILL_SOLID, Material.ORANGE_CONCRETE, ChatColor.GOLD + "Сплошная заливка");
                player.getInventory().addItem(it);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.GOLD + "Инструмент заливки выдан"));
            }
            case 22 -> { // hollow fill
                ItemStack it = adv.createToolItem(ToolKind.FILL_HOLLOW, Material.HONEYCOMB_BLOCK, ChatColor.YELLOW + "Заливка с полостью");
                player.getInventory().addItem(it);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.YELLOW + "Инструмент оболочки выдан"));
            }
            case 23 -> { // walls fill
                ItemStack it = adv.createToolItem(ToolKind.FILL_WALLS, Material.BRICKS, ChatColor.YELLOW + "Заливка стен");
                player.getInventory().addItem(it);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.YELLOW + "Инструмент стен выдан"));
            }
            case 24 -> { // gradient picker
                if (plugin.getGradientToolManager() != null) {
                    ItemStack it = plugin.getGradientToolManager().createPickerItem();
                    player.getInventory().addItem(it);
                    player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.BLUE + "Инструмент градиента выдан"));
                }
            }
            case 29 -> { // copy
                ItemStack it = adv.createToolItem(ToolKind.COPY, Material.PAPER, ChatColor.GREEN + "Копировать выделение");
                player.getInventory().addItem(it);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.GREEN + "Инструмент копирования выдан"));
            }
            case 30 -> { // paste
                ItemStack it = adv.createToolItem(ToolKind.PASTE, Material.BOOK, ChatColor.GREEN + "Вставить у ног");
                player.getInventory().addItem(it);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.GREEN + "Инструмент вставки выдан"));
            }
            case 31 -> { // paste air toggle tool
                ItemStack it = adv.createToolItem(ToolKind.PASTE_AIR_TOGGLE, Material.GLASS, ChatColor.AQUA + "Переключить вставку воздуха");
                player.getInventory().addItem(it);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.AQUA + "Добавлен переключатель воздуха"));
            }
            case 32 -> { // rotate CW
                NeoBuildBattleCore.getInstance().getClipboardService().rotateYaw(player, true);
            }
            case 33 -> { // rotate CCW
                NeoBuildBattleCore.getInstance().getClipboardService().rotateYaw(player, false);
            }
            case 34 -> { // mirror horizontally
                NeoBuildBattleCore.getInstance().getClipboardService().mirrorHorizontal(player);
            }
            case 35 -> { // open pattern editor
                Inventory pat = plugin.getPatternGui().create(player, plugin.getAdvancedToolsManager().getOrCreatePattern(player));
                player.openInventory(pat);
            }
        }
    }
}


