package net.jaxx0rr.jxmainquest.network;

import net.jaxx0rr.jxmainquest.util.ClientTimerManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StartTimerClientPacket {
    public static void encode(StartTimerClientPacket msg, FriendlyByteBuf buf) {}
    public static StartTimerClientPacket decode(FriendlyByteBuf buf) { return new StartTimerClientPacket(); }

    public static void handle(StartTimerClientPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(ClientTimerManager::start);
        ctx.get().setPacketHandled(true);
    }
}
