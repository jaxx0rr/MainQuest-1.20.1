package net.jaxx0rr.jxmainquest.util;

import net.minecraft.client.Minecraft;

public class ClientTimerManager {
    private static long startTime = -1;
    private static boolean running = false;

    public static void start() {
        startTime = Minecraft.getInstance().level.getGameTime();
        running = true;
    }

    public static void stop() {
        running = false;
    }

    public static boolean isRunning() {
        return running;
    }

    public static String getFormattedTime() {
        if (!running || startTime < 0) return "";
        long currentTime = Minecraft.getInstance().level.getGameTime();
        long ticksElapsed = currentTime - startTime;
        int totalMillis = (int) (ticksElapsed * 50);
        int minutes = totalMillis / 60000;
        int seconds = (totalMillis / 1000) % 60;
        int millis = totalMillis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }
}