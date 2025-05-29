package net.jaxx0rr.jxmainquest.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.*;

public class EnemySpawnTracker {

    private static final Map<UUID, UUID> mobToPlayer = new HashMap<>();
    private static final Map<UUID, Long> expiryTime = new HashMap<>();
    private static final long TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    private static final Map<UUID, Set<UUID>> playerToMobs = new HashMap<>();

    public static void associateMobWithPlayer(Entity mob, ServerPlayer player) {
        UUID mobId = mob.getUUID();
        UUID playerId = player.getUUID();

        mobToPlayer.put(mobId, playerId);
        expiryTime.put(mobId, System.currentTimeMillis());

        playerToMobs.computeIfAbsent(playerId, k -> new HashSet<>()).add(mobId);
    }

    public static UUID getPlayerForMob(UUID mobId) {
        return mobToPlayer.get(mobId);
    }

    public static void cleanupOldEntries() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = expiryTime.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now - entry.getValue() > TIMEOUT_MS) {
                UUID mobId = entry.getKey();
                mobToPlayer.remove(mobId);
                it.remove();
            }
        }
    }

    public static void clearMob(UUID mobId) {
        mobToPlayer.remove(mobId);
        expiryTime.remove(mobId);
    }


    public static void clearForPlayer(UUID playerId) {
        Set<UUID> mobIds = playerToMobs.remove(playerId);
        if (mobIds != null) {
            for (UUID mobId : mobIds) {
                mobToPlayer.remove(mobId);
                expiryTime.remove(mobId);
            }
        }
    }

}
