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
            case 10 -> { // axe
                ItemStack axe = sel.createAxeItem();
                player.getInventory().addItem(axe);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.AQUA + "Выдан топор выделения"));
            }
            case 12 -> { // solid fill
                ItemStack it = adv.createToolItem(ToolKind.FILL_SOLID, Material.ORANGE_CONCRETE, ChatColor.GOLD + "Сплошная заливка");
                player.getInventory().addItem(it);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.GOLD + "Инструмент заливки выдан"));
            }
            case 13 -> { // hollow fill
                ItemStack it = adv.createToolItem(ToolKind.FILL_HOLLOW, Material.HONEYCOMB_BLOCK, ChatColor.YELLOW + "Заливка с полостью");
                player.getInventory().addItem(it);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.YELLOW + "Инструмент оболочки выдан"));
            }
            case 14 -> { // walls fill
                ItemStack it = adv.createToolItem(ToolKind.FILL_WALLS, Material.BRICKS, ChatColor.YELLOW + "Заливка стен");
                player.getInventory().addItem(it);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.YELLOW + "Инструмент стен выдан"));
            }
            case 49 -> { // gradient picker
                if (plugin.getGradientToolManager() != null) {
                    ItemStack it = plugin.getGradientToolManager().createPickerItem();
                    player.getInventory().addItem(it);
                    player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.BLUE + "Инструмент градиента выдан"));
                }
            }
            case 28 -> { // copy
                ItemStack it = adv.createToolItem(ToolKind.COPY, Material.PAPER, ChatColor.GREEN + "Копировать выделение");
                player.getInventory().addItem(it);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.GREEN + "Инструмент копирования выдан"));
            }
            case 29 -> { // paste
                ItemStack it = adv.createToolItem(ToolKind.PASTE, Material.BOOK, ChatColor.GREEN + "Вставить у ног");
                player.getInventory().addItem(it);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.GREEN + "Инструмент вставки выдан"));
            }
            case 31 -> { // paste air toggle (immediate toggle)
                boolean newState = !plugin.getClipboardService().isPasteAir(player);
                plugin.getClipboardService().setPasteAir(player, newState);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.AQUA + (newState ? "Вставка воздуха: ВКЛ" : "Вставка воздуха: ВЫКЛ")));
            }
            case 33 -> { // replace tool item
                ItemStack it = adv.createToolItem(ToolKind.REPLACE, Material.GOLDEN_PICKAXE, ChatColor.GOLD + "Замена блоков");
                player.getInventory().addItem(it);
                player.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.GOLD + "Инструмент замены выдан"));
            }
            case 37 -> { // rotate CW
                NeoBuildBattleCore.getInstance().getClipboardService().rotateYaw(player, true);
            }
            case 38 -> { // rotate CCW
                NeoBuildBattleCore.getInstance().getClipboardService().rotateYaw(player, false);
            }
            case 40 -> { // mirror horizontally
                NeoBuildBattleCore.getInstance().getClipboardService().mirrorHorizontal(player);
            }
            case 41 -> { // mirror vertically
                NeoBuildBattleCore.getInstance().getClipboardService().mirrorVertical(player);
            }
            case 16 -> { // open pattern editor
                Inventory pat = plugin.getPatternGui().create(player, plugin.getAdvancedToolsManager().getOrCreatePattern(player));
                player.openInventory(pat);
            }
        }
    }
}


