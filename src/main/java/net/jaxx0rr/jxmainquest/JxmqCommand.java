package net.jaxx0rr.jxmainquest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.jaxx0rr.jxmainquest.network.StoryNetwork;
import net.jaxx0rr.jxmainquest.story.InteractionTracker;
import net.jaxx0rr.jxmainquest.story.StoryProgressProvider;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.jaxx0rr.jxmainquest.story.StoryStageLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class JxmqCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jxmq")
                .requires(source -> source.hasPermission(2))

                // /jxmq reload
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            int count = StoryStageLoader.reloadStages();

                            if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                                StoryNetwork.sendReloadRequest(player);
                            }

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("[jxmainquest] Reloaded " + count + " stages."), true);
                            return 1;
                        }))

                // /jxmq list
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            int i = 1;
                            for (StoryStage stage : StoryStageLoader.stages) {
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

                // /jxmq stage ...
                .then(Commands.literal("stage")

                        // ✅ NEW: /jxmq stage → show current stage
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                                int stage = progress.getCurrentStage();

                                if (stage < StoryStageLoader.stages.size()) {
                                    String text = StoryStageLoader.stages.get(stage).text;
                                    player.sendSystemMessage(Component.literal("§e[MainQuest] Current Stage: §f" + stage));
                                    player.sendSystemMessage(Component.literal("§7→ " + text));
                                } else {
                                    player.sendSystemMessage(Component.literal("§aYou have completed all quests!"));
                                }
                            });
                            return 1;
                        })

                        // /jxmq stage set <value>
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayer();
                                            int newStage = IntegerArgumentType.getInteger(ctx, "value");

                                            player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                                                progress.setStage(newStage);
                                                StoryNetwork.sendStageToClient(player, newStage);

                                                // Clear future interaction triggers
                                                UUID playerId = player.getUUID();
                                                for (int i = newStage + 1; i < StoryStageLoader.stages.size(); i++) {
                                                    StoryStage stage = StoryStageLoader.stages.get(i);
                                                    if (stage.trigger != null && "interaction".equals(stage.trigger.type)) {
                                                        String npcName = stage.trigger.npc_name;
                                                        if (npcName != null && !npcName.isEmpty()) {
                                                            InteractionTracker.clearInteraction(playerId, npcName);
                                                        }
                                                    }
                                                }
                                            });

                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal("Set stage to " + newStage), false);
                                            return 1;
                                        })))

                        // /jxmq stage advance
                        .then(Commands.literal("advance")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                                        int newStage = progress.getCurrentStage() + 1;
                                        progress.setStage(newStage);
                                        StoryNetwork.sendStageToClient(player, newStage);
                                    });

                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Advanced stage by 1"), false);
                                    return 1;
                                }))

                        // /jxmq stage back
                        .then(Commands.literal("back")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                                        int newStage = Math.max(0, progress.getCurrentStage() - 1);
                                        progress.setStage(newStage);
                                        StoryNetwork.sendStageToClient(player, newStage);

                                        // Clear future interaction triggers
                                        UUID playerId = player.getUUID();
                                        for (int i = newStage + 1; i < StoryStageLoader.stages.size(); i++) {
                                            StoryStage stage = StoryStageLoader.stages.get(i);
                                            if (stage.trigger != null && "interaction".equals(stage.trigger.type)) {
                                                String npcName = stage.trigger.npc_name;
                                                if (npcName != null && !npcName.isEmpty()) {
                                                    InteractionTracker.clearInteraction(playerId, npcName);
                                                }
                                            }
                                        }
                                    });

                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("Stage decreased by 1"), false);
                                    return 1;
                                }))

                        // /jxmq stage reset
                        .then(Commands.literal("reset")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                                        progress.setStage(0);
                                        StoryNetwork.sendStageToClient(player, 0);
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
