package net.jaxx0rr.jxmainquest.network;

import net.jaxx0rr.jxmainquest.config.StoryStageLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ReloadStagesPacket {
    public static void encode(ReloadStagesPacket msg, FriendlyByteBuf buf) {}
    public static ReloadStagesPacket decode(FriendlyByteBuf buf) { return new ReloadStagesPacket(); }

    public static void handle(ReloadStagesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            StoryStageLoader.loadStages();
            System.out.println("[jxmainquest] Reloaded stages.json on client.");
        });
        ctx.get().setPacketHandled(true);
    }
}
