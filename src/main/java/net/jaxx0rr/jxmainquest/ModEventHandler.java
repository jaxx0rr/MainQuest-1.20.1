package net.jaxx0rr.jxmainquest;

import net.jaxx0rr.jxmainquest.config.StoryStageLoader;
import net.jaxx0rr.jxmainquest.network.StoryNetwork;
import net.jaxx0rr.jxmainquest.story.InteractionTracker;
import net.jaxx0rr.jxmainquest.story.StoryProgressProvider;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.jaxx0rr.jxmainquest.util.SpawnRetryTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
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
    private static final boolean DEBUG_MODE = false;

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
                if (triggerMatches(player, stage.trigger, i)) {
                    newStage++;
                } else {
                    break; // Stop if the current stage trigger fails
                }
            }

            if (newStage != currentStage) {
                progress.setStage(newStage);

                onStageStart(serverPlayer, newStage);

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

                    if (stage == null || stage.trigger == null) {
                        System.out.println("[jxmainquest] Skipped null or malformed stage at index " + i);
                        continue;
                    }

                    if ("interaction".equals(stage.trigger.type)) {
                        InteractionTracker.clearInteraction(player.getUUID(), i);
                    }
                }


                StoryNetwork.sendStageToClient(serverPlayer, newStage);
            }
        });
    }

    public static void onStageStart(ServerPlayer player, int stageIndex) {
        if (stageIndex >= StoryStageLoader.stages.size()) return;

        StoryStage stage = StoryStageLoader.stages.get(stageIndex);
        if (stage.trigger == null) return;

        System.out.println("[jxmainquest] Stage start: " + stageIndex + " for " + player.getName().getString());

        StoryStage.Trigger trigger = stage.trigger;

        if ("interaction".equals(trigger.type)) {
            BlockPos target = new BlockPos(trigger.x, trigger.y, trigger.z);
            String npcName = trigger.npc_name;

            // Only spawn if chunk is loaded
            if (!player.level().hasChunkAt(target)) {
                System.out.println("[jxmainquest] Chunk not loaded for stage " + stageIndex + ", skipping spawn for now.");
                SpawnRetryTracker.mark(stageIndex, target);
                return;
            }

            if (player.level() instanceof ServerLevel serverLevel) {
                // 🧹 Always remove existing NPCs with this name at that exact block
                player.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(target))
                        .stream()
                        .filter(e -> e.getName().getString().equals(npcName))
                        .forEach(e -> {
                            e.remove(Entity.RemovalReason.KILLED);
                            System.out.println("[jxmainquest] Removed NPC '" + npcName + "' at " + target);
                        });

                // ✅ Spawn fresh NPC
                spawnNamedEntity(serverLevel, target, npcName, trigger.dir, trigger.profession, trigger.entity_type);
                System.out.println("[jxmainquest] Spawned NPC '" + npcName + "' at " + target + " for stage " + stageIndex);
            }
        }
        else if ("enemy".equals(trigger.type)) {
            BlockPos target = new BlockPos(trigger.x, trigger.y, trigger.z);

            if (!player.level().hasChunkAt(target)) {
                System.out.println("[jxmainquest] Chunk not loaded for enemy spawn at " + target + " (stage " + stageIndex + ")");
                return;
            }

            if (player.blockPosition().closerThan(target, 20.0)) {
                String enemyType = trigger.enemy;
                String enemyName = trigger.enemy_name;
                double radius = trigger.enemy_radius > 0 ? trigger.enemy_radius : 10.0;
/*
                AABB zone = new AABB(
                        target.getX() - radius, target.getY() - 5, target.getZ() - radius,
                        target.getX() + radius, target.getY() + 5, target.getZ() + radius
                );

                List<LivingEntity> duplicates = player.level().getEntitiesOfClass(LivingEntity.class, zone)
                        .stream()
                        .filter(e ->
                                e.isAlive() &&
                                        ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).toString().equals(enemyType) &&
                                        (enemyName == null || enemyName.equals(e.getName().getString()))
                        )
                        .toList();

                // Quietly discard duplicates without triggering death events
                for (LivingEntity e : duplicates) {
                    e.discard();
                }
*/
                //System.out.println("[jxmainquest] Discarded " + duplicates.size() + " duplicate enemies at " + target + " for stage " + stageIndex);

                // ✅ Spawn fresh enemy
                if (player.level() instanceof ServerLevel serverLevel) {
                    spawnEnemy(serverLevel, trigger);
                    System.out.println("[jxmainquest] Spawned enemy '" + enemyName + "' at " + target + " for stage " + stageIndex);
                }
            }
        }

    }


    public static boolean triggerMatches(Player player, StoryStage.Trigger trigger, int stageIndex){
        return switch (trigger.type) {

            case "enemy" -> {
                BlockPos target = new BlockPos(trigger.x, trigger.y, trigger.z);

                if (!player.blockPosition().closerThan(target, 20.0)) yield false;
                if (trigger.spawn_enemy != null && !trigger.spawn_enemy) yield false;

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

                yield false;
            }

            case "location", "waypoint" -> nearPos(player, new BlockPos(trigger.x, trigger.y, trigger.z), trigger.radius);

            case "item" -> {
                if (trigger.item == null || trigger.item.isEmpty()) {
                    System.err.println("[jxmainquest] item trigger is missing required 'item' field");
                    yield false;
                }

                boolean base = hasAllItems(player, List.of(trigger.item));
                boolean ors = hasOrItem(player, extractItems(
                        trigger.oritem1, trigger.oritem2, trigger.oritem3,
                        trigger.oritem4, trigger.oritem5, trigger.oritem6,
                        trigger.oritem7, trigger.oritem8, trigger.oritem9));
                boolean ands = hasAllItems(player, extractItems(
                        trigger.anditem1, trigger.anditem2, trigger.anditem3,
                        trigger.anditem4, trigger.anditem5, trigger.anditem6,
                        trigger.anditem7, trigger.anditem8, trigger.anditem9));

                //System.out.println("[jxmainquest] item: base=" + base + " ors=" + ors + " ands=" + ands);

                yield (base || ors) && ands;
            }

            case "locationitem" -> {
                if (trigger.item == null || trigger.item.isEmpty()) {
                    System.err.println("[jxmainquest] locationitem trigger missing required 'item'");
                    yield false;
                }

                boolean near = nearPos(player, new BlockPos(trigger.x, trigger.y, trigger.z), trigger.radius);

                boolean base = hasAllItems(player, List.of(trigger.item));
                boolean ors = hasOrItem(player, extractItems(
                        trigger.oritem1, trigger.oritem2, trigger.oritem3,
                        trigger.oritem4, trigger.oritem5, trigger.oritem6,
                        trigger.oritem7, trigger.oritem8, trigger.oritem9));
                boolean ands = hasAllItems(player, extractItems(
                        trigger.anditem1, trigger.anditem2, trigger.anditem3,
                        trigger.anditem4, trigger.anditem5, trigger.anditem6,
                        trigger.anditem7, trigger.anditem8, trigger.anditem9));

                //System.out.println("[jxmainquest] locationitem: near=" + near + " base=" + base + " ors=" + ors + " ands=" + ands);

                yield near && (base || ors) && ands;
            }


            case "interaction" -> {
                if (InteractionTracker.hasTalkedTo(player.getUUID(), stageIndex)) {
                    yield true;
                }
                yield false;
            }

            default -> false; // ✅ This fixes the error
        };
    }



    public static void spawnNamedEntity(ServerLevel level, BlockPos pos, String name, float yaw, @Nullable String professionId, @Nullable String entityTypeId) {
        // Default to villager if not specified
        ResourceLocation typeId = (entityTypeId != null && !entityTypeId.isEmpty())
                ? ResourceLocation.tryParse(entityTypeId)
                : ForgeRegistries.ENTITY_TYPES.getKey(EntityType.VILLAGER);

        if (typeId == null || !ForgeRegistries.ENTITY_TYPES.containsKey(typeId)) {
            System.err.println("[jxmainquest] Unknown entity type: " + entityTypeId);
            return;
        }

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(typeId);
        if (type == null) return;

        Entity rawEntity = type.create(level);
        if (!(rawEntity instanceof LivingEntity entity)) return;

        // Position and rotation
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);

        // Behavior and persistence
        entity.setCustomName(Component.literal(name));
        entity.setCustomNameVisible(true);

        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.setNoAi(true);
        }
        entity.setInvulnerable(true);
        entity.setSilent(true);

        // Special case: apply profession if it's a villager
        if (entity instanceof Villager villager) {
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
        }

        // Add to world
        level.addFreshEntity(entity);

        // Optional debug message
        for (ServerPlayer p : level.players()) {
            if (p.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
                p.sendSystemMessage(Component.literal("§7[Debug] §eSpawned entity (" + name + ") of type " + typeId + " at " +
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

        // Optional debug message
        for (ServerPlayer p : level.players()) {
            if (p.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
                p.sendSystemMessage(Component.literal("§7[Debug] §eSpawned enemy (" + trigger.enemy_name + ") at " +
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

    private static List<String> extractItems(String... items) {
        List<String> list = new ArrayList<>();
        for (String s : items) {
            if (s != null && !s.isEmpty()) list.add(s);
        }
        return list;
    }

    /*
    //WORKS !!!
    private static boolean hasAllItems(Player player, List<String> itemDefs) {
        for (String def : itemDefs) {
            if (def == null || def.isEmpty()) continue;

            String[] parts = def.split(":");
            String id = parts.length >= 2 ? parts[0] + ":" + parts[1] : def;
            int requiredCount = (parts.length == 3) ? Integer.parseInt(parts[2]) : 1;

            int totalCount = player.getInventory().items.stream()
                    .filter(stack -> !stack.isEmpty())
                    .peek(stack -> System.out.println("   [CHECK] Item in slot: " + ForgeRegistries.ITEMS.getKey(stack.getItem())))
                    .filter(stack -> ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals(id))
                    .mapToInt(ItemStack::getCount)
                    .sum();

            System.out.println("   [MATCH] Looking for: " + id + ", found: " + totalCount + " (need " + requiredCount + ")");
            if (totalCount < requiredCount) return false;
        }

        return true;
    }

    //WORKS !!!
    private static boolean hasOrItem(Player player, List<String> itemDefs) {
        for (String def : itemDefs) {
            if (def == null || def.isEmpty()) continue;

            String[] parts = def.split(":");
            String id = parts.length >= 2 ? parts[0] + ":" + parts[1] : def;
            int requiredCount = (parts.length == 3) ? Integer.parseInt(parts[2]) : 1;

            int totalCount = player.getInventory().items.stream()
                    .filter(stack -> !stack.isEmpty())
                    .peek(stack -> System.out.println("   [CHECK-OR] Item in slot: " + ForgeRegistries.ITEMS.getKey(stack.getItem())))
                    .filter(stack -> ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals(id))
                    .mapToInt(ItemStack::getCount)
                    .sum();

            System.out.println("   [MATCH-OR] Looking for: " + id + ", found: " + totalCount + " (need " + requiredCount + ")");
            if (totalCount >= requiredCount) return true;
        }

        return false;
    }
    */

    private static boolean hasAllItems(Player player, List<String> itemDefs) {
        for (String def : itemDefs) {
            if (def == null || def.isEmpty()) continue;

            String[] parts = def.split(":");
            String id = parts.length >= 2 ? parts[0] + ":" + parts[1] : def;
            int requiredCount = (parts.length == 3) ? Integer.parseInt(parts[2]) : 1;

            int totalCount = player.getInventory().items.stream()
                    .filter(stack -> !stack.isEmpty())
                    .filter(stack -> {
                        if (DEBUG_MODE) {
                            System.out.println("   [CHECK] Item in slot: " + ForgeRegistries.ITEMS.getKey(stack.getItem()));
                        }
                        return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals(id);
                    })
                    .mapToInt(ItemStack::getCount)
                    .sum();

            if (DEBUG_MODE) {
                System.out.println("   [MATCH] Looking for: " + id + ", found: " + totalCount + " (need " + requiredCount + ")");
            }

            if (totalCount < requiredCount) return false;
        }

        return true;
    }


    private static boolean hasOrItem(Player player, List<String> itemDefs) {
        for (String def : itemDefs) {
            if (def == null || def.isEmpty()) continue;

            String[] parts = def.split(":");
            String id = parts.length >= 2 ? parts[0] + ":" + parts[1] : def;
            int requiredCount = (parts.length == 3) ? Integer.parseInt(parts[2]) : 1;

            int totalCount = player.getInventory().items.stream()
                    .filter(stack -> !stack.isEmpty())
                    .filter(stack -> {
                        if (DEBUG_MODE) {
                            System.out.println("   [CHECK-OR] Item in slot: " + ForgeRegistries.ITEMS.getKey(stack.getItem()));
                        }
                        return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString().equals(id);
                    })
                    .mapToInt(ItemStack::getCount)
                    .sum();

            if (DEBUG_MODE) {
                System.out.println("   [MATCH-OR] Looking for: " + id + ", found: " + totalCount + " (need " + requiredCount + ")");
            }

            if (totalCount >= requiredCount) return true;
        }

        return false;
    }


}