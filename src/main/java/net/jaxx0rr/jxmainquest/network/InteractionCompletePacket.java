package net.jaxx0rr.jxmainquest.network;

import net.jaxx0rr.jxmainquest.story.InteractionTracker;
import net.jaxx0rr.jxmainquest.story.StoryProgressProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// Remove npcName entirely
public class InteractionCompletePacket {
    public static void encode(InteractionCompletePacket packet, FriendlyByteBuf buf) {}

    public static InteractionCompletePacket decode(FriendlyByteBuf buf) {
        return new InteractionCompletePacket();
    }

    public static void handle(InteractionCompletePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
                    InteractionTracker.markInteracted(player.getUUID(), progress.getCurrentStage());
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

