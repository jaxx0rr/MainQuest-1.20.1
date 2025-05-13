package net.jaxx0rr.jxmainquest.network;

import net.jaxx0rr.jxmainquest.JxmqCommand;
import net.jaxx0rr.jxmainquest.story.InteractionTracker;
import net.jaxx0rr.jxmainquest.story.StoryProgressProvider;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.jaxx0rr.jxmainquest.story.StoryStageLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
        StoryStageLoader.loadStages(); // initial load
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        JxmqCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Entity target = event.getTarget();
        if (!target.hasCustomName()) return;

        String name = target.getName().getString();

        player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
            int stage = progress.getCurrentStage();
            if (stage >= StoryStageLoader.stages.size()) return;

            StoryStage current = StoryStageLoader.stages.get(stage);
            StoryStage.Trigger trigger = current.trigger;

            if ("interaction".equals(trigger.type) && name.equals(trigger.npc_name)) {
                if (trigger.dialogue != null && !trigger.dialogue.isEmpty()) {
                    StoryNetwork.sendOpenDialogue(player, trigger.dialogue, name);
                    event.setCanceled(true); // ✅ Prevent default (trade) GUI
                }
            }
        });
    }


    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(new ResourceLocation(MODID, "story_progress"), new StoryProgressProvider());
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

            // Sync current stage
            player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                StoryNetwork.sendStageListToClient(player, StoryStageLoader.stages);
                StoryNetwork.sendStageToClient(player, progress.getCurrentStage());
            });

        }
    }

    @SubscribeEvent
    public static void onEntityDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!villager.hasCustomName()) return;

        String name = villager.getName().getString();
        BlockPos pos = villager.blockPosition();

        InteractionTracker.clearSpawned(name, pos);
    }

    @SubscribeEvent
    public static void onEnemyKilled(LivingDeathEvent event) {

        /*
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
            int stageIndex = progress.getCurrentStage();
            if (stageIndex >= StoryStageLoader.stages.size()) return;

            StoryStage stage = StoryStageLoader.stages.get(stageIndex);
            if (!"enemy".equals(stage.trigger.type)) return;

            // Match entity type
            Entity killed = event.getEntity();
            ResourceLocation expectedType = ResourceLocation.tryParse(stage.trigger.enemy);
            if (expectedType == null || !ForgeRegistries.ENTITY_TYPES.getKey(killed.getType()).equals(expectedType)) return;

            // Optional: name match
            if (stage.trigger.enemy_name != null && !stage.trigger.enemy_name.isEmpty()) {
                if (!killed.hasCustomName() || !stage.trigger.enemy_name.equals(killed.getName().getString())) return;
            }

            // Optional: radius match
            if (stage.trigger.enemy_radius > 0) {
                BlockPos expected = new BlockPos(stage.trigger.x, stage.trigger.y, stage.trigger.z);
                if (!killed.blockPosition().closerThan(expected, stage.trigger.enemy_radius)) return;
            }

            // ✅ It matches — advance and grant reward
            progress.advanceStage();

            if (stage.trigger.reward_item != null && !stage.trigger.reward_item.isEmpty()) {
                ResourceLocation itemId = ResourceLocation.tryParse(stage.trigger.reward_item);
                if (itemId != null && ForgeRegistries.ITEMS.containsKey(itemId)) {
                    Item item = ForgeRegistries.ITEMS.getValue(itemId);
                    int count = stage.trigger.reward_amount > 0 ? stage.trigger.reward_amount : 1;

                    // 💡 DROP the item instead of adding it to inventory
                    killed.spawnAtLocation(new ItemStack(item, count));
                }
            }


            if (stage.trigger.reward_xp > 0) {
                killed.level().addFreshEntity(new ExperienceOrb(killed.level(),
                        killed.getX(), killed.getY(), killed.getZ(), stage.trigger.reward_xp));
            }

            StoryNetwork.sendStageToClient(player, progress.getCurrentStage());
        });
        */

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


}
