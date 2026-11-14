package dev.itemwhispers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Item Whispers (client-only). The tool in your main hand occasionally "speaks" as a short line shown in the
 * overlay text above the hotbar:
 *   - GREETING: when you pick it up
 *   - IDLE:     every 30-60 seconds while you keep holding it
 *   - EXCITED:  when it gains a new enchantment while you hold it
 *   - TIRED:    when its durability drops to 10% or less (and sometimes when under 25%)
 * Lines depend on the tool type (pickaxe/axe/shovel/hoe/sword) and its enchantments.
 *
 * No mixins, no rendering changes: it only shows text, so it coexists with Enchant Tag, Silk & Fortune Glow and
 * the Enchantment Outlines resource pack.
 */
public class ItemWhispersClient implements ClientModInitializer {

    private enum Mood { GREETING, IDLE, TIRED, EXCITED }

    private enum Kind { PICKAXE, AXE, SHOVEL, HOE, SWORD }

    private static final Random RANDOM = new Random();

    /** Minimum ticks between any two whispers (20 ticks = 1 second). */
    private static final int MIN_GAP_TICKS = 100;

    private static Item lastItem;
    private static ItemEnchantments lastEnchants = ItemEnchantments.EMPTY;
    private static int idleTimer = 900;
    private static int sinceLast = 1000;
    private static boolean lowWarned;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(ItemWhispersClient::tick);
    }

    // ------------------------------------------------------------------ main loop

    private static void tick(Minecraft mc) {
        Player player = mc.player;
        if (player == null || mc.level == null) {
            lastItem = null;
            lastEnchants = ItemEnchantments.EMPTY;
            return;
        }
        sinceLast++;

        ItemStack held = player.getMainHandItem();
        Kind kind = kindOf(held);
        if (kind == null) {
            lastItem = null;
            lastEnchants = ItemEnchantments.EMPTY;
            return;
        }

        ItemEnchantments ench = held.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        double left = durabilityLeft(held);

        // Picked up a different kind of tool.
        if (lastItem != held.getItem()) {
            lastItem = held.getItem();
            lastEnchants = ench;
            lowWarned = left <= 0.10;
            resetIdle();
            whisper(mc, held, kind, ench, Mood.GREETING);
            return;
        }

        // Gained an enchantment while held.
        if (ench.keySet().size() > lastEnchants.keySet().size()) {
            lastEnchants = ench;
            whisper(mc, held, kind, ench, Mood.EXCITED);
            return;
        }
        lastEnchants = ench;

        // Nearly broken.
        if (left <= 0.10) {
            if (!lowWarned) {
                lowWarned = true;
                whisper(mc, held, kind, ench, Mood.TIRED);
                return;
            }
        } else if (left > 0.25) {
            lowWarned = false; // repaired
        }

        // Idle chatter (only when no menu is open).
        if (--idleTimer <= 0) {
            resetIdle();
            if (mc.screen == null) {
                boolean worn = left <= 0.25 && RANDOM.nextBoolean();
                whisper(mc, held, kind, ench, worn ? Mood.TIRED : Mood.IDLE);
            }
        }
    }

    private static void resetIdle() {
        idleTimer = 600 + RANDOM.nextInt(600); // 30-60 seconds
    }

    // ------------------------------------------------------------------ helpers

    private static Kind kindOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.is(ItemTags.PICKAXES)) return Kind.PICKAXE;
        if (stack.is(ItemTags.AXES)) return Kind.AXE;
        if (stack.is(ItemTags.SHOVELS)) return Kind.SHOVEL;
        if (stack.is(ItemTags.HOES)) return Kind.HOE;
        if (stack.is(ItemTags.SWORDS)) return Kind.SWORD;
        return null;
    }

    private static double durabilityLeft(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return 1.0;
        return 1.0 - (double) stack.getDamageValue() / (double) stack.getMaxDamage();
    }

    private static void whisper(Minecraft mc, ItemStack held, Kind kind, ItemEnchantments ench, Mood mood) {
        if (sinceLast < MIN_GAP_TICKS) return;
        sinceLast = 0;

        String line = pick(kind, ench, mood);
        MutableComponent message = Component.empty()
                .append(held.getHoverName().copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": \u201C" + line + "\u201D")
                        .withStyle(ChatFormatting.ITALIC, color(mood)));
        mc.gui.setOverlayMessage(message, false);
    }

    private static ChatFormatting color(Mood mood) {
        return switch (mood) {
            case GREETING -> ChatFormatting.YELLOW;
            case IDLE -> ChatFormatting.GRAY;
            case TIRED -> ChatFormatting.RED;
            case EXCITED -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    // ------------------------------------------------------------------ the lines

    private static String pick(Kind kind, ItemEnchantments ench, Mood mood) {
        List<String> pool = new ArrayList<>();
        switch (mood) {
            case GREETING -> {
                pool.addAll(GREETINGS);
                pool.addAll(forKind(kind));
                addEnchantLines(pool, ench);
            }
            case IDLE -> {
                pool.addAll(forKind(kind));
                addEnchantLines(pool, ench);
            }
            case TIRED -> pool.addAll(TIRED);
            case EXCITED -> pool.addAll(EXCITED);
        }
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    private static List<String> forKind(Kind kind) {
        return switch (kind) {
            case PICKAXE -> PICKAXE;
            case AXE -> AXE;
            case SHOVEL -> SHOVEL;
            case HOE -> HOE;
            case SWORD -> SWORD;
        };
    }

    private static void addEnchantLines(List<String> pool, ItemEnchantments ench) {
        for (Holder<Enchantment> h : ench.keySet()) {
            if (h.is(Enchantments.SILK_TOUCH)) pool.addAll(SILK);
            if (h.is(Enchantments.FORTUNE)) pool.addAll(FORTUNE);
            if (h.is(Enchantments.EFFICIENCY)) pool.addAll(EFFICIENCY);
            if (h.is(Enchantments.UNBREAKING)) pool.addAll(UNBREAKING);
            if (h.is(Enchantments.MENDING)) pool.addAll(MENDING);
            if (h.is(Enchantments.SHARPNESS)) pool.addAll(SHARPNESS);
        }
    }

    private static final List<String> GREETINGS = List.of(
            "Oh, it's you! Ready to work?",
            "Good to be back in your hand.",
            "Lead the way.",
            "I was getting bored in there.");

    private static final List<String> PICKAXE = List.of(
            "Another day, another vein.",
            "Do you smell diamonds? I smell diamonds.",
            "Dig, dig, dig...",
            "I think there's something shiny nearby.",
            "Just point me at some stone.");

    private static final List<String> AXE = List.of(
            "Timber!",
            "The trees fear me.",
            "Forests are just firewood waiting to happen.",
            "Chop first, ask questions never.");

    private static final List<String> SHOVEL = List.of(
            "Dirt is honest work.",
            "Just a little more digging...",
            "Sand, gravel, whatever. I'm not picky.",
            "Hole-digging is a respectable career.");

    private static final List<String> HOE = List.of(
            "Farming is a quiet life.",
            "Are the crops ready yet?",
            "Till, plant, repeat. Lovely.",
            "I do love a neat row of wheat.");

    private static final List<String> SWORD = List.of(
            "Any mobs about? I'm bored.",
            "Keep me sharp, please.",
            "I'm ready when you are.",
            "Something creepy is out there. I can feel it.");

    private static final List<String> SILK = List.of(
            "Handle me gently. I take blocks whole.",
            "Silk Touch: nothing breaks on my watch.",
            "Careful with that spawner, I could carry it home.");

    private static final List<String> FORTUNE = List.of(
            "Fortune smiles on us today.",
            "Let's make those ores multiply!",
            "I feel lucky. Do you feel lucky?");

    private static final List<String> EFFICIENCY = List.of(
            "Faster! I can go faster!",
            "Stone barely slows me down.");

    private static final List<String> UNBREAKING = List.of(
            "I'm built to last.",
            "Plenty of work left in me yet.");

    private static final List<String> MENDING = List.of(
            "Mending: I'll patch myself up, don't worry.",
            "Feed me some XP and I'm good as new.");

    private static final List<String> SHARPNESS = List.of(
            "Sharp enough to part the night.",
            "Careful, I cut deeper than I look.");

    private static final List<String> TIRED = List.of(
            "I'm getting tired...",
            "I don't have much left in me...",
            "Please, I need repairs!",
            "One more swing and I might snap.",
            "An anvil would be lovely right about now.");

    private static final List<String> EXCITED = List.of(
            "Ooh, something new! I feel stronger!",
            "Did you just enchant me? Thank you!",
            "I feel... shinier!",
            "Now we're talking!");
}
