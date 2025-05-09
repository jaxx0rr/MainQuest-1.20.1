package net.jaxx0rr.jxmainquest.story;

import java.util.List;

public class StoryStage {
    public String text;
    public Trigger trigger;

    public class Trigger {
        public String type;

        // For location triggers
        public int x, y, z, radius;

        // For item triggers
        public String item;

        // For interaction triggers
        public String npc_name;
        public float dir;

        // ✅ For dialogue
        public List<StoryDialogueLine> dialogue;
    }
}
