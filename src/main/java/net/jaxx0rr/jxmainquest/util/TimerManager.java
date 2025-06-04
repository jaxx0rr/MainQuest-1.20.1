package net.jaxx0rr.jxmainquest.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TimerManager {
    private static final Map<UUID, Map<String, Long>> playerTimers = new HashMap<>();

    public static void startTimer(ServerPlayer player, String timerName) {
        playerTimers.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .put(timerName, System.currentTimeMillis());
    }

    public static void stopTimer(ServerPlayer player, String timerName) {
        Map<String, Long> timers = playerTimers.get(player.getUUID());
        if (timers == null || !timers.containsKey(timerName)) return;

        long start = timers.remove(timerName);
        long duration = System.currentTimeMillis() - start;

        String formatted = formatDuration(duration);
        Component message = Component.literal("⏱ " + player.getName().getString() + " finished " + timerName + " in " + formatted);
        for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(message);
        }
    }

    private static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long remSec = seconds % 60;
        long remMillis = millis % 1000 / 10;
        return String.format("%d:%02d.%02d", minutes, remSec, remMillis);
    }

    public static void clear(ServerPlayer player) {
        playerTimers.remove(player.getUUID());
    }
}