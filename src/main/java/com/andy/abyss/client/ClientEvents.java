package com.andy.abyss.client;

import com.andy.abyss.Abyss;
import com.andy.abyss.Config;
import com.andy.abyss.client.gui.*;
import com.andy.abyss.client.render.FxManager;
import com.andy.abyss.client.render.MoveFx;
import com.andy.abyss.data.Ability;
import com.andy.abyss.data.Payload;
import com.andy.abyss.net.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Input (Ronova-style), ticking, world rendering, camera, domain sky and fog.
 * X: tap = next skill, hold = wheel, + 1-8 = that place, + scroll = flip. R: use / charge. In a form: LMB, RMB, mouse 4,
 * mouse 5 are its sub-abilities (vanilla attack/use are blocked while a form is open).
 */
@Mod.EventBusSubscriber(modid = Abyss.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean castHeld, jumpWas, xHeld, xUsed;
    private static long xDownAt;
    /** Ticks each mouse button (0 LMB, 1 RMB, 3 mouse 4, 4 mouse 5) has been held in a form, -1 = up. */
    private static final int[] HELD = {-1, -1, -1, -1, -1};
    private static final boolean[] HOLD_SENT = new boolean[5];
    public static Entity looked;
    public static float insideDomain;
    private static net.minecraft.client.CloudStatus savedClouds;
    public static boolean openDomainInside;

    private static boolean formInput() {
        var mc = Minecraft.getInstance();
        // while peeking through the abyss's eye you are a spectator: the Authority buttons must still reach the server, or the eye is a one-way trip
        return mc.screen == null && mc.player != null && ClientState.awakened() && ClientState.form() >= 0
                && (!mc.player.isSpectator() || ClientState.form() == com.andy.abyss.data.Ability.AUTHORITY.ordinal());
    }

    private static int flags() {
        var p = Minecraft.getInstance().player;
        int f = 0;
        if (p.isShiftKeyDown()) f |= C2SInput.SHIFT;
        if (p.isSprinting()) f |= C2SInput.SPRINT;
        if (!p.onGround()) f |= C2SInput.AIR;
        if (!p.onGround() && p.getDeltaMovement().y < 0) f |= C2SInput.FALLING;
        return f;
    }

    private static int moveInput() {
        var o = Minecraft.getInstance().options;
        return (o.keyUp.isDown() ? 1 : 0) | (o.keyDown.isDown() ? 2 : 0) | (o.keyLeft.isDown() ? 4 : 0) | (o.keyRight.isDown() ? 8 : 0);
    }

    @SubscribeEvent
    public static void renderLiving(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> e) { Disintegrate.onRender(e); }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { if (e.phase == TickEvent.Phase.END) { ClientState.reset(); SporeClient.ALL.clear(); FxManager.clear(); MoveFx.clear(); } return; }
        if (e.phase == TickEvent.Phase.START) {
            // X + 1-8: the number keys belong to the wheel while X is held
            if (xHeld && ClientState.awakened()) {
                List<Ability> order = WheelOrder.get();
                for (int i = 0; i < 9; i++) while (mc.options.keyHotbarSlots[i].consumeClick()) {
                    if (i < order.size()) slot(order.get(i));
                    xUsed = true;
                }
            }
            return;
        }
        ClientState.ticks++;
        FxManager.tick();
        MoveFx.tick();
        SporeClient.tick();
        boolean awake = ClientState.awakened();
        boolean free = mc.screen == null;
        var clash = ClientState.myClash();
        boolean jump = mc.options.keyJump.isDown();
        if (clash != null && clash.you() == 1 && jump && !jumpWas) Net.toServer(new C2SAction(C2SAction.CLASH_PUSH, 0));
        jumpWas = jump;
        if (awake && free) {
            // X: tap = next, hold = wheel
            boolean x = Keys.down(Keys.WHEEL);
            if (x && !xHeld) { xHeld = true; xUsed = false; xDownAt = ClientState.ticks; }
            if (xHeld && x && !xUsed && ClientState.ticks - xDownAt >= 5) { xUsed = true; mc.setScreen(new WheelScreen()); }
            if (xHeld && !x) {
                if (!xUsed) select(WheelOrder.next(Ability.byIndex(ClientState.selected()), 1), true);
                xHeld = false;
            }
            while (Keys.WHEEL.consumeClick()) { }
            while (Keys.ROSTER.consumeClick()) mc.setScreen(new RosterScreen());
            while (Keys.GUIDE.consumeClick()) mc.setScreen(new GuideScreen(null));
            // R: use / charge
            boolean cast = Keys.CAST.isDown();
            if (cast && !castHeld) {
                Ability a = Ability.byIndex(ClientState.selected());
                if (a == Ability.GUIDE) mc.setScreen(new GuideScreen(null));
                else Net.toServer(new C2SAction(C2SAction.CAST_START, 0));
            }
            if (!cast && castHeld) Net.toServer(new C2SAction(C2SAction.CAST_RELEASE, ClientState.domainVariant));
            castHeld = cast;
            while (Keys.CAST.consumeClick()) { }
            // form buttons held: hold thresholds and painting
            if (formInput()) {
                for (int b = 0; b < 5; b++) if (HELD[b] >= 0) {
                    HELD[b]++;
                    if (HELD[b] == 8 && !HOLD_SENT[b]) { HOLD_SENT[b] = true; send(b, C2SInput.HOLD, HELD[b]); }
                }
                ClientState.heavyHeld = ClientState.openForm() == Ability.ARSENAL && HELD[0] >= 0 ? HELD[0] : 0;
                ClientState.painting = ClientState.openForm() == Ability.SPORES && HELD[1] >= 0 && !mc.player.isShiftKeyDown();
                if (ClientState.painting) SporeClient.paint(1f);
            } else {
                for (int b = 0; b < 5; b++) HELD[b] = -1;
                ClientState.painting = false; ClientState.heavyHeld = 0;
            }
        } else {
            if (castHeld && !Keys.CAST.isDown()) { Net.toServer(new C2SAction(C2SAction.CAST_RELEASE, ClientState.domainVariant)); castHeld = false; }
            if (!free) xHeld = false;
            if (!awake) { for (int b = 0; b < 5; b++) HELD[b] = -1; ClientState.painting = false; }
        }
        SporeClient.lens = awake && ClientState.openForm() == Ability.SPORES;
        SporeClient.pick(1f);
        looked = lookTarget(64);
        if (looked != null && awake) {
            Long at = ClientState.CP_AT.get(looked.getId());
            if ((at == null || ClientState.ticks - at > 20) && ClientState.ticks % 5 == 0) Net.toServer(new C2SAction(C2SAction.CP_QUERY, looked.getId()));
        }
        float target = 0;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        openDomainInside = false;
        for (S2CWorld.Dom d : ClientState.domains) if (cam.distanceTo(new Vec3(d.x(), d.y(), d.z())) < d.radius()) { target = d.mine() ? 0.75f : 1f; if (!d.closed()) openDomainInside = true; }
        insideDomain += (target - insideDomain) * 0.06f;
        // no clouds drifting across the galaxy: hide them inside an open domain, give them back after
        var cs = mc.options.cloudStatus();
        if (insideDomain > 0.3f && openDomainInside && cs.get() != net.minecraft.client.CloudStatus.OFF) { savedClouds = cs.get(); cs.set(net.minecraft.client.CloudStatus.OFF); }
        else if (insideDomain < 0.2f && savedClouds != null) { cs.set(savedClouds); savedClouds = null; }
        if (ClientState.ticks - ClientState.worldAt > 40) { ClientState.domains = List.of(); ClientState.clashes = List.of(); ClientState.CORRODED.clear(); }
    }

    private static void select(Ability a, boolean sound) {
        if (a == null) return;
        Net.toServer(new C2SAction(C2SAction.SELECT, a.ordinal()));
        ClientState.me.putInt("selected", a.ordinal());
        ClientState.selectAnim = 0;
        if (sound) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.6f));
    }

    private static void slot(Ability a) {
        if (a == Ability.GUIDE) { Minecraft.getInstance().setScreen(new GuideScreen(null)); return; }
        if (Config.slotCastsImmediately.get() && a.type != Ability.Type.CHARGE) Net.toServer(new C2SAction(C2SAction.CAST_SLOT, a.ordinal()));
        else select(a, true);
        ClientState.me.putInt("selected", a.ordinal());
    }

    private static void send(int glfwButton, int phase, int arg) {
        int b = glfwButton == 0 ? C2SInput.LMB : glfwButton == 1 ? C2SInput.RMB : glfwButton == 3 ? C2SInput.M4 : C2SInput.M5;
        Net.toServer(new C2SInput(b, phase, flags(), arg));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void mouse(InputEvent.MouseButton.Pre e) {
        var mc = Minecraft.getInstance();
        int b = e.getButton();
        if (b == 2 || b > 4) return;
        // Domain charge: right click switches Open/Closed
        if (castHeld && ClientState.me.getInt("chargeAbility") == Ability.DOMAIN.ordinal() && b == 1 && e.getAction() == GLFW.GLFW_PRESS) { ClientState.domainVariant ^= 1; e.setCanceled(true); return; }
        if (!formInput()) return;
        Ability f = ClientState.openForm();
        if (e.getAction() == GLFW.GLFW_PRESS) {
            HELD[b] = 0; HOLD_SENT[b] = false;
            boolean shift = mc.player.isShiftKeyDown();
            // client-side parts of forms
            if (f == Ability.SPORES && b == 3) unleash();
            else if (f == Ability.SPORES && b == 4) ClientState.payload = Math.floorMod(ClientState.payload + (shift ? -1 : 1), Payload.values().length);
            else if (f == Ability.SPORES && b == 1 && !shift) SporeClient.paintStart();
            else if (f == Ability.AUTHORITY && b == 3) mc.setScreen(new RiftScreen(false));
            else if (f == Ability.AUTHORITY && b == 4 && ClientState.inRealm()) mc.setScreen(new WorldsScreen());
            else if (f == Ability.ARSENAL && b == 1) Net.toServer(new C2SInput(C2SInput.RMB, C2SInput.PRESS, flags(), moveInput()));
            else send(b, C2SInput.PRESS, 0);
            e.setCanceled(true);
        } else if (e.getAction() == GLFW.GLFW_RELEASE) {
            int held = Math.max(0, HELD[b]);
            if (HELD[b] >= 0) {
                if (!(f == Ability.SPORES && b != 0) && !(f == Ability.AUTHORITY && b >= 3)) send(b, C2SInput.RELEASE, held);
                if (f == Ability.SPORES && b == 1) ClientState.painting = false;
            }
            HELD[b] = -1;
            e.setCanceled(true);
        }
    }

    /** Spore form, mouse 4: the selection — or, with nothing selected, everything of yours in front of you. */
    private static void unleash() {
        int[] spores = SporeClient.SELECTED.stream().mapToInt(Integer::intValue).toArray();
        int[] hosts = SporeClient.SELECTED_HOSTS.stream().mapToInt(Integer::intValue).toArray();
        if (spores.length == 0 && hosts.length == 0) {
            SporeClient.selectCone(1f, 40, 96);
            spores = SporeClient.SELECTED.stream().mapToInt(Integer::intValue).toArray();
            hosts = SporeClient.SELECTED_HOSTS.stream().mapToInt(Integer::intValue).toArray();
        }
        if (spores.length == 0 && hosts.length == 0) { Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.literal("§7No spores of yours in front of you."), true); return; }
        Net.toServer(new C2SPayload(ClientState.payload, spores, hosts));
        SporeClient.flashSelection();
        SporeClient.clearSelection();
    }

    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered e) {
        if (!formInput()) return;
        if (e.isAttack() || e.isUseItem() || e.isPickBlock() && false) { e.setCanceled(true); e.setSwingHand(false); }
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.screen != null || !ClientState.awakened()) return;
        int dir = e.getScrollDelta() > 0 ? -1 : 1;
        if (xHeld) { select(WheelOrder.next(Ability.byIndex(ClientState.selected()), dir), true); xUsed = true; e.setCanceled(true); return; }
        if (castHeld && ClientState.me.getInt("chargeAbility") == Ability.DOMAIN.ordinal()) { ClientState.domainVariant ^= 1; e.setCanceled(true); return; }
        if (!formInput()) return;
        Ability f = ClientState.openForm();
        if (f == Ability.SPORES) { ClientState.payload = Math.floorMod(ClientState.payload + dir, Payload.values().length); e.setCanceled(true); }
        else if (f == Ability.ARSENAL) { Net.toServer(new C2SInput(C2SInput.SCROLL, C2SInput.PRESS, 0, dir)); e.setCanceled(true); }
    }

    private static Entity lookTarget(double range) {
        var mc = Minecraft.getInstance();
        Vec3 eye = mc.player.getEyePosition(1), look = mc.player.getViewVector(1);
        Vec3 end = eye.add(look.scale(range));
        AABB box = mc.player.getBoundingBox().expandTowards(look.scale(range)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(mc.player, eye, end, box, x -> x instanceof LivingEntity && !x.isSpectator() && x != mc.player, range * range);
        if (hit == null) return null;
        var block = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (block.getLocation().distanceToSqr(eye) < hit.getLocation().distanceToSqr(eye)) return null;
        return hit.getEntity();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY && insideDomain > 0.02f && openDomainInside)
            com.andy.abyss.client.render.AbyssSky.drawGalaxy(e.getPoseStack(), Math.min(1f, insideDomain * 1.1f));
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) FxManager.render(e.getPoseStack(), e.getCamera(), e.getPartialTick());
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) MoveFx.renderNumbers(e.getPoseStack(), e.getCamera(), e.getPartialTick());
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        float s = FxManager.shake;
        if (s < 0.01f) return;
        double t = ClientState.ticks + e.getPartialTick();
        e.setYaw(e.getYaw() + (float) (Math.sin(t * 2.1) * s * 1.6));
        e.setPitch(e.getPitch() + (float) (Math.cos(t * 2.7) * s * 1.2));
        e.setRoll(e.getRoll() + (float) (Math.sin(t * 1.7) * s * 0.8));
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        double k = 1 - 0.06 * insideDomain - 0.03 * FxManager.pulse - MoveFx.fovKick();
        if (ClientState.ticks < ClientState.slowUntil) k -= 0.05;
        if (k != 1) e.setFOV(e.getFOV() * k);
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor e) {
        if (insideDomain < 0.02f || !Config.domainFog.get()) return;
        float k = insideDomain * 0.7f;
        e.setRed(e.getRed() * (1 - k) + 0.04f * k);
        e.setGreen(e.getGreen() * (1 - k) + 0.02f * k);
        e.setBlue(e.getBlue() * (1 - k) + 0.08f * k);
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog e) {
        if (insideDomain < 0.02f || !Config.domainFog.get()) return;
        e.setFarPlaneDistance(e.getFarPlaneDistance() * (1 - insideDomain * 0.35f));
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { ClientState.reset(); SporeClient.ALL.clear(); FxManager.clear(); MoveFx.clear(); }

    private ClientEvents() {}
}
