package net.jaxx0rr.jxmainquest.client;

import net.jaxx0rr.jxmainquest.network.StoryNetwork;
import net.jaxx0rr.jxmainquest.story.StoryDialogueLine;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ClientDialogueManager {

    private static boolean active = false;
    private static String npcName;
    private static List<String> playerChoices;
    private static List<StoryDialogueLine> lines;
    private static int index;
    private static int delayTicks = 0;
    private static boolean waitingForChoice = false;

    public static void startDialogue(String npcName, List<StoryDialogueLine> dialogueLines) {
        ClientDialogueManager.npcName = npcName;
        ClientDialogueManager.lines = dialogueLines;
        ClientDialogueManager.index = 0;
        ClientDialogueManager.active = true;

        advanceDialogue(); // or whatever starts showing the first line
    }

    public static void advanceDialogue() {
        if (!active || lines == null || index >= lines.size()) {
            end();
            return;
        }

        StoryDialogueLine line = lines.get(index);

        if (line.npc != null && !line.npc.isEmpty()) {
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("§e" + npcName + ": §f" + line.npc)
            );
        }

        // ✅ First: check for player choices
        if (line.player_choices != null && line.npc_responses != null &&
                !line.player_choices.isEmpty() && !line.npc_responses.isEmpty()) {

            waitingForChoice = true;
            for (int i = 0; i < line.player_choices.size(); i++) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal("[" + (i + 1) + "] §7" + line.player_choices.get(i))
                );
            }
            return;
        }

        // ✅ Then: check for a single player line (use as [1] choice)
        if (line.player != null && !line.player.isEmpty()) {
            waitingForChoice = true;
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("[1] §7" + line.player)
            );
            return;
        }

        // ✅ Otherwise: just delay and move on
        index++;
        delayTicks = 40;
    }

    public static void selectChoice(int choiceIndex) {
        if (!active || lines == null || index >= lines.size() || !waitingForChoice) return;

        StoryDialogueLine line = lines.get(index);

        if (line.player_choices != null && line.npc_responses != null &&
                !line.player_choices.isEmpty() && !line.npc_responses.isEmpty()) {

            if (choiceIndex >= line.player_choices.size()) return;

            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("§bYou: §f" + line.player_choices.get(choiceIndex))
            );
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("§e" + npcName + ": §f" + line.npc_responses.get(choiceIndex))
            );

        } else if (line.player != null && !line.player.isEmpty()) {
            if (choiceIndex != 0) return; // Only key 1 is valid

            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("§bYou: §f" + line.player)
            );
        } else {
            return; // Nothing to respond to
        }

        waitingForChoice = false;
        index++;
        delayTicks = 40;
    }


    public static void onTick() {
        if (!active) return;

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        if (waitingForChoice) return;

        advanceDialogue();
    }

    public static boolean isAwaitingChoice() {
        return waitingForChoice;
    }


    public static boolean isActive() {
        return active;
    }

    public static void end() {
        // ✅ Tell the server the dialogue is complete BEFORE clearing npcName
        if (npcName != null) {
            StoryNetwork.sendInteractionComplete(npcName);
        }

        active = false;
        lines = null;
        npcName = null;
        index = 0;
    }

}
