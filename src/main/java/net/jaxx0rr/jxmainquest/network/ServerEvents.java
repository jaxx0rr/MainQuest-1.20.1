package net.jaxx0rr.jxmainquest.network;

import net.jaxx0rr.jxmainquest.JxmqCommand;
import net.jaxx0rr.jxmainquest.story.StoryProgressProvider;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.jaxx0rr.jxmainquest.story.StoryStageLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
            player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                StoryNetwork.sendStageToClient(player, progress.getCurrentStage());
            });
        }
    }


}
