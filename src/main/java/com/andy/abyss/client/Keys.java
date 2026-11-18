package com.andy.abyss.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** One-handed layout around WASD. All rebindable in Controls. */
public final class Keys {
    public static final String CAT = "key.categories.abyss";
    public static final KeyMapping WHEEL = k("wheel", GLFW.GLFW_KEY_V);
    public static final KeyMapping CAST = k("cast", GLFW.GLFW_KEY_R);
    public static final KeyMapping SPORES = k("spores", GLFW.GLFW_KEY_C);
    public static final KeyMapping LENS = k("lens", GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping COMBAT = k("combat", GLFW.GLFW_KEY_X);
    public static final KeyMapping NEXT = k("next", GLFW.GLFW_KEY_Z);
    public static final KeyMapping PREV = k("prev", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping MAIN = k("main", GLFW.GLFW_KEY_B);
    public static final KeyMapping ROSTER = k("roster", GLFW.GLFW_KEY_G);

    private static KeyMapping k(String n, int key) { return new KeyMapping("key.abyss." + n, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CAT); }

    public static void register(RegisterKeyMappingsEvent e) {
        for (KeyMapping m : new KeyMapping[]{WHEEL, CAST, SPORES, LENS, COMBAT, NEXT, PREV, MAIN, ROSTER}) e.register(m);
    }

    public static boolean down(KeyMapping m) {
        long win = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        var key = m.getKey();
        if (key.getValue() == InputConstants.UNKNOWN.getValue()) return false;
        return key.getType() == InputConstants.Type.MOUSE ? GLFW.glfwGetMouseButton(win, key.getValue()) == 1 : InputConstants.isKeyDown(win, key.getValue());
    }

    public static String name(KeyMapping m) { return m.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT); }
    private Keys() {}
}
