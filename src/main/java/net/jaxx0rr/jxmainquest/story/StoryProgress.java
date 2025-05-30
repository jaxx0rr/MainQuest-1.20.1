package net.jaxx0rr.jxmainquest.story;

import net.jaxx0rr.jxmainquest.config.StoryStageLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.HashSet;
import java.util.Set;

public class StoryProgress implements INBTSerializable<CompoundTag> {
    private int currentStage = 0;

    public int getCurrentStage() {
        return currentStage;
    }

    public void setStage(int newStage) {
        if (newStage == currentStage) return;

        int oldStage = currentStage;
        currentStage = newStage;

        // Optional: put debug/log here
        // System.out.println("[StoryProgress] Stage changed from " + oldStage + " to " + newStage);
    }

    public void advanceStage() {
        currentStage++;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Stage", currentStage);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        currentStage = tag.getInt("Stage");
    }

    public static void showStageTransition(int oldStage, int newStage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (oldStage >= 0 && oldStage < StoryStageLoader.stages.size()) {
            StoryStage prevStage = StoryStageLoader.stages.get(oldStage);

            if ("waypoint".equals(prevStage.trigger.type)) {
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f); // or any other soft cue
                return;
            }

            String oldText = StoryStageLoader.stages.get(oldStage).text;

            if (newStage < StoryStageLoader.stages.size()) {
                String newText = StoryStageLoader.stages.get(newStage).text;
                mc.player.sendSystemMessage(Component.literal("§aQuest Complete: §f" + oldText));
                mc.player.sendSystemMessage(Component.literal("§eNew Quest: §f" + newText));
            } else {
                mc.player.sendSystemMessage(Component.literal("§aQuest Complete: §f" + oldText));
                mc.player.sendSystemMessage(Component.literal("§6You have completed all quests!"));
            }

            mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    private final Set<Integer> completedEnemyTriggers = new HashSet<>();

    public boolean hasKilledForStage(int stageIndex) {
        return completedEnemyTriggers.contains(stageIndex);
    }

    public void markKillForStage(int stageIndex) {
        completedEnemyTriggers.add(stageIndex);
    }

    public void resetKillForStage(int stageIndex) {
        completedEnemyTriggers.remove(stageIndex);
    }

}
