package net.jaxx0rr.jxmainquest.network;

import net.jaxx0rr.jxmainquest.util.ClientTimerManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StopTimerClientPacket {
    public static void encode(StopTimerClientPacket msg, FriendlyByteBuf buf) {}
    public static StopTimerClientPacket decode(FriendlyByteBuf buf) { return new StopTimerClientPacket(); }

    public static void handle(StopTimerClientPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(ClientTimerManager::stop);
        ctx.get().setPacketHandled(true);
    }
}
