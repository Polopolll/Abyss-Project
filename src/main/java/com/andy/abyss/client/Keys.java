package com.andy.abyss.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Defaults avoid Hachimon's and Chronoweave's keys so all three can live in one pack. All rebindable. */
public final class Keys {
    public static final String CAT = "key.categories.abyss";
    public static final KeyMapping WHEEL = k("wheel", GLFW.GLFW_KEY_P);
    public static final KeyMapping CAST = k("cast", GLFW.GLFW_KEY_L);
    public static final KeyMapping SPORES = k("spores", GLFW.GLFW_KEY_O);
    public static final KeyMapping LENS = k("lens", GLFW.GLFW_KEY_CAPS_LOCK);
    public static final KeyMapping PREV = k("prev", GLFW.GLFW_KEY_LEFT_BRACKET);
    public static final KeyMapping NEXT = k("next", GLFW.GLFW_KEY_RIGHT_BRACKET);
    public static final KeyMapping MAIN = k("main", GLFW.GLFW_KEY_BACKSLASH);
    public static final KeyMapping ROSTER = k("roster", GLFW.GLFW_KEY_APOSTROPHE);

    private static KeyMapping k(String n, int key) { return new KeyMapping("key.abyss." + n, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CAT); }

    public static void register(RegisterKeyMappingsEvent e) {
        for (KeyMapping m : new KeyMapping[]{WHEEL, CAST, SPORES, LENS, PREV, NEXT, MAIN, ROSTER}) e.register(m);
    }

    /** Physical state of a mapping (works while a screen is open). */
    public static boolean down(KeyMapping m) {
        long win = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        var key = m.getKey();
        if (key.getValue() == InputConstants.UNKNOWN.getValue()) return false;
        return key.getType() == InputConstants.Type.MOUSE ? GLFW.glfwGetMouseButton(win, key.getValue()) == 1 : InputConstants.isKeyDown(win, key.getValue());
    }
    private Keys() {}
}
