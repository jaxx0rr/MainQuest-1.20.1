package net.jaxx0rr.jxmainquest;

import net.jaxx0rr.jxmainquest.network.StoryNetwork;
import net.jaxx0rr.jxmainquest.story.InteractionTracker;
import net.jaxx0rr.jxmainquest.story.StoryProgressProvider;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.jaxx0rr.jxmainquest.story.StoryStageLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = Main.MODID)
public class ModEventHandler {


    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof ServerPlayer serverPlayer)) return;

        Player player = event.player;

        player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
            int currentStage = progress.getCurrentStage();
            int newStage = currentStage;

            //System.out.println(StoryStageLoader.stages.size());

            // Go through remaining stages and evaluate triggers in order
            for (int i = currentStage; i < StoryStageLoader.stages.size(); i++) {
                StoryStage stage = StoryStageLoader.stages.get(i);
                if (triggerMatches(player, stage.trigger)) {
                    newStage++;
                } else {
                    break; // Stop if the current stage trigger fails
                }
            }

            if (newStage != currentStage) {
                progress.setStage(newStage);

                // Reset interactions for upcoming stages
                for (int i = newStage + 1; i < StoryStageLoader.stages.size(); i++) {
                    StoryStage stage = StoryStageLoader.stages.get(i);
                    if ("interaction".equals(stage.trigger.type)) {
                        InteractionTracker.clearInteraction(player.getUUID(), stage.trigger.npc_name);
                    }
                }

                StoryNetwork.sendStageToClient(serverPlayer, newStage);
            }
        });
    }


    private static boolean triggerMatches(Player player, StoryStage.Trigger trigger) {
        return switch (trigger.type) {
            case "location" -> nearPos(player, new BlockPos(trigger.x, trigger.y, trigger.z), trigger.radius);
            case "item" -> hasItem(player, trigger.item);
            case "locationitem" -> nearPos(player, new BlockPos(trigger.x, trigger.y, trigger.z), trigger.radius)
                    && hasItem(player, trigger.item);
            case "interaction" -> {
                String npcName = trigger.npc_name;

                // Already interacted?
                if (InteractionTracker.hasTalkedTo(player.getUUID(), npcName)) yield true;

                BlockPos target = new BlockPos(trigger.x, trigger.y, trigger.z);

                // Only spawn or check NPC if the player is nearby
                if (!player.blockPosition().closerThan(target, 10.0)) yield false;

                // Is NPC already nearby?
                boolean found = player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(10))
                        .stream().anyMatch(e -> e.getName().getString().equals(npcName));

                if (!found && player.level() instanceof ServerLevel serverLevel) {
                    spawnNamedVillager(serverLevel, target, npcName);
                }

                yield false;
            }

            default -> false;
        };
    }

    public static void spawnNamedVillager(ServerLevel level, BlockPos pos, String name) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return;



        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        villager.setCustomName(Component.literal(name));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setNoAi(true); // ✅ prevent wandering
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));

        level.addFreshEntity(villager);

        for (ServerPlayer p : level.players()) {
            if (p.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
                p.sendSystemMessage(Component.literal("§7[Debug] §eSpawned NPC ("+name+") at " +
                        pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
            }
        }

    }




    private static boolean nearPos(Player player, BlockPos pos, int radius) {
        return player.blockPosition().closerThan(pos, radius);
    }

    private static boolean hasItem(Player player, String itemId) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
                if (id.equals(itemId)) return true;
            }
        }
        return false;
    }


}