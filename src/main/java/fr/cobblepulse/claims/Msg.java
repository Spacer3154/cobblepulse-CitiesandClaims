package fr.cobblepulse.claims;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Messages du mod (prefixe commun, refus sans spam). */
public final class Msg {
    private static final Map<UUID, Map<String, Long>> LAST_DENY = new HashMap<>();

    private Msg() {}

    public static MutableComponent prefix() {
        return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("Territoires").withStyle(ChatFormatting.AQUA))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static MutableComponent text(String s, ChatFormatting color) {
        return prefix().append(Component.literal(s).withStyle(color));
    }

    public static void info(ServerPlayer p, String s) {
        if (p != null) p.sendSystemMessage(text(s, ChatFormatting.GRAY));
    }

    public static void ok(ServerPlayer p, String s) {
        if (p != null) p.sendSystemMessage(text(s, ChatFormatting.GREEN));
    }

    public static void err(ServerPlayer p, String s) {
        if (p != null) p.sendSystemMessage(text(s, ChatFormatting.RED));
    }

    public static void bar(ServerPlayer p, Component c) {
        if (p != null) p.displayClientMessage(c, true);
    }

    public static void ok(CommandSourceStack src, String s) {
        src.sendSuccess(() -> text(s, ChatFormatting.GREEN), false);
    }

    public static void info(CommandSourceStack src, String s) {
        src.sendSuccess(() -> text(s, ChatFormatting.GRAY), false);
    }

    public static void err(CommandSourceStack src, String s) {
        src.sendFailure(text(s, ChatFormatting.RED));
    }

    /** Refus affiche dans la barre d'action, au plus une fois toutes les 1,5 s par type d'action. */
    public static void deny(ServerPlayer p, String key, String s) {
        if (p == null || p.connection == null) return;
        long now = System.currentTimeMillis();
        Map<String, Long> m = LAST_DENY.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        Long last = m.get(key);
        if (last != null && now - last < 1500) return;
        m.put(key, now);
        p.displayClientMessage(Component.literal("✖ " + s).withStyle(ChatFormatting.RED), true);
    }

    public static void forget(UUID id) {
        LAST_DENY.remove(id);
    }
}
