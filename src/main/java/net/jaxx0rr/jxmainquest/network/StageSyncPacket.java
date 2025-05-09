package net.jaxx0rr.jxmainquest.network;

import net.jaxx0rr.jxmainquest.client.ClientStoryState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// StageSyncPacket.java
public class StageSyncPacket {
    private final int stage;

    public StageSyncPacket(int stage) {
        this.stage = stage;
    }

    public static void encode(StageSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.stage);
    }

    public static StageSyncPacket decode(FriendlyByteBuf buf) {
        return new StageSyncPacket(buf.readInt());
    }

    public static void handle(StageSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientStoryState.setStage(packet.stage); // handles transition + assignment
        });
        ctx.get().setPacketHandled(true);
    }

    public int getStage() {
        return stage;
    }
}
