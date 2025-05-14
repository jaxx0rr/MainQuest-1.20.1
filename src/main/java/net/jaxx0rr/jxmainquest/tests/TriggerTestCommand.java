package net.jaxx0rr.jxmainquest.tests;

import net.jaxx0rr.jxmainquest.ModEventHandler;
import net.jaxx0rr.jxmainquest.story.StoryStage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

public class TriggerTestCommand {

    public static int runAllTests(ServerPlayer player) {
        int passed = 0;
        int failed = 0;

        Map<Integer, ItemStack> savedInventory = new HashMap<>();
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (!stack.isEmpty()) savedInventory.put(i, stack.copy());
        }

        if (test(player, "I only - has item", true, new TriggerBuilder().item("minecraft:apple").build(), "minecraft:apple")) passed++; else failed++;
        if (test(player, "I only - missing item", false, new TriggerBuilder().item("minecraft:apple").build())) passed++; else failed++;

        if (test(player, "I+A - has I only", false, new TriggerBuilder().item("minecraft:apple").and("minecraft:bread").build(), "minecraft:apple")) passed++; else failed++;
        if (test(player, "I+A - has A only", false, new TriggerBuilder().item("minecraft:apple").and("minecraft:bread").build(), "minecraft:bread")) passed++; else failed++;
        if (test(player, "I+A - has both", true, new TriggerBuilder().item("minecraft:apple").and("minecraft:bread").build(), "minecraft:apple", "minecraft:bread")) passed++; else failed++;
        if (test(player, "I+A - missing both", false, new TriggerBuilder().item("minecraft:apple").and("minecraft:bread").build())) passed++; else failed++;

        if (test(player, "I+O - has item only", true, new TriggerBuilder().item("minecraft:apple").or("minecraft:carrot").build(), "minecraft:apple")) passed++; else failed++;
        if (test(player, "I+O - has O only", true, new TriggerBuilder().item("minecraft:apple").or("minecraft:carrot").build(), "minecraft:carrot")) passed++; else failed++;
        if (test(player, "I+O - has both", true, new TriggerBuilder().item("minecraft:apple").or("minecraft:carrot").build(), "minecraft:apple", "minecraft:carrot")) passed++; else failed++;
        if (test(player, "I+O - missing both", false, new TriggerBuilder().item("minecraft:apple").or("minecraft:carrot").build())) passed++; else failed++;

        if (test(player, "I+A+O - has I+A", true, new TriggerBuilder().item("minecraft:apple").and("minecraft:bread").or("minecraft:carrot").build(), "minecraft:apple", "minecraft:bread")) passed++; else failed++;
        if (test(player, "I+A+O - has O+A", true, new TriggerBuilder().item("minecraft:apple").and("minecraft:bread").or("minecraft:carrot").build(), "minecraft:carrot", "minecraft:bread")) passed++; else failed++;
        if (test(player, "I+A+O - I only", false, new TriggerBuilder().item("minecraft:apple").and("minecraft:bread").or("minecraft:carrot").build(), "minecraft:apple")) passed++; else failed++;
        if (test(player, "I+A+O - A only", false, new TriggerBuilder().item("minecraft:apple").and("minecraft:bread").or("minecraft:carrot").build(), "minecraft:bread")) passed++; else failed++;
        if (test(player, "I+A+O - missing all", false, new TriggerBuilder().item("minecraft:apple").and("minecraft:bread").or("minecraft:carrot").build())) passed++; else failed++;

        if (test(player, "I:2 - has 2 apples", true, new TriggerBuilder().item("minecraft:apple:2").build(), "minecraft:apple", "minecraft:apple")) passed++; else failed++;
        if (test(player, "I:2 - has 1 apple", false, new TriggerBuilder().item("minecraft:apple:2").build(), "minecraft:apple")) passed++; else failed++;


        player.getInventory().clearContent();
        savedInventory.forEach((slot, stack) -> player.getInventory().items.set(slot, stack));

        player.sendSystemMessage(Component.literal("§aTrigger tests complete. Passed: " + passed + ", Failed: " + failed));
        return 1;
    }

    private static boolean test(ServerPlayer player, String label, boolean expected, StoryStage.Trigger trigger, String... giveItems) {
        player.getInventory().clearContent();
        for (String id : giveItems) {
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
            if (item != null) {
                player.getInventory().add(new ItemStack(item));
            }
        }

        boolean result = ModEventHandler.triggerMatches(player, trigger, 0);
        player.sendSystemMessage(Component.literal("§7[" + label + "] §r" + (result == expected ? "§a[P]" : "§cF") + " exp: " + (expected ? "T" : "F") + ", act: " + (result ? "T" : "F")));
        return result == expected;
    }

    private static class TriggerBuilder {
        StoryStage.Trigger trigger;
        TriggerBuilder() { trigger = new StoryStage().new Trigger(); trigger.type = "item"; }
        TriggerBuilder item(String id) { trigger.item = id; return this; }
        TriggerBuilder and(String id) { trigger.anditem1 = id; return this; }
        TriggerBuilder or(String id) { trigger.oritem1 = id; return this; }
        public StoryStage.Trigger build() { return trigger; }
    }
}

