package net.jaxx0rr.jxmainquest.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static net.jaxx0rr.jxmainquest.Main.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class DialogueInputHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        // ⏱ Run pending dialogue line if delay finished and not waiting for input
        ClientDialogueManager.onTick();

        // ⌨️ Handle key presses if expecting input
        if (ClientDialogueManager.isActive() && ClientDialogueManager.isAwaitingChoice()) {
            for (int i = 0; i < 9; i++) {
                if (mc.options.keyHotbarSlots[i].isDown()) {
                    ClientDialogueManager.selectChoice(i);
                    break;
                }
            }
        }
    }


}
