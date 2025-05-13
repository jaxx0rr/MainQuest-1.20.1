package net.jaxx0rr.jxmainquest.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Supplier;

public class StageListSyncPacket {
    private final String json;

    public StageListSyncPacket(String json) {
        this.json = json;
    }

    public static void encode(StageListSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.json);
    }

    public static StageListSyncPacket decode(FriendlyByteBuf buf) {
        return new StageListSyncPacket(buf.readUtf());
    }

    public static void handle(StageListSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Gson gson = new Gson();
            Type type = new TypeToken<List<StoryStage>>(){}.getType();
            List<StoryStage> stages = gson.fromJson(packet.json, type);
            net.jaxx0rr.jxmainquest.story.StoryStageLoader.stages = stages;
            System.out.println("[jxmainquest] Client synced " + stages.size() + " stages from server.");
        });
        ctx.get().setPacketHandled(true);
    }
}
