package net.jaxx0rr.jxmainquest.util; // adjust package as needed

import net.jaxx0rr.jxmainquest.Main;
import net.jaxx0rr.jxmainquest.ModEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Main.MODID, value = Dist.DEDICATED_SERVER) // or remove "value" to support all sides
public class SpawnRetryTracker {

    private static final Map<Integer, BlockPos> pendingSpawns = new HashMap<>();

    public static void mark(int stage, BlockPos pos) {
        pendingSpawns.put(stage, pos);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof ServerPlayer player)) return;
        if (event.phase != TickEvent.Phase.END) return;

        ServerLevel level = player.serverLevel();

        Iterator<Map.Entry<Integer, BlockPos>> it = pendingSpawns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, BlockPos> entry = it.next();
            BlockPos pos = entry.getValue();

            if (level.hasChunkAt(pos)) {
                System.out.println("[jxmainquest] Retrying spawn for stage " + entry.getKey());
                ModEventHandler.onStageStart(player, entry.getKey());
                it.remove();
            }
        }
    }
}
