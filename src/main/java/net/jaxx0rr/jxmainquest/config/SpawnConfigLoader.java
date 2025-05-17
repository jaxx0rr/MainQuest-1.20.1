package net.jaxx0rr.jxmainquest.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public class SpawnConfigLoader {
    private static BlockPos initialSpawn = new BlockPos(0, 64, 0);
    private static ResourceKey<Level> initialDimension = Level.OVERWORLD;

    private static BlockPos respawnPoint = null;
    private static ResourceKey<Level> respawnDimension = Level.OVERWORLD;

    public static void load() {
        try {
            Path configPath = FMLPaths.CONFIGDIR.get().resolve("jxmainquest/spawn_config.json");

            if (Files.notExists(configPath)) {
                System.out.println("[jxmainquest] spawn_config.json not found — skipping custom spawn setup.");
                return;
            }

            Reader reader = Files.newBufferedReader(configPath);
            JsonObject obj = new Gson().fromJson(reader, JsonObject.class);

            // Load initial_spawn
            if (obj.has("initial_spawn")) {
                JsonObject init = obj.getAsJsonObject("initial_spawn");
                initialSpawn = new BlockPos(
                        init.get("x").getAsInt(),
                        init.get("y").getAsInt(),
                        init.get("z").getAsInt()
                );
                if (init.has("dimension")) {
                    String dimStr = init.get("dimension").getAsString();
                    initialDimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimStr));
                }
            }

            // Load respawn_point (optional)
            if (obj.has("respawn_point")) {
                JsonObject respawn = obj.getAsJsonObject("respawn_point");
                respawnPoint = new BlockPos(
                        respawn.get("x").getAsInt(),
                        respawn.get("y").getAsInt(),
                        respawn.get("z").getAsInt()
                );
                if (respawn.has("dimension")) {
                    String dimStr = respawn.get("dimension").getAsString();
                    respawnDimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimStr));
                }
            }

            System.out.println("[jxmainquest] Loaded spawn_config.json with custom spawn settings.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static BlockPos getInitialSpawn() {
        return initialSpawn;
    }

    public static ResourceKey<Level> getInitialDimension() {
        return initialDimension;
    }

    public static BlockPos getRespawnPoint() {
        return respawnPoint;
    }

    public static ResourceKey<Level> getRespawnDimension() {
        return respawnDimension;
    }

}
