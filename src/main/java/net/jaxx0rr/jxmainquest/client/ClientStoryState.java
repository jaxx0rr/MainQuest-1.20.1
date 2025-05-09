package net.jaxx0rr.jxmainquest.client;

import net.jaxx0rr.jxmainquest.story.StoryProgress;

public class ClientStoryState {
    public static int currentStage = -1;

    public static void setStage(int newStage) {
        if (newStage == currentStage) return;

        int oldStage = currentStage;
        currentStage = newStage;

        StoryProgress.showStageTransition(oldStage, newStage);
    }
}
