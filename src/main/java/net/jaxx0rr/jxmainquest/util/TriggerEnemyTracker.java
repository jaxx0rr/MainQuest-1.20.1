// TriggerEnemyTracker.java
package net.jaxx0rr.jxmainquest.util;

import net.jaxx0rr.jxmainquest.config.StoryStageLoader;
import net.jaxx0rr.jxmainquest.story.StoryProgress;
import net.jaxx0rr.jxmainquest.story.StoryProgressProvider;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

@Mod.EventBusSubscriber
public class TriggerEnemyTracker {

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < 200) return; // every 10 seconds (20 ticks * 10)
        tickCounter = 0;

        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                    if (checkEnemyTriggerForPlayer(level, player, progress)) {
                        int stageIndex = progress.getCurrentStage();
                        progress.markKillForStage(stageIndex);
                        progress.advanceStage();
                        int newStage = progress.getCurrentStage();
                        net.jaxx0rr.jxmainquest.network.StoryNetwork.sendStageToClient(player, newStage);
                        net.jaxx0rr.jxmainquest.ModEventHandler.onStageStart(player, newStage);
                    }
                });
            }
        }
    }


    public static boolean checkEnemyTriggerForPlayer(ServerLevel level, ServerPlayer player, StoryProgress progress) {
        int stageIndex = progress.getCurrentStage();
        if (stageIndex >= StoryStageLoader.stages.size()) {
            //System.out.println("[jxmainquest] Stage index out of bounds for " + player.getName().getString());
            return false;
        }

        StoryStage stage = StoryStageLoader.stages.get(stageIndex);
        StoryStage.Trigger trigger = stage.trigger;

        if (!"enemy".equals(trigger.type)) {
            //System.out.println("[jxmainquest] Current stage is not an enemy trigger");
            return false;
        }

        if (trigger.enemy == null || trigger.enemy.isEmpty()) {
            //System.out.println("[jxmainquest] Trigger.enemy is missing");
            return false;
        }

        ResourceLocation id = getEnemyType(trigger.enemy);
        if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            //System.out.println("[jxmainquest] Enemy type invalid: " + trigger.enemy);
            return false;
        }

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            //System.out.println("[jxmainquest] Could not resolve EntityType for " + id);
            return false;
        }

        BlockPos center = new BlockPos(trigger.x, trigger.y, trigger.z);
        int radius = 100;

        if (!player.blockPosition().closerThan(center, radius)) {
            //System.out.println("[jxmainquest] Player " + player.getName().getString() + " is too far from trigger center");
            return false;
        }

        List<LivingEntity> mobs = level.getEntities(EntityTypeTest.forClass(LivingEntity.class),
                new net.minecraft.world.phys.AABB(
                        center.offset(-radius, -radius, -radius),
                        center.offset(radius, radius, radius)
                ),
                e -> {
                    boolean match = e.getType().equals(type) &&
                            (trigger.enemy_name == null || trigger.enemy_name.equals(e.getName().getString()));
                    if (match) {
                        //System.out.println("[jxmainquest] Found matching mob: " + e.getName().getString() + " (" + e.getUUID() + ")");
                    }
                    return match;
                });

        //System.out.println("[jxmainquest] Mob count in area: " + mobs.size());

        return mobs.isEmpty();
    }


    private static ResourceLocation getEnemyType(String enemy) {
        String[] parts = enemy.split(":");
        if (parts.length < 2) return null;
        return new ResourceLocation(parts[0], parts[1]);
    }
}
