/*
 * Copyright (C) 2022 ScreamingSandals
 *
 * This file is part of Screaming BedWars.
 *
 * Screaming BedWars is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Screaming BedWars is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Screaming BedWars. If not, see <https://www.gnu.org/licenses/>.
 */

package org.screamingsandals.bedwars.bukkit;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.screamingsandals.bedwars.BedWarsPlugin;
import org.screamingsandals.bedwars.PlatformService;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.bukkit.hooks.BukkitBStatsMetrics;
import org.screamingsandals.bedwars.bukkit.hooks.PerWorldInventoryCompatibilityFix;
import org.screamingsandals.bedwars.bukkit.listener.BungeeMotdListener;
import org.screamingsandals.bedwars.bukkit.region.LegacyRegion;
import org.screamingsandals.bedwars.bukkit.utils.BukkitEntityUtils;
import org.screamingsandals.bedwars.bukkit.utils.BukkitFakeDeath;
import org.screamingsandals.bedwars.game.GameImpl;
import org.screamingsandals.bedwars.game.GameManagerImpl;
import org.screamingsandals.bedwars.lang.LangKeys;
import org.screamingsandals.bedwars.nms.accessors.ServerGamePacketListenerImplAccessor;
import org.screamingsandals.bedwars.nms.accessors.ServerboundClientCommandPacketAccessor;
import org.screamingsandals.bedwars.nms.accessors.ServerboundClientCommandPacket_i_ActionAccessor;
import org.screamingsandals.bedwars.region.BWRegion;
import org.screamingsandals.bedwars.utils.EntityUtils;
import org.screamingsandals.bedwars.utils.FakeDeath;
import org.screamingsandals.lib.block.BlockPlacement;
import org.screamingsandals.lib.block.snapshot.BlockSnapshot;
import org.screamingsandals.lib.entity.Entities;
import org.screamingsandals.lib.entity.Entity;
import org.screamingsandals.lib.event.player.PlayerBlockBreakEvent;
import org.screamingsandals.lib.event.player.PlayerBlockPlaceEvent;
import org.screamingsandals.lib.impl.bukkit.event.player.BukkitPlayerBlockBreakEvent;
import org.screamingsandals.lib.impl.bukkit.event.player.BukkitPlayerBlockPlaceEvent;
import org.screamingsandals.lib.impl.bukkit.utils.nms.ClassStorage;
import org.screamingsandals.lib.lang.Message;
import org.screamingsandals.lib.sender.CommandSender;
import org.screamingsandals.lib.spectator.Component;
import org.screamingsandals.lib.tasker.DefaultThreads;
import org.screamingsandals.lib.tasker.Tasker;
import org.screamingsandals.lib.tasker.TaskerTime;
import org.screamingsandals.lib.utils.annotations.Service;
import org.screamingsandals.lib.utils.annotations.ServiceDependencies;
import org.screamingsandals.lib.utils.reflect.Reflect;

import java.util.Objects;

@Service
@ServiceDependencies(initAnother = {
        PerWorldInventoryCompatibilityFix.class,
        BukkitBStatsMetrics.class,
        BungeeMotdListener.class
})
@Getter
public class BukkitPlatformService extends PlatformService {
    private final FakeDeath fakeDeath = new BukkitFakeDeath();
    private final EntityUtils entityUtils = new BukkitEntityUtils();

    @Override
    public void respawnPlayer(@NotNull org.screamingsandals.lib.player.Player playerWrapper, long delay) {
        var player = playerWrapper.as(Player.class);
        Tasker.runDelayed(DefaultThreads.GLOBAL_THREAD, () -> {
            try {
                player.spigot().respawn();
            } catch (Throwable t) {
                try {
                    var selectedObj = ServerboundClientCommandPacket_i_ActionAccessor.getFieldPERFORM_RESPAWN();
                    var packet = Reflect.construct(ServerboundClientCommandPacketAccessor.getConstructor0(), selectedObj);
                    var connection = ClassStorage.getPlayerConnection(player);
                    Reflect.fastInvoke(connection, ServerGamePacketListenerImplAccessor.getMethodHandleClientCommand1(), packet);
                } catch (Throwable ignored) {
                    t.printStackTrace();
                }
            }
        }, delay, TaskerTime.TICKS);
    }

    @Override
    public void reloadPlugin(@NotNull CommandSender sender) {
        sender.sendMessage(Message.of(LangKeys.SAFE_RELOAD).defaultPrefix());

        for (var game : GameManagerImpl.getInstance().getGameNames()) {
            GameManagerImpl.getInstance().getGame(game).ifPresent(GameImpl::stop);
        }

        var logger = BedWarsPlugin.getInstance().getLogger();
        var plugin = BedWarsPlugin.getInstance().getPluginDescription().as(JavaPlugin.class);

        Tasker.runRepeatedly(DefaultThreads.GLOBAL_THREAD, taskBase -> new Runnable() {
            public int timer = 60;

            @Override
            public void run() {
                boolean gameRuns = false;
                for (var game : GameManagerImpl.getInstance().getGames()) {
                    if (game.getStatus() != GameStatus.DISABLED) {
                        gameRuns = true;
                        break;
                    }
                }

                if (gameRuns && timer == 0) {
                    sender.sendMessage(Message.of(LangKeys.SAFE_RELOAD_FAILED_TO_STOP_GAME).defaultPrefix());
                }

                if (!gameRuns || timer == 0) {
                    taskBase.cancel();
                    try {
                        logger.info(String.format("Disabling %s", plugin.getDescription().getFullName()));
                        Bukkit.getPluginManager().callEvent(new PluginDisableEvent(plugin));
                        Reflect.getMethod(plugin, "setEnabled", boolean.class).invoke(false);
                    } catch (Throwable ex) {
                        logger.trace("Error occurred (in the plugin loader) while disabling " + plugin.getDescription().getFullName() + " (Is it up to date?)", ex);
                    }

                    try {
                        Bukkit.getScheduler().cancelTasks(plugin);
                    } catch (Throwable ex) {
                        logger.trace("Error occurred (in the plugin loader) while cancelling tasks for " + plugin.getDescription().getFullName() + " (Is it up to date?)", ex);
                    }

                    try {
                        Bukkit.getServicesManager().unregisterAll(plugin);
                    } catch (Throwable ex) {
                        logger.trace("Error occurred (in the plugin loader) while unregistering services for " + plugin.getDescription().getFullName() + " (Is it up to date?)", ex);
                    }

                    try {
                        HandlerList.unregisterAll(plugin);
                    } catch (Throwable ex) {
                        logger.trace("Error occurred (in the plugin loader) while unregistering events for " + plugin.getDescription().getFullName() + " (Is it up to date?)", ex);
                    }

                    try {
                        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin);
                        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin);
                    } catch (Throwable ex) {
                        logger.trace("Error occurred (in the plugin loader) while unregistering plugin channels for " + plugin.getDescription().getFullName() + " (Is it up to date?)", ex);
                    }

                    try {
                        for (var world : Bukkit.getWorlds()) {
                            world.removePluginChunkTickets(plugin);
                        }
                    } catch (Throwable ex) {
                        logger.trace("Error occurred (in the plugin loader) while removing chunk tickets for " + plugin.getDescription().getFullName() + " (Is it up to date?)", ex);
                    }
                    Bukkit.getServer().getPluginManager().enablePlugin(plugin);
                    sender.sendMessage(Component.text("Plugin reloaded! Keep in mind that restarting the server is safer!"));
                    return;
                }
                timer--;
            }
        }, 20, TaskerTime.TICKS);
    }

    // TODO: slib?
    @Override
    public void spawnEffect(@NotNull org.screamingsandals.lib.world.Location location, @NotNull String value) {
        var particle = Effect.valueOf(value.toUpperCase());
        var bukkitLoc =  location.as(Location.class);
        bukkitLoc.getWorld().playEffect(bukkitLoc, particle, 1);
    }

    // TODO: slib? (tnt source doesn't exist in Minestom for example)
    @Override
    @Nullable
    public org.screamingsandals.lib.player.Player getSourceOfTnt(@NotNull Entity tnt) {
        if (!tnt.getEntityType().is("tnt")) {
            return null;
        }
        final var tntSource = tnt.as(TNTPrimed.class).getSource();
        if (tntSource instanceof Player) {
            return Objects.requireNonNull(Entities.wrapEntity(tntSource)).as(org.screamingsandals.lib.player.Player.class);
        }
        return null;
    }

    @Override
    @NotNull
    public PlayerBlockPlaceEvent fireFakeBlockPlaceEvent(@NotNull BlockPlacement block, @NotNull BlockSnapshot originalState, @NotNull BlockPlacement clickedBlock, @NotNull org.screamingsandals.lib.item.ItemStack item, @NotNull org.screamingsandals.lib.player.Player player, boolean canBuild) {
        var event = new BlockPlaceEvent(block.as(Block.class), originalState.as(BlockState.class),
                clickedBlock.as(Block.class), item.as(ItemStack.class), player.as(Player.class), canBuild);
        Bukkit.getPluginManager().callEvent(event);

        return new BukkitPlayerBlockPlaceEvent(event);
    }

    @Override
    @NotNull
    public PlayerBlockBreakEvent fireFakeBlockBreakEvent(@NotNull BlockPlacement block, @NotNull org.screamingsandals.lib.player.Player player) {
        var event = new BlockBreakEvent(block.as(Block.class), player.as(Player.class));
        Bukkit.getPluginManager().callEvent(event);

        return new BukkitPlayerBlockBreakEvent(event);
    }

    @Override
    @NotNull
    public BWRegion getLegacyRegion() {
        return new LegacyRegion();
    }

    @Override
    public @Nullable Object savePlatformScoreboard(@NotNull org.screamingsandals.lib.player.Player player) {
        return player.as(Player.class).getScoreboard();
    }

    @Override
    public void restorePlatformScoreboard(@NotNull org.screamingsandals.lib.player.Player player, @NotNull Object scoreboard) {
        if (scoreboard instanceof Scoreboard) {
            player.as(Player.class).setScoreboard((Scoreboard) scoreboard);
        }
    }
}