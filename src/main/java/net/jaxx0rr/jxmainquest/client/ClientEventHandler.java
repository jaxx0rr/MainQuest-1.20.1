package net.jaxx0rr.jxmainquest.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static net.jaxx0rr.jxmainquest.Main.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        Screen screen = event.getNewScreen();

        if (screen instanceof MerchantScreen && ClientDialogueManager.isActive()) {
            System.out.println("[Client] Blocking trade screen because dialogue is active");
            event.setCanceled(true);
        }
    }
}
