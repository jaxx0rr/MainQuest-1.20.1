package net.jaxx0rr.jxmainquest.network;

import net.jaxx0rr.jxmainquest.Main;
import net.jaxx0rr.jxmainquest.story.StoryDialogueLine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

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

    public static void sendInteractionComplete(String npcName) {
        CHANNEL.sendToServer(new InteractionCompletePacket(npcName));
    }

}
