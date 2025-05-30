package net.jaxx0rr.jxmainquest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.jaxx0rr.jxmainquest.config.StoryStageLoader;
import net.jaxx0rr.jxmainquest.network.StoryNetwork;
import net.jaxx0rr.jxmainquest.story.InteractionTracker;
import net.jaxx0rr.jxmainquest.story.StoryProgressProvider;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.jaxx0rr.jxmainquest.tests.TriggerTestCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.UUID;

import static net.minecraft.commands.Commands.literal;

public class JxmqCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("jxmq")
                .requires(source -> source.hasPermission(2))

                .then(literal("reload")
                        .executes(ctx -> {
                            int count = StoryStageLoader.reloadStages();
                            for (ServerPlayer online : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                StoryNetwork.sendStageListToClient(online, StoryStageLoader.stages);
                            }

                            ServerPlayer player = ctx.getSource().getPlayerOrException();

                            if (count == 0) {
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§cReloaded 0 stages — check stages.json for errors."), false);
                            } else {
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§aReloaded " + count + " stages."), false);

                                player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                                    int stage = progress.getCurrentStage();
                                    if (stage < StoryStageLoader.stages.size()) {
                                        InteractionTracker.clearInteraction(player.getUUID(), stage);
                                        ModEventHandler.onStageStart(player, stage);
                                        StoryNetwork.sendStageToClient(player, stage);
                                        player.sendSystemMessage(Component.literal("§7Re-applied current stage: §f" + stage));
                                    } else {
                                        player.sendSystemMessage(Component.literal("§cCurrent stage is invalid after reload."));
                                    }
                                });
                            }
                            return 1;
                        })
                )

                .then(literal("list")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            int i = 1;
                            for (StoryStage stage : StoryStageLoader.stages) {
                                if ("waypoint".equals(stage.trigger.type)) continue;

                                StringBuilder msg = new StringBuilder("§a" + i + " - " + stage.text);
                                if (stage.trigger != null) {
                                    switch (stage.trigger.type) {
                                        case "location" -> msg.append(" §7(Loc: ")
                                                .append(stage.trigger.x).append(",")
                                                .append(stage.trigger.y).append(",")
                                                .append(stage.trigger.z).append(" r=").append(stage.trigger.radius).append(")");
                                        case "item" -> msg.append(" §7(Item: ").append(stage.trigger.item).append(")");
                                        case "interaction" -> msg.append(" §7(NPC: ").append(stage.trigger.npc_name).append(")");
                                        case "locationitem" -> msg.append(" §7(Loc+Item)");
                                    }
                                }
                                source.sendSuccess(() -> Component.literal(msg.toString()), false);
                                i++;
                            }
                            return 1;
                        }))

                .then(literal("debug")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            UUID id = player.getUUID();

                            ctx.getSource().sendSystemMessage(Component.literal("§7[Debug] Interactions:"));
                            InteractionTracker.getTalkedTo(id).forEach(stageIndex ->
                                    ctx.getSource().sendSystemMessage(Component.literal("  §aStage " + stageIndex)));

                            return 1;
                        }))

                .then(literal("dotests")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            return TriggerTestCommand.runAllTests(player);
                        })
                )

                .then(literal("unmark")
                        .then(net.minecraft.commands.Commands.argument("stage", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    int stageIndex = IntegerArgumentType.getInteger(ctx, "stage");

                                    InteractionTracker.clearInteraction(player.getUUID(), stageIndex);
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Cleared interaction flag for stage " + stageIndex), false);
                                    return 1;
                                })
                        )
                )

                .then(literal("stage")

                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                                int stage = progress.getCurrentStage();
                                if (stage < StoryStageLoader.stages.size()) {
                                    int visibleStage = stage;
                                    for (int i = stage; i >= 0; i--) {
                                        if (!"waypoint".equals(StoryStageLoader.stages.get(i).trigger.type)) {
                                            visibleStage = i;
                                            break;
                                        }
                                    }
                                    String text = StoryStageLoader.stages.get(visibleStage).text;
                                    player.sendSystemMessage(Component.literal("§e[MainQuest] Current Stage: §f" + visibleStage));
                                    player.sendSystemMessage(Component.literal("§7→ " + text));
                                } else {
                                    player.sendSystemMessage(Component.literal("§aYou have completed all quests!"));
                                }
                            });
                            return 1;
                        })

                        .then(literal("set")
                                .then(net.minecraft.commands.Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayer();
                                            int visibleStage = IntegerArgumentType.getInteger(ctx, "value");

                                            final int[] newStage = {0};
                                            int count = -1;
                                            for (int i = 0; i < StoryStageLoader.stages.size(); i++) {
                                                if (!"waypoint".equals(StoryStageLoader.stages.get(i).trigger.type)) {
                                                    count++;
                                                    if (count == visibleStage) {
                                                        newStage[0] = i;
                                                        break;
                                                    }
                                                }
                                            }

                                            player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                                                progress.setStage(newStage[0]);
                                                StoryNetwork.sendStageToClient(player, newStage[0]);
                                                ModEventHandler.onStageStart(player, newStage[0]);
                                                UUID playerId = player.getUUID();
                                                for (int i = newStage[0] + 1; i < StoryStageLoader.stages.size(); i++) {
                                                    StoryStage stage = StoryStageLoader.stages.get(i);
                                                    if (stage.trigger != null && "interaction".equals(stage.trigger.type)) {
                                                        InteractionTracker.clearInteraction(playerId, i);
                                                    }
                                                }
                                            });

                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Set stage to visible index " + visibleStage + " (actual stage " + newStage[0] + ")"), false);
                                            return 1;
                                        })
                                )
                        )

                        .then(literal("advance")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                                        int newStage = progress.getCurrentStage() + 1;
                                        progress.setStage(newStage);
                                        StoryNetwork.sendStageToClient(player, newStage);
                                        ModEventHandler.onStageStart(player, newStage);
                                    });

                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Advanced stage by 1"), false);
                                    return 1;
                                }))

                        .then(literal("back")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                                        int oldStage = progress.getCurrentStage();
                                        int newStage = Math.max(0, oldStage - 1);
                                        progress.setStage(newStage);
                                        StoryNetwork.sendStageToClient(player, newStage);
                                        ModEventHandler.onStageStart(player, newStage);

                                        if (newStage < StoryStageLoader.stages.size()) {
                                            StoryStage stage = StoryStageLoader.stages.get(newStage);
                                            if (stage.trigger != null && "interaction".equals(stage.trigger.type)) {
                                                InteractionTracker.clearInteraction(player.getUUID(), newStage);
                                            }
                                        }
                                    });

                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Stage decreased by 1"), false);
                                    return 1;
                                }))

                        .then(literal("reset")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                                        progress.setStage(0);
                                        StoryNetwork.sendStageToClient(player, 0);
                                        ModEventHandler.onStageStart(player, 0);
                                        InteractionTracker.resetAllInteractions(player.getUUID());
                                    });

                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Stage reset to 0"), false);
                                    return 1;
                                }))
                )
        );
    }
}
