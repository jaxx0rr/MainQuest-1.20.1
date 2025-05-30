package net.jaxx0rr.jxmainquest.network;

import net.jaxx0rr.jxmainquest.JxmqCommand;
import net.jaxx0rr.jxmainquest.ModEventHandler;
import net.jaxx0rr.jxmainquest.config.SpawnConfigLoader;
import net.jaxx0rr.jxmainquest.config.StoryStageLoader;
import net.jaxx0rr.jxmainquest.story.StoryProgressProvider;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.jaxx0rr.jxmainquest.util.EnemySpawnTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        ServerPlayer killerPlayer = null;

        Entity source = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();

        if (source instanceof ServerPlayer p) {
            killerPlayer = p;
        } else if (direct instanceof ServerPlayer p) {
            killerPlayer = p;
        } else if (source instanceof Projectile proj && proj.getOwner() instanceof ServerPlayer p) {
            killerPlayer = p;
        }

        // 🟡 Fallback: use SpawnTracker
        if (killerPlayer == null) {
            UUID ownerId = EnemySpawnTracker.getPlayerForMob(event.getEntity().getUUID());
            if (ownerId != null) {
                killerPlayer = level.getServer().getPlayerList().getPlayer(ownerId);
                System.out.println("[jxmainquest] Using SpawnTracker: found owner " + ownerId);
            }
        }

        AtomicReference<StoryStage.Trigger> matchedTrigger = new AtomicReference<>(null);

        // Check if any player has a matching enemy stage — used to drop reward
        for (ServerPlayer online : level.getServer().getPlayerList().getPlayers()) {
            online.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                int stageIndex = progress.getCurrentStage();
                if (stageIndex >= StoryStageLoader.stages.size()) return;

                StoryStage stage = StoryStageLoader.stages.get(stageIndex);
                if (!"enemy".equals(stage.trigger.type)) return;

                StoryStage.Trigger trigger = stage.trigger;

                ResourceLocation expectedType = getEnemyType(trigger.enemy);

                if (expectedType == null ||
                        !ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType()).equals(expectedType)) return;

                if (trigger.enemy_name != null &&
                        !trigger.enemy_name.equals(event.getEntity().getName().getString())) return;

                if (trigger.enemy_radius > 0) {
                    BlockPos expected = new BlockPos(trigger.x, trigger.y, trigger.z);
                    if (!event.getEntity().blockPosition().closerThan(expected, trigger.enemy_radius)) return;
                }

                matchedTrigger.set(trigger); // ✅ valid enemy, we can drop reward
            });
        }

        // Drop reward for valid trigger
        StoryStage.Trigger trigger = matchedTrigger.get();
        if (trigger != null) {
            if (trigger.reward_item != null && !trigger.reward_item.isEmpty()) {
                ResourceLocation itemId = ResourceLocation.tryParse(trigger.reward_item);
                if (itemId != null && ForgeRegistries.ITEMS.containsKey(itemId)) {
                    Item item = ForgeRegistries.ITEMS.getValue(itemId);
                    int count = trigger.reward_amount > 0 ? trigger.reward_amount : 1;
                    event.getEntity().spawnAtLocation(new ItemStack(item, count));
                    System.out.println("[jxmainquest] Dropped reward item: " + itemId);
                }
            }

            if (trigger.reward_xp > 0) {
                level.addFreshEntity(new ExperienceOrb(
                        level,
                        event.getEntity().getX(),
                        event.getEntity().getY(),
                        event.getEntity().getZ(),
                        trigger.reward_xp
                ));
                System.out.println("[jxmainquest] Dropped XP: " + trigger.reward_xp);
            }
        }

        // Progress only the killer if their stage matches
        final ServerPlayer finalKillerPlayer = killerPlayer;
        if (finalKillerPlayer != null) {
            finalKillerPlayer.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                int stageIndex = progress.getCurrentStage();
                System.out.println("[jxmainquest] Killer " + finalKillerPlayer.getName().getString() + " is at stage " + stageIndex);

                if (stageIndex >= StoryStageLoader.stages.size()) {
                    System.out.println("[jxmainquest] Stage index out of bounds");
                    return;
                }

                StoryStage stage = StoryStageLoader.stages.get(stageIndex);
                if (!"enemy".equals(stage.trigger.type)) {
                    System.out.println("[jxmainquest] Stage " + stageIndex + " is not enemy trigger");
                    return;
                }

                StoryStage.Trigger playerTrigger = stage.trigger;

                ResourceLocation expectedType = getEnemyType(playerTrigger.enemy);
                ResourceLocation actualType = ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType());
                if (expectedType == null || !actualType.equals(expectedType)) {
                    System.out.println("[jxmainquest] Entity type mismatch: expected " + expectedType + ", got " + actualType);
                    return;
                }

                if (playerTrigger.enemy_name != null &&
                        !playerTrigger.enemy_name.equals(event.getEntity().getName().getString())) {
                    System.out.println("[jxmainquest] Enemy name mismatch: expected " + playerTrigger.enemy_name + ", got " + event.getEntity().getName().getString());
                    return;
                }

                if (playerTrigger.enemy_radius > 0) {
                    BlockPos expected = new BlockPos(playerTrigger.x, playerTrigger.y, playerTrigger.z);
                    double dist = event.getEntity().blockPosition().distSqr(expected);
                    System.out.println("[jxmainquest] Distance squared from trigger center: " + dist);
                    if (!event.getEntity().blockPosition().closerThan(expected, playerTrigger.enemy_radius)) {
                        System.out.println("[jxmainquest] Entity is out of radius");
                        return;
                    }
                }

                if (progress.hasKilledForStage(stageIndex)) {
                    System.out.println("[jxmainquest] Player already recorded a kill for this stage");
                    return;
                }

                System.out.println("[jxmainquest] Advancing stage for player: " + finalKillerPlayer.getName().getString());
                progress.markKillForStage(stageIndex);
                progress.advanceStage();
                int newStage = progress.getCurrentStage();
                StoryNetwork.sendStageToClient(finalKillerPlayer, newStage);
                ModEventHandler.onStageStart(finalKillerPlayer, newStage);
            });
        }
    }

    public static ResourceLocation getEnemyType(String raw) {
        String[] parts = raw.split(":");
        return parts.length >= 2 ? ResourceLocation.tryParse(parts[0] + ":" + parts[1]) : null;
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
