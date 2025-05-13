package net.jaxx0rr.jxmainquest.client;

import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.jaxx0rr.jxmainquest.story.StoryStageLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static net.jaxx0rr.jxmainquest.Main.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class HudOverlay {


    @SubscribeEvent
    public static void renderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        GuiGraphics guiGraphics = event.getGuiGraphics();
        Font font = mc.font;

        //player.getCapability(StoryProgressProvider.STORY).ifPresent(progress -> {
        //int stageIndex = progress.getCurrentStage();
        //if (stageIndex >= StoryStageLoader.stages.size()) return;

        int stageIndex = ClientStoryState.currentStage;
        if (stageIndex < 0 || stageIndex >= StoryStageLoader.stages.size()) return;
        StoryStage stage = StoryStageLoader.stages.get(stageIndex);
        String text = stage.text;

        if (stage.trigger != null && "location".equals(stage.trigger.type) || "waypoint".equals(stage.trigger.type) || "locationitem".equals(stage.trigger.type) || "interaction".equals(stage.trigger.type)) {

            Vec3 worldPos = new Vec3(stage.trigger.x + 0.5, stage.trigger.y + 1.5, stage.trigger.z + 0.5);
            Vec2 screenPos = projectToScreen(worldPos, event.getPartialTick());

            if (screenPos != null) {
                guiGraphics.drawString(font, "[ ]", (int)screenPos.x, (int)screenPos.y, 0xFFFFFF, true);
            }

            BlockPos target = new BlockPos(stage.trigger.x, stage.trigger.y, stage.trigger.z);


            double dx = target.getX() + 0.5 - player.getX();
            double dz = target.getZ() + 0.5 - player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            // Get yaw as radians (convert from Minecraft's system: 0 = south)
            float yawDegrees = player.getYRot();
            double yawRadians = Math.toRadians(-yawDegrees);

            // Player facing vector
            double facingX = Math.sin(yawRadians);
            double facingZ = Math.cos(yawRadians);

            // Normalize target direction
            double dirX = dx / dist;
            double dirZ = dz / dist;

            // Dot product = cosine of angle between vectors
            double dot = dirX * facingX + dirZ * facingZ;
            double det = dirX * facingZ - dirZ * facingX;

            double angle = Math.toDegrees(Math.atan2(det, dot));
            angle = (angle + 360) % 360; // Normalize

            String arrow = switch ((int) ((angle + 22.5) / 45) % 8) {
                case 0 -> "↑"; // ahead
                case 1 -> "↖";
                case 2 -> "←"; // left
                case 3 -> "↙";
                case 4 -> "↓"; // behind
                case 5 -> "↘";
                case 6 -> "→"; // right
                case 7 -> "↗";
                default -> "?";
            };

            text += String.format(" §7(%.1fm %s)", dist, arrow);
        }

        int padding = 2;
        float scale = 0.8f;

        int textWidth = font.width(text);
        int textHeight = font.lineHeight;

        int boxWidth = textWidth + padding * 2;
        int boxHeight = textHeight + padding * 2;

        int x = 10;
        int y = 10;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);

        int sx = (int) (x / scale);
        int sy = (int) (y / scale);

        // Border and background
        guiGraphics.fill(sx - 1, sy - 1, sx + boxWidth + 1, sy + boxHeight + 1, 0xFF000000); // thin black border
        guiGraphics.fill(sx, sy, sx + boxWidth, sy + boxHeight, 0xFFFFFFFF); // white background

        // Text
        guiGraphics.drawString(font, text, sx + padding, sy + padding, 0x000000, false);

        guiGraphics.pose().popPose();

    }


    private static Vec2 projectToScreen_(Vec3 worldPos, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();

        Vec3 camPos = camera.getPosition();
        Vec3 rel = worldPos.subtract(camPos);

        float yaw = camera.getYRot();
        float pitch = camera.getXRot();

        // ❌ Invert both yaw and pitch
        rel = rel.yRot((float) Math.toRadians(-yaw));
        rel = rel.xRot((float) Math.toRadians(pitch));

        double z = rel.z;
        if (z <= 0.1) return null;

        double fov = mc.options.fov().get();
        double scale = mc.getWindow().getGuiScaledHeight() / (2.0 * Math.tan(Math.toRadians(fov / 2)));

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // ⬅ no inversion here; the rotation flips already handle it
        double screenX = rel.x * scale / z + width / 2.0;
        double screenY = -rel.y * scale / z + height / 2.0;

        return new Vec2((float) screenX, (float) screenY);
    }

    private static Vec2 projectToScreen_2(Vec3 worldPos, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();

        Vec3 camPos = camera.getPosition();
        Vec3 rel = worldPos.subtract(camPos);

        float yaw = camera.getYRot();
        float pitch = camera.getXRot();

        // ✅ Keep pitch inversion
        rel = rel.yRot((float) Math.toRadians(-yaw));   // DO NOT change this
        rel = rel.xRot((float) Math.toRadians(pitch));  // DO NOT change this

        double z = rel.z;
        if (z <= 0.1) return null;

        double fov = mc.options.fov().get();
        double scale = mc.getWindow().getGuiScaledHeight() / (2.0 * Math.tan(Math.toRadians(fov / 2)));

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // ✅ Flip X only — fix side-to-side without affecting Y or camera
        double screenX = -rel.x * scale / z + width / 2.0;
        double screenY = -rel.y * scale / z + height / 2.0;

        return new Vec2((float) screenX, (float) screenY);
    }

    private static Vec2 projectToScreen(Vec3 worldPos, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();

        Vec3 camPos = camera.getPosition();
        Vec3 rel = worldPos.subtract(camPos);

        float yaw = camera.getYRot();
        float pitch = camera.getXRot();

        // ✅ Apply yaw and pitch consistently — both unflipped
        rel = rel.yRot((float) Math.toRadians(yaw));
        rel = rel.xRot((float) Math.toRadians(pitch));

        double z = rel.z;
        if (z <= 0.1) return null;

        double fov = mc.options.fov().get();
        double scale = mc.getWindow().getGuiScaledHeight() / (2.0 * Math.tan(Math.toRadians(fov / 2)));

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        double screenX = -rel.x * scale / z + width / 2.0;
        double screenY = -rel.y * scale / z + height / 2.0;

        return new Vec2((float) screenX, (float) screenY);
    }



}
