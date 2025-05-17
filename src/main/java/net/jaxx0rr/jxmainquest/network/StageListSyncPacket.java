package net.jaxx0rr.jxmainquest.network;

/*
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
            StoryStageLoader.stages = stages;
            System.out.println("[jxmainquest] Client synced " + stages.size() + " stages from server.");
        });
        ctx.get().setPacketHandled(true);
    }
}
*/

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.jaxx0rr.jxmainquest.config.StoryStageLoader;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

public class StageListSyncPacket {
    private final byte[] compressedJson;

    public StageListSyncPacket(byte[] compressedJson) {
        this.compressedJson = compressedJson;
    }

    public static void encode(StageListSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeByteArray(packet.compressedJson);
    }

    public static StageListSyncPacket decode(FriendlyByteBuf buf) {
        return new StageListSyncPacket(buf.readByteArray());
    }

    public static void handle(StageListSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            try {
                String json = decompress(packet.compressedJson);
                Gson gson = new Gson();
                Type type = new TypeToken<List<StoryStage>>() {}.getType();
                List<StoryStage> stages = gson.fromJson(json, type);
                StoryStageLoader.stages = stages;
                System.out.println("[jxmainquest] ✅ Client synced " + stages.size() + " stages from server.");
            } catch (IOException e) {
                System.err.println("[jxmainquest] ❌ Failed to decompress stage list.");
                e.printStackTrace();
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static String decompress(byte[] compressed) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8);
            StringBuilder out = new StringBuilder();
            char[] buffer = new char[1024];
            int len;
            while ((len = reader.read(buffer)) > 0) {
                out.append(buffer, 0, len);
            }
            return out.toString();
        }
    }
}
