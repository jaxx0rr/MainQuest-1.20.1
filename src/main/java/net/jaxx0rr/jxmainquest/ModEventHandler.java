package net.jaxx0rr.jxmainquest;

import net.jaxx0rr.jxmainquest.network.StoryNetwork;
import net.jaxx0rr.jxmainquest.story.InteractionTracker;
import net.jaxx0rr.jxmainquest.story.StoryProgressProvider;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.jaxx0rr.jxmainquest.story.StoryStageLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = Main.MODID)
public class ModEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("jxmainquest");

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

                LOGGER.info("[jxmainquest] Stage changed to " + newStage + " for player " + player.getName().getString());

                for (int i = currentStage; i < newStage; i++) {
                    StoryStage stage = StoryStageLoader.stages.get(i);

                    if (stage.trigger != null && stage.trigger.reward_item != null && !stage.trigger.reward_item.isEmpty()) {
                        ResourceLocation itemId = ResourceLocation.tryParse(stage.trigger.reward_item);
                        if (itemId != null && ForgeRegistries.ITEMS.containsKey(itemId)) {
                            Item item = ForgeRegistries.ITEMS.getValue(itemId);
                            int count = stage.trigger.reward_amount > 0 ? stage.trigger.reward_amount : 1;
                            player.getInventory().add(new ItemStack(item, count));
                        } else {
                            System.err.println("[jxmainquest] Invalid reward item: " + stage.trigger.reward_item);
                        }
                    }

                    if (stage.trigger.reward_xp > 0) {
                        player.giveExperiencePoints(stage.trigger.reward_xp);
                    }
                }

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

            case "enemy" -> {
                BlockPos target = new BlockPos(trigger.x, trigger.y, trigger.z);
                //LOGGER.info("[jxmainquest] Checking enemy trigger at " + target + " for player " + player.getName().getString());
                //System.err.println("[jxmainquest] println - Checking enemy trigger at " + target + " for player " + player.getName().getString());

                if (!player.blockPosition().closerThan(target, 20.0)) {
                    //LOGGER.info("[jxmainquest] Player too far from spawn target");
                    yield false;
                }

                if (trigger.spawn_enemy != null && !trigger.spawn_enemy) {
                    //LOGGER.info("[jxmainquest] spawn_enemy is false — skipping");
                    yield false;
                }

                double checkRadius = trigger.enemy_radius > 0 ? trigger.enemy_radius : 10.0;

                boolean found = player.level().getEntitiesOfClass(LivingEntity.class,
                                new net.minecraft.world.phys.AABB(
                                        target.getX() - checkRadius, target.getY() - 5, target.getZ() - checkRadius,
                                        target.getX() + checkRadius, target.getY() + 5, target.getZ() + checkRadius))
                        .stream()
                        .anyMatch(e ->
                                e.isAlive() &&
                                        ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).toString().equals(trigger.enemy) &&
                                        (trigger.enemy_name == null || trigger.enemy_name.equals(e.getName().getString()))
                        );

                //LOGGER.info("[jxmainquest] Existing enemy found nearby? " + found);

                if (!found && player.level() instanceof ServerLevel serverLevel
                        && !InteractionTracker.wasSpawned(player.getUUID(), trigger.enemy, target)) {

                    //LOGGER.info("[jxmainquest] Spawning enemy: " + trigger.enemy + " at " + target);
                    spawnEnemy(serverLevel, trigger);

                    InteractionTracker.markSpawned(player.getUUID(), trigger.enemy, target);
                }

                yield false;
            }



            case "location", "waypoint" -> nearPos(player, new BlockPos(trigger.x, trigger.y, trigger.z), trigger.radius);

            case "item" -> {
                boolean base = hasAllItems(player, List.of(trigger.item));
                boolean ands = hasAllItems(player, extractItems(trigger.anditem1, trigger.anditem2, trigger.anditem3,
                        trigger.anditem4, trigger.anditem5, trigger.anditem6,
                        trigger.anditem7, trigger.anditem8, trigger.anditem9));
                boolean ors = hasAnyItem(player, extractItems(trigger.oritem1, trigger.oritem2, trigger.oritem3,
                        trigger.oritem4, trigger.oritem5, trigger.oritem6,
                        trigger.oritem7, trigger.oritem8, trigger.oritem9));
                yield base && ands && ors;
            }

            case "locationitem" -> {
                boolean near = nearPos(player, new BlockPos(trigger.x, trigger.y, trigger.z), trigger.radius);
                boolean base = hasAllItems(player, List.of(trigger.item));
                boolean ands = hasAllItems(player, extractItems(trigger.anditem1, trigger.anditem2, trigger.anditem3,
                        trigger.anditem4, trigger.anditem5, trigger.anditem6,
                        trigger.anditem7, trigger.anditem8, trigger.anditem9));
                boolean ors = hasAnyItem(player, extractItems(trigger.oritem1, trigger.oritem2, trigger.oritem3,
                        trigger.oritem4, trigger.oritem5, trigger.oritem6,
                        trigger.oritem7, trigger.oritem8, trigger.oritem9));
                yield near && base && ands && ors;
            }

            case "interaction" -> {
                String npcName = trigger.npc_name;

                // Already interacted?
                if (InteractionTracker.hasTalkedTo(player.getUUID(), npcName)) yield true;

                BlockPos target = new BlockPos(trigger.x, trigger.y, trigger.z);

                // Only spawn or check NPC if the player is nearby
                if (!player.blockPosition().closerThan(target, 20.0)) yield false;
/*
                boolean found = player.level().getEntitiesOfClass(Villager.class,
                                new net.minecraft.world.phys.AABB(
                                        target.getX() - 5, target.getY() - 3, target.getZ() - 5,
                                        target.getX() + 5, target.getY() + 3, target.getZ() + 5))
                        .stream().anyMatch(e -> e.getName().getString().equals(npcName));
*/

                boolean found = player.level().getEntitiesOfClass(Villager.class,
                                new net.minecraft.world.phys.AABB(
                                        target.getX() - 1, target.getY() - 1, target.getZ() - 1,
                                        target.getX() + 1, target.getY() + 2, target.getZ() + 1))
                        .stream()
                        .filter(e -> e.isAlive())
                        .anyMatch(e -> e.getName().getString().equals(npcName));


                if (!found
                        && player.level() instanceof ServerLevel serverLevel
                        && !InteractionTracker.wasSpawned(player.getUUID(), npcName, target)) {

                    spawnNamedVillager(serverLevel, target, npcName, trigger.dir, trigger.profession);
                    InteractionTracker.markSpawned(player.getUUID(), npcName, target);
                }

                yield false;
            }

            default -> false;
        };
    }



    public static void spawnNamedVillager(ServerLevel level, BlockPos pos, String name, float yaw, @Nullable String professionId) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return;

        // Position and rotation
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        villager.setYRot(yaw);
        villager.setYHeadRot(yaw);
        villager.setYBodyRot(yaw);

        // Appearance and behavior
        villager.setCustomName(Component.literal(name));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setNoAi(true);

        // ✅ Apply profession if provided
        VillagerProfession profession = VillagerProfession.NONE;
        if (professionId != null && !professionId.isEmpty()) {
            try {
                ResourceLocation key = ResourceLocation.tryParse(professionId);
                if (key != null && BuiltInRegistries.VILLAGER_PROFESSION.containsKey(key)) {
                    profession = BuiltInRegistries.VILLAGER_PROFESSION.get(key);
                } else {
                    System.err.println("[jxmainquest] Unknown profession: " + professionId);
                }
            } catch (Exception e) {
                System.err.println("[jxmainquest] Invalid profession string: " + professionId);
            }
        }

        villager.setVillagerData(villager.getVillagerData().setProfession(profession));

        // Add to world
        level.addFreshEntity(villager);

        // Optional debug message
        for (ServerPlayer p : level.players()) {
            if (p.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
                p.sendSystemMessage(Component.literal("§7[Debug] §eSpawned NPC (" + name + ") at " +
                        pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
            }
        }
    }

    public static void spawnEnemy(ServerLevel level, StoryStage.Trigger trigger) {
        //LOGGER.info("[jxmainquest] Attempting to spawn enemy: " + trigger.enemy);

        ResourceLocation id = ResourceLocation.tryParse(trigger.enemy);
        if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            //LOGGER.error("[jxmainquest] Unknown enemy type: " + trigger.enemy);
            return;
        }

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            //LOGGER.error("[jxmainquest] EntityType is null for " + id);
            return;
        }

        LivingEntity entity = (LivingEntity) type.create(level);
        if (entity == null) {
            //LOGGER.error("[jxmainquest] Could not create entity: " + id);
            return;
        }

        BlockPos pos = new BlockPos(trigger.x, trigger.y, trigger.z);
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        if (trigger.enemy_name != null && !trigger.enemy_name.isEmpty()) {
            entity.setCustomName(Component.literal(trigger.enemy_name));
            entity.setCustomNameVisible(true);
        }

        // ✅ Apply spawn behavior for mod entities
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        }

        level.addFreshEntity(entity);
        //LOGGER.info("[jxmainquest] Spawned enemy: " + trigger.enemy + " at " + pos);
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

    private static List<String> extractItems(String... items) {
        List<String> list = new ArrayList<>();
        for (String s : items) {
            if (s != null && !s.isEmpty()) list.add(s);
        }
        return list;
    }

    private static boolean hasAllItems(Player player, List<String> itemDefs) {
        for (String def : itemDefs) {
            if (def == null || def.isEmpty()) continue;

            String[] parts = def.split(":");
            String id = parts.length >= 2 ? parts[0] + ":" + parts[1] : def;
            int requiredCount = (parts.length == 3) ? Integer.parseInt(parts[2]) : 1;

            int totalCount = player.getInventory().items.stream()
                    .filter(stack -> !stack.isEmpty())
                    .filter(stack -> ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals(id))
                    .mapToInt(ItemStack::getCount)
                    .sum();

            if (totalCount < requiredCount) return false;
        }
        return true;
    }


    private static boolean hasAnyItem(Player player, List<String> itemDefs) {
        for (String def : itemDefs) {
            if (def == null || def.isEmpty()) continue;

            String[] parts = def.split(":");
            String id = parts.length >= 2 ? parts[0] + ":" + parts[1] : def;
            int requiredCount = (parts.length == 3) ? Integer.parseInt(parts[2]) : 1;

            int totalCount = player.getInventory().items.stream()
                    .filter(stack -> !stack.isEmpty())
                    .filter(stack -> ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals(id))
                    .mapToInt(ItemStack::getCount)
                    .sum();

            if (totalCount >= requiredCount) return true;
        }
        return itemDefs.isEmpty(); // no or-items specified = satisfied
    }


}