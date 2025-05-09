package net.jaxx0rr.jxmainquest.story;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.common.util.INBTSerializable;

public class StoryProgress implements INBTSerializable<CompoundTag> {
    public static int currentStage = 0;

    public int getCurrentStage() { return currentStage; }

    public void setStage(int newStage) {
        if (newStage == currentStage) return;

        int oldStage = currentStage;
        currentStage = newStage;

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

}
