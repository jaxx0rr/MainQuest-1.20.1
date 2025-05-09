package net.jaxx0rr.jxmainquest.network;

import net.jaxx0rr.jxmainquest.client.ClientDialogueManager;
import net.jaxx0rr.jxmainquest.story.StoryDialogueLine;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenDialoguePacket {

    private final String npcName;
    private final List<StoryDialogueLine> dialogue;

    public OpenDialoguePacket(String npcName, List<StoryDialogueLine> dialogue) {
        this.npcName = npcName;
        this.dialogue = dialogue;
    }

    public static void encode(OpenDialoguePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.npcName);
        buf.writeInt(packet.dialogue.size());
        for (StoryDialogueLine line : packet.dialogue) {
            buf.writeUtf(line.npc != null ? line.npc : "");
            buf.writeUtf(line.player != null ? line.player : "");
            buf.writeInt(line.player_choices != null ? line.player_choices.size() : 0);
            if (line.player_choices != null) {
                for (String s : line.player_choices) buf.writeUtf(s);
            }
            buf.writeInt(line.npc_responses != null ? line.npc_responses.size() : 0);
            if (line.npc_responses != null) {
                for (String s : line.npc_responses) buf.writeUtf(s);
            }
        }
    }

    public static OpenDialoguePacket decode(FriendlyByteBuf buf) {
        String npcName = buf.readUtf();
        int count = buf.readInt();
        List<StoryDialogueLine> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StoryDialogueLine line = new StoryDialogueLine();
            line.npc = buf.readUtf();
            line.player = buf.readUtf();
            int pcCount = buf.readInt();
            if (pcCount > 0) {
                line.player_choices = new ArrayList<>();
                for (int j = 0; j < pcCount; j++) {
                    line.player_choices.add(buf.readUtf());
                }
            }
            int nrCount = buf.readInt();
            if (nrCount > 0) {
                line.npc_responses = new ArrayList<>();
                for (int j = 0; j < nrCount; j++) {
                    line.npc_responses.add(buf.readUtf());
                }
            }
            lines.add(line);
        }
        return new OpenDialoguePacket(npcName, lines);
    }

    public static void handle(OpenDialoguePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientDialogueManager.startDialogue(packet.npcName, packet.dialogue);
        }));
        ctx.get().setPacketHandled(true);
    }
}
