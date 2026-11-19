package com.andy.abyss.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Ronova-style: X — tap: next skill, hold: the wheel, X + 1-8: that place on the wheel, X + scroll: flip skills.
 * R — use the selected skill (hold to charge). Inside a form the mouse buttons are its sub-abilities.
 * G — bodies screen, H — guide (unbound by default; the guide is also on the wheel).
 */
public final class Keys {
    public static final String CAT = "key.categories.abyss";
    public static final KeyMapping WHEEL = k("wheel", GLFW.GLFW_KEY_X);
    public static final KeyMapping CAST = k("cast", GLFW.GLFW_KEY_R);
    public static final KeyMapping ROSTER = k("roster", GLFW.GLFW_KEY_G);
    public static final KeyMapping GUIDE = k("guide", GLFW.GLFW_KEY_UNKNOWN);

    private static KeyMapping k(String n, int key) { return new KeyMapping("key.abyss." + n, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CAT); }

    public static void register(RegisterKeyMappingsEvent e) { for (KeyMapping m : new KeyMapping[]{WHEEL, CAST, ROSTER, GUIDE}) e.register(m); }

    public static boolean down(KeyMapping m) {
        long win = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        var key = m.getKey();
        if (key.getValue() == InputConstants.UNKNOWN.getValue()) return false;
        // the binding's own state (from key events) or the raw key: either one counts as held
        if (m.isDown()) return true;
        return key.getType() == InputConstants.Type.MOUSE ? GLFW.glfwGetMouseButton(win, key.getValue()) == 1 : InputConstants.isKeyDown(win, key.getValue());
    }

    public static String name(KeyMapping m) { return m.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT); }
    private Keys() {}
}
