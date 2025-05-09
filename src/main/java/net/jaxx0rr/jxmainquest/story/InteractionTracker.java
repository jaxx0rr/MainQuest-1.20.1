package net.jaxx0rr.jxmainquest.story;

import java.util.*;

public class InteractionTracker {

    private static final Map<UUID, Set<String>> talkedTo = new HashMap<>();

    // Called when a player interacts with a named NPC
    public static void markInteracted(UUID player, String npcName) {
        talkedTo.computeIfAbsent(player, k -> new HashSet<>()).add(npcName);
    }

    // Checks if player has already interacted with the named NPC
    public static boolean hasTalkedTo(UUID player, String npcName) {
        return talkedTo.getOrDefault(player, Collections.emptySet()).contains(npcName);
    }

    // Removes a specific interaction from memory
    public static void clearInteraction(UUID player, String npcName) {
        Set<String> names = talkedTo.get(player);
        if (names != null) {
            names.remove(npcName);
            if (names.isEmpty()) {
                talkedTo.remove(player); // cleanup if empty
            }
        }
    }

    // Completely resets all tracked interactions for a player
    public static void resetAllInteractions(UUID player) {
        talkedTo.remove(player);
    }
}