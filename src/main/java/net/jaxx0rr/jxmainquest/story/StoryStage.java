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

        public String profession;

        // ✅ For dialogue
        public List<StoryDialogueLine> dialogue;

        public String reward_item;  // e.g. "minecraft:emerald"
        public int reward_amount;   // how many to give
        public int reward_xp; // optional, default 0

        public String enemy;           // e.g. "minecraft:zombie"
        public String enemy_name;      // optional: custom name match
        public double enemy_radius;    // optional: must be killed near a specific location
        public Boolean spawn_enemy;

        public String anditem1, anditem2, anditem3, anditem4, anditem5, anditem6, anditem7, anditem8, anditem9;
        public String oritem1,  oritem2,  oritem3,  oritem4,  oritem5,  oritem6,  oritem7,  oritem8,  oritem9;

        public String entity_type;
    }
}
