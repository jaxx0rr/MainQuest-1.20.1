package net.jaxx0rr.jxmainquest.story;

import com.google.gson.Gson;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
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
            // Get config directory (cross-platform safe)
            Path configPath = FMLPaths.CONFIGDIR.get().resolve("jxmainquest/stages.json");

            // If it doesn't exist, copy a default version from resources (optional, see below)
            if (Files.notExists(configPath)) {
                System.err.println("[jxmainquest] stages.json not found in config! Creating default...");
                copyDefaultStagesTo(configPath); // optional step
            }

            System.out.println("Looking for stages.json at: " + configPath.toAbsolutePath());

            // Load JSON from config file
            Reader reader = Files.newBufferedReader(configPath);
            Gson gson = new Gson();
            StoryStage[] loaded = gson.fromJson(reader, StoryStage[].class);
            stages = Arrays.asList(loaded);
            System.out.println("[jxmainquest] Loaded " + stages.size() + " story stages from config.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int reloadStages() {
        loadStages();
        return stages.size();
    }

    private static void copyDefaultStagesTo(Path destination) {
        try (InputStream in = StoryStageLoader.class.getResourceAsStream("/assets/jxmainquest/default_stages.json")) {
            if (in == null) {
                System.err.println("[jxmainquest] Default stages.json not found in assets!");
                return;
            }

            Files.createDirectories(destination.getParent());
            Files.copy(in, destination);
            System.out.println("[jxmainquest] Default stages.json copied to config.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


