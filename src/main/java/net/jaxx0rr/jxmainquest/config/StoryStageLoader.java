package net.jaxx0rr.jxmainquest.config;

import com.google.gson.Gson;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StoryStageLoader {
    public static List<StoryStage> stages = new ArrayList<>();

    public static void loadStages() {
        try {
            Path configPath = FMLPaths.CONFIGDIR.get().resolve("jxmainquest/stages.json");

            if (Files.notExists(configPath)) {
                System.err.println("[jxmainquest] stages.json not found in config! Skipping load.");
                stages = new ArrayList<>();
                return;
            }

            Reader reader = Files.newBufferedReader(configPath);
            Gson gson = new Gson();
            StoryStage[] loaded = gson.fromJson(reader, StoryStage[].class);

            if (loaded == null || loaded.length == 0) {
                System.err.println("[jxmainquest] ❌ Failed to load story json — file is empty or invalid.");
                stages = new ArrayList<>();
                return;
            }

            for (int i = 0; i < loaded.length; i++) {
                StoryStage stage = loaded[i];
                if (stage == null || stage.trigger == null) {
                    System.err.println("[jxmainquest] ❌ Failed to load story json — malformed entry at index " + i);
                    stages = new ArrayList<>();
                    return;
                }
            }

            stages = Arrays.asList(loaded);
            System.out.println("[jxmainquest] Loaded " + stages.size() + " story stages.");
        } catch (Exception e) {
            System.err.println("[jxmainquest] Failed to load story json — exception thrown.");
            e.printStackTrace();
            stages = new ArrayList<>();
        }
    }

    public static int reloadStages() {
        loadStages();
        return stages.size();
    }

}


