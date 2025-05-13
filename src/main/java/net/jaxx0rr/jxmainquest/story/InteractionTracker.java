package net.jaxx0rr.jxmainquest.story;

import net.minecraft.core.BlockPos;

import java.util.*;

public class InteractionTracker {

    private static final Map<UUID, Set<String>> talkedTo = new HashMap<>();

    private static final Map<UUID, Set<NpcKey>> spawnedNpcs = new HashMap<>();

    public static boolean wasSpawned(UUID playerId, String npcName, BlockPos pos) {
        return spawnedNpcs.getOrDefault(playerId, Collections.emptySet()).contains(new NpcKey(npcName, pos));
    }

    public static void markSpawned(UUID playerId, String npcName, BlockPos pos) {
        spawnedNpcs.computeIfAbsent(playerId, k -> new HashSet<>()).add(new NpcKey(npcName, pos));
    }

    public record NpcKey(String name, BlockPos pos) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NpcKey other)) return false;
            return Objects.equals(name, other.name) && Objects.equals(pos, other.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, pos);
        }
    }

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
        spawnedNpcs.remove(player); // ✅ Also clear the NPC spawn tracking
    }

    public static void clearSpawned(String npcName, BlockPos pos) {
        for (UUID playerId : spawnedNpcs.keySet()) {
            spawnedNpcs.get(playerId).remove(new NpcKey(npcName, pos));
        }
    }

    public static Set<String> getTalkedTo(UUID player) {
        return talkedTo.getOrDefault(player, Collections.emptySet());
    }

    public static Set<NpcKey> getSpawned(UUID player) {
        return spawnedNpcs.getOrDefault(player, Collections.emptySet());
    }

}