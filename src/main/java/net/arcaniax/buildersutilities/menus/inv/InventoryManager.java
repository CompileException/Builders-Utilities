/*
 *      ____        _ _     _                      _    _ _   _ _ _ _   _
 *     |  _ \      (_) |   | |                    | |  | | | (_) (_) | (_)
 *     | |_) |_   _ _| | __| | ___ _ __ ___ ______| |  | | |_ _| |_| |_ _  ___  ___
 *     |  _ <| | | | | |/ _` |/ _ \ '__/ __|______| |  | | __| | | | __| |/ _ \/ __|
 *     | |_) | |_| | | | (_| |  __/ |  \__ \      | |__| | |_| | | | |_| |  __/\__ \
 *     |____/ \__,_|_|_|\__,_|\___|_|  |___/       \____/ \__|_|_|_|\__|_|\___||___/
 *
 *    Builder's Utilities is a collection of a lot of tiny features that help with building.
 *                          Copyright (C) 2020 Arcaniax
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.arcaniax.buildersutilities.menus.inv;

import net.arcaniax.buildersutilities.menus.inv.content.InventoryContents;
import net.arcaniax.buildersutilities.menus.inv.opener.ChestInventoryOpener;
import net.arcaniax.buildersutilities.menus.inv.opener.InventoryOpener;
import net.arcaniax.buildersutilities.menus.inv.opener.SpecialInventoryOpener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class InventoryManager {

    private final JavaPlugin plugin;
    private final PluginManager pluginManager;

    private final Map<Player, SmartInventory> inventories;
    private final Map<Player, InventoryContents> contents;

    private final List<InventoryOpener> defaultOpeners;
    private final List<InventoryOpener> openers;

    public InventoryManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.pluginManager = Bukkit.getPluginManager();

        this.inventories = new HashMap<>();
        this.contents = new HashMap<>();

        this.defaultOpeners = Arrays.asList(
                new ChestInventoryOpener(),
                new SpecialInventoryOpener()
        );

        this.openers = new ArrayList<>();
    }

    public void init() {
        pluginManager.registerEvents(new InvListener(), plugin);

        new InvTask().runTaskTimer(plugin, 1, 1);
    }

    public Optional<InventoryOpener> findOpener(InventoryType type) {
        Optional<InventoryOpener> opInv = this.openers.stream()
                .filter(opener -> opener.supports(type))
                .findAny();

        if (!opInv.isPresent()) {
            opInv = this.defaultOpeners.stream()
                    .filter(opener -> opener.supports(type))
                    .findAny();
        }

        return opInv;
    }

    public void registerOpeners(InventoryOpener... openers) {
        this.openers.addAll(Arrays.asList(openers));
    }

    public List<Player> getOpenedPlayers(SmartInventory inv) {
        List<Player> list = new ArrayList<>();

        this.inventories.forEach((player, playerInv) -> {
            if (inv.equals(playerInv)) {
                list.add(player);
            }
        });

        return list;
    }

    public Optional<SmartInventory> getInventory(Player p) {
        return Optional.ofNullable(this.inventories.get(p));
    }

    protected void setInventory(Player p, SmartInventory inv) {
        if (inv == null) {
            this.inventories.remove(p);
        } else {
            this.inventories.put(p, inv);
        }
    }

    public Optional<InventoryContents> getContents(Player p) {
        return Optional.ofNullable(this.contents.get(p));
    }

    protected void setContents(Player p, InventoryContents contents) {
        if (contents == null) {
            this.contents.remove(p);
        } else {
            this.contents.put(p, contents);
        }
    }

    @SuppressWarnings("unchecked")
    class InvListener implements Listener {

        @EventHandler(priority = EventPriority.LOW)
        public void onInventoryClick(InventoryClickEvent e) {
            Player p = (Player) e.getWhoClicked();

            if (!inventories.containsKey(p)) {
                return;
            }

            if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                e.setCancelled(true);
            }

            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
                return;
            }

            if (e.getAction() == InventoryAction.NOTHING && e.getClick() != ClickType.MIDDLE) {
                e.setCancelled(true);
            }

            if (e.getClickedInventory() == p.getOpenInventory().getTopInventory()) {
                e.setCancelled(true);

                int row = e.getSlot() / 9;
                int column = e.getSlot() % 9;

                if (row < 0 || column < 0) {
                    return;
                }

                SmartInventory inv = inventories.get(p);

                if (row >= inv.getRows() || column >= inv.getColumns()) {
                    return;
                }

                inv.getListeners().stream()
                        .filter(listener -> listener.getType() == InventoryClickEvent.class)
                        .forEach(listener -> ((InventoryListener<InventoryClickEvent>) listener).accept(e));

                contents.get(p).get(row, column).ifPresent(item -> item.run(e));

                p.updateInventory();
            }
        }

        @EventHandler(priority = EventPriority.LOW)
        public void onInventoryDrag(InventoryDragEvent e) {
            Player p = (Player) e.getWhoClicked();

            if (!inventories.containsKey(p)) {
                return;
            }

            SmartInventory inv = inventories.get(p);

            for (int slot : e.getRawSlots()) {
                if (slot >= p.getOpenInventory().getTopInventory().getSize()) {
                    continue;
                }

                e.setCancelled(true);
                break;
            }

            inv.getListeners().stream()
                    .filter(listener -> listener.getType() == InventoryDragEvent.class)
                    .forEach(listener -> ((InventoryListener<InventoryDragEvent>) listener).accept(e));
        }

        @EventHandler(priority = EventPriority.LOW)
        public void onInventoryOpen(InventoryOpenEvent e) {
            Player p = (Player) e.getPlayer();

            if (!inventories.containsKey(p)) {
                return;
            }

            SmartInventory inv = inventories.get(p);

            inv.getListeners().stream()
                    .filter(listener -> listener.getType() == InventoryOpenEvent.class)
                    .forEach(listener -> ((InventoryListener<InventoryOpenEvent>) listener).accept(e));
        }

        @EventHandler(priority = EventPriority.LOW)
        public void onInventoryClose(InventoryCloseEvent e) {
            Player p = (Player) e.getPlayer();

            if (!inventories.containsKey(p)) {
                return;
            }

            SmartInventory inv = inventories.get(p);

            inv.getListeners().stream()
                    .filter(listener -> listener.getType() == InventoryCloseEvent.class)
                    .forEach(listener -> ((InventoryListener<InventoryCloseEvent>) listener).accept(e));

            if (inv.isCloseable()) {
                e.getInventory().clear();

                inventories.remove(p);
                contents.remove(p);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> p.openInventory(e.getInventory()));
            }
        }

        @EventHandler(priority = EventPriority.LOW)
        public void onPlayerQuit(PlayerQuitEvent e) {
            Player p = e.getPlayer();

            if (!inventories.containsKey(p)) {
                return;
            }

            SmartInventory inv = inventories.get(p);

            inv.getListeners().stream()
                    .filter(listener -> listener.getType() == PlayerQuitEvent.class)
                    .forEach(listener -> ((InventoryListener<PlayerQuitEvent>) listener).accept(e));

            inventories.remove(p);
            contents.remove(p);
        }

        @EventHandler(priority = EventPriority.LOW)
        public void onPluginDisable(PluginDisableEvent e) {
            new HashMap<>(inventories).forEach((player, inv) -> {
                inv.getListeners().stream()
                        .filter(listener -> listener.getType() == PluginDisableEvent.class)
                        .forEach(listener -> ((InventoryListener<PluginDisableEvent>) listener).accept(e));

                inv.close(player);
            });

            inventories.clear();
            contents.clear();
        }

    }

    class InvTask extends BukkitRunnable {

        @Override
        public void run() {
            new HashMap<>(inventories)
                    .forEach((player, inv) -> inv.getProvider().update(player, contents.get(player)));
        }

    }

}
