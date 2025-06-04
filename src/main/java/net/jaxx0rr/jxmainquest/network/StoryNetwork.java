package net.jaxx0rr.jxmainquest.network;

import com.google.gson.Gson;
import net.jaxx0rr.jxmainquest.Main;
import net.jaxx0rr.jxmainquest.story.StoryDialogueLine;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

// StoryNetwork.java
public class StoryNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Main.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, StageSyncPacket.class, StageSyncPacket::encode, StageSyncPacket::decode, StageSyncPacket::handle);
        CHANNEL.registerMessage(id++, ReloadStagesPacket.class, ReloadStagesPacket::encode, ReloadStagesPacket::decode, ReloadStagesPacket::handle);
        CHANNEL.registerMessage(id++, OpenDialoguePacket.class, OpenDialoguePacket::encode, OpenDialoguePacket::decode, OpenDialoguePacket::handle);
        CHANNEL.registerMessage(id++, InteractionCompletePacket.class, InteractionCompletePacket::encode, InteractionCompletePacket::decode, InteractionCompletePacket::handle);
        CHANNEL.registerMessage(id++, StageListSyncPacket.class, StageListSyncPacket::encode, StageListSyncPacket::decode, StageListSyncPacket::handle);

        CHANNEL.registerMessage(id++, StartTimerClientPacket.class, StartTimerClientPacket::encode, StartTimerClientPacket::decode, StartTimerClientPacket::handle);
        CHANNEL.registerMessage(id++, StopTimerClientPacket.class, StopTimerClientPacket::encode, StopTimerClientPacket::decode, StopTimerClientPacket::handle);

    }

    public static void sendStartTimerPacket(ServerPlayer player) {
        CHANNEL.sendTo(new StartTimerClientPacket(), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendStopTimerPacket(ServerPlayer player) {
        CHANNEL.sendTo(new StopTimerClientPacket(), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendStageToClient(ServerPlayer player, int stage) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StageSyncPacket(stage));
    }

    public static void sendReloadRequest(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ReloadStagesPacket());
    }

    public static void sendOpenDialogue(ServerPlayer player, List<StoryDialogueLine> lines, String npcName) {
        CHANNEL.sendTo(new OpenDialoguePacket(npcName, lines), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendInteractionComplete() {
        CHANNEL.sendToServer(new InteractionCompletePacket());
    }

    public static void sendStageListToClient(ServerPlayer player, List<StoryStage> stages) {
        Gson gson = new Gson();
        String json = gson.toJson(stages);

        try {
            byte[] compressed = compress(json);
            CHANNEL.sendTo(new StageListSyncPacket(compressed), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        } catch (IOException e) {
            System.err.println("[jxmainquest] ❌ Failed to compress stage list for sync.");
            e.printStackTrace();
        }
    }

    public static byte[] compress(String str) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }


}
