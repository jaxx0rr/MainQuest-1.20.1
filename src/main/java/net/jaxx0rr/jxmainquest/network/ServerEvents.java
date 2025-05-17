package net.jaxx0rr.jxmainquest.network;

import net.jaxx0rr.jxmainquest.JxmqCommand;
import net.jaxx0rr.jxmainquest.config.SpawnConfigLoader;
import net.jaxx0rr.jxmainquest.config.StoryStageLoader;
import net.jaxx0rr.jxmainquest.story.StoryProgressProvider;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import static net.jaxx0rr.jxmainquest.Main.MODID;

@Mod.EventBusSubscriber(modid = MODID)
public class ServerEvents {
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        StoryStageLoader.loadStages();
        SpawnConfigLoader.load();
    }


    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        JxmqCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onEntityRightClick(PlayerInteractEvent.EntityInteractSpecific event) {
        handleNpcInteraction(event.getEntity(), event.getTarget(), event);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            //event.addCapability(new ResourceLocation(MODID, "story_progress"), new StoryProgressProvider());
            event.addCapability(ResourceLocation.fromNamespaceAndPath(MODID, "story_progress"), new StoryProgressProvider());
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().getCapability(StoryProgressProvider.STORY).ifPresent(oldStore -> {
            event.getEntity().getCapability(StoryProgressProvider.STORY).ifPresent(newStore -> {
                newStore.setStage(oldStore.getCurrentStage());
            });
        });
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                StoryNetwork.sendStageListToClient(player, StoryStageLoader.stages);
                StoryNetwork.sendStageToClient(player, progress.getCurrentStage());

                if (progress.getCurrentStage() == 0) {
                    ServerLevel level = player.server.getLevel(SpawnConfigLoader.getInitialDimension());
                    BlockPos pos = SpawnConfigLoader.getInitialSpawn();
                    if (level != null) {
                        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
                        System.out.println("[jxmainquest] Teleported new player " + player.getName().getString() + " to custom spawn: " + pos);
                    }
                }
            });
        }
    }

/*
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isEndConquered()) {
            BlockPos pos = SpawnConfigLoader.getRespawnPoint();
            if (pos != null) {
                ServerLevel level = player.server.getLevel(SpawnConfigLoader.getRespawnDimension());
                if (level != null) {
                    player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                            player.getYRot(), player.getXRot());
                    System.out.println("[jxmainquest] Teleported player to respawn_point: " + pos);
                }
            }
        }
    }
*/

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.isEndConquered()) return; // skip End respawns

        // ✅ Only override if no valid bed
        if (player.getRespawnPosition() == null) {
            BlockPos pos = SpawnConfigLoader.getRespawnPoint();
            ResourceKey<Level> dim = SpawnConfigLoader.getRespawnDimension();

            if (pos != null) {
                ServerLevel level = player.server.getLevel(dim);
                if (level != null) {
                    player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                            player.getYRot(), player.getXRot());
                    System.out.println("[jxmainquest] Player had no bed, respawned at JSON-defined point: " + pos);
                }
            }
        }
    }


    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
    }




    @SubscribeEvent
    public static void onEnemyKilled(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) return;

        Entity killer = event.getSource().getEntity();
        ServerPlayer player = killer instanceof ServerPlayer ? (ServerPlayer) killer : null;

        for (ServerPlayer online : event.getEntity().getServer().getPlayerList().getPlayers()) {
            online.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                int stageIndex = progress.getCurrentStage();
                if (stageIndex >= StoryStageLoader.stages.size()) return;

                StoryStage stage = StoryStageLoader.stages.get(stageIndex);
                if (!"enemy".equals(stage.trigger.type)) return;

                StoryStage.Trigger trigger = stage.trigger;

                // Match enemy type
                ResourceLocation expectedType = ResourceLocation.tryParse(trigger.enemy);
                if (expectedType == null ||
                        !ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType()).equals(expectedType)) return;

                // Match name
                if (trigger.enemy_name != null && !trigger.enemy_name.equals(event.getEntity().getName().getString())) return;

                // Match radius
                if (trigger.enemy_radius > 0) {
                    BlockPos expected = new BlockPos(trigger.x, trigger.y, trigger.z);
                    if (!event.getEntity().blockPosition().closerThan(expected, trigger.enemy_radius)) return;
                }

                // ✅ Progress the quest for this player
                progress.advanceStage();
                StoryNetwork.sendStageToClient(online, progress.getCurrentStage());

                // ✅ Drop reward from entity (even if killed by fire)
                if (trigger.reward_item != null && !trigger.reward_item.isEmpty()) {
                    ResourceLocation itemId = ResourceLocation.tryParse(trigger.reward_item);
                    if (itemId != null && ForgeRegistries.ITEMS.containsKey(itemId)) {
                        Item item = ForgeRegistries.ITEMS.getValue(itemId);
                        int count = trigger.reward_amount > 0 ? trigger.reward_amount : 1;
                        event.getEntity().spawnAtLocation(new ItemStack(item, count));
                    }
                }

                if (trigger.reward_xp > 0) {
                    event.getEntity().level().addFreshEntity(new ExperienceOrb(
                            event.getEntity().level(),
                            event.getEntity().getX(),
                            event.getEntity().getY(),
                            event.getEntity().getZ(),
                            trigger.reward_xp
                    ));
                }
            });
        }

    }

    private static void handleNpcInteraction(Entity source, Entity target, PlayerInteractEvent event) {
        if (!(source instanceof ServerPlayer player)) return;
        if (!target.hasCustomName()) return;

        System.out.println("[jxmainquest] handleNpcInteraction: player=" + source.getName().getString() +
                ", target=" + target.getName().getString());

        String name = target.getName().getString();

        player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
            int stage = progress.getCurrentStage();
            if (stage >= StoryStageLoader.stages.size()) return;

            StoryStage current = StoryStageLoader.stages.get(stage);
            StoryStage.Trigger trigger = current.trigger;

            // 🟢 Match interaction type and NPC name
            if ("interaction".equals(trigger.type) && name.equals(trigger.npc_name)) {
                // ✅ Cancel first — no matter what
                event.setCanceled(true);

                // 🟡 Only send dialogue if it exists
                if (trigger.dialogue != null && !trigger.dialogue.isEmpty()) {
                    System.out.println("[jxmainquest] Sending dialogue for stage interaction: " + name);
                    StoryNetwork.sendOpenDialogue(player, trigger.dialogue, name);
                } else {
                    System.out.println("[jxmainquest] No dialogue found for NPC: " + name);
                }
            }
        });
    }



}
