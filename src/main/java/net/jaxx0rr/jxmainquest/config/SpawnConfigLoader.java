package net.jaxx0rr.jxmainquest.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public class SpawnConfigLoader {
    private static BlockPos defaultSpawn = new BlockPos(0, 64, 0);
    private static ResourceKey<Level> spawnDimension = Level.OVERWORLD;

    public static void load() {
        try {
            Path configPath = FMLPaths.CONFIGDIR.get().resolve("jxmainquest/spawn_config.json");

            if (Files.notExists(configPath)) {
                System.err.println("[jxmainquest] spawn_config.json not found! Creating default...");
                copyDefaultSpawnTo(configPath);
            }

            Reader reader = Files.newBufferedReader(configPath);
            Gson gson = new Gson();
            JsonObject obj = gson.fromJson(reader, JsonObject.class);

            int x = obj.has("x") ? obj.get("x").getAsInt() : 0;
            int y = obj.has("y") ? obj.get("y").getAsInt() : 64;
            int z = obj.has("z") ? obj.get("z").getAsInt() : 0;
            defaultSpawn = new BlockPos(x, y, z);

            if (obj.has("dimension")) {
                String dimStr = obj.get("dimension").getAsString();
                try {
                    ResourceLocation dimId = ResourceLocation.tryParse(dimStr);
                    if (dimId != null) {
                        spawnDimension = ResourceKey.create(Registries.DIMENSION, dimId);
                    }
                } catch (Exception e) {
                    System.err.println("[jxmainquest] Invalid dimension ID in spawn_config.json: " + dimStr);
                }
            }

            System.out.println("[jxmainquest] Loaded default spawn: " + defaultSpawn + " in " + spawnDimension.location());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void copyDefaultSpawnTo(Path path) {
        try {
            Files.createDirectories(path.getParent());

            JsonObject obj = new JsonObject();
            obj.addProperty("x", 0);
            obj.addProperty("y", 64);
            obj.addProperty("z", 0);
            obj.addProperty("dimension", "minecraft:overworld");

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(path))) {
                gson.toJson(obj, writer);
            }

            System.out.println("[jxmainquest] Default spawn_config.json created.");
        } catch (Exception e) {
            System.err.println("[jxmainquest] Failed to create default spawn_config.json");
            e.printStackTrace();
        }
    }

    public static BlockPos getDefaultSpawn() {
        return defaultSpawn;
    }

    public static ResourceKey<Level> getSpawnDimension() {
        return spawnDimension;
    }
}
