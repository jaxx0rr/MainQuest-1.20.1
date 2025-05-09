package net.jaxx0rr.jxmainquest.network;

import net.jaxx0rr.jxmainquest.story.InteractionTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class InteractionCompletePacket {
    private final String npcName;

    public InteractionCompletePacket(String npcName) {
        this.npcName = npcName;
    }

    public static void encode(InteractionCompletePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.npcName);
    }

    public static InteractionCompletePacket decode(FriendlyByteBuf buf) {
        return new InteractionCompletePacket(buf.readUtf());
    }

    public static void handle(InteractionCompletePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                InteractionTracker.markInteracted(player.getUUID(), packet.npcName);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
