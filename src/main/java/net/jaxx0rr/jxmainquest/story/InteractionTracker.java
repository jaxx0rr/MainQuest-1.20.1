package net.jaxx0rr.jxmainquest.story;

import java.util.*;

public class InteractionTracker {

    private static final Map<UUID, Set<Integer>> talkedTo = new HashMap<>();


    public static boolean hasTalkedTo(UUID player, int stageIndex) {
        return talkedTo.getOrDefault(player, Collections.emptySet()).contains(stageIndex);
    }

    public static void markInteracted(UUID player, int stageIndex) {
        System.out.println("[Server] Marked interaction complete for stage " + stageIndex + " (player UUID = " + player + ")");

        talkedTo.computeIfAbsent(player, k -> new HashSet<>()).add(stageIndex);
    }

    public static Set<Integer> getTalkedTo(UUID player) {
        return talkedTo.getOrDefault(player, Collections.emptySet());
    }

    public static void clearInteraction(UUID player, int stageIndex) {
        Set<Integer> stages = talkedTo.get(player);
        if (stages != null) {
            stages.remove(stageIndex);
            if (stages.isEmpty()) talkedTo.remove(player);
        }
    }

    public static void resetAllInteractions(UUID player) {
        talkedTo.remove(player);
    }


}