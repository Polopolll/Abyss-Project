package com.andy.abyss.client;

import com.andy.abyss.Abyss;
import com.andy.abyss.Config;
import com.andy.abyss.client.gui.RiftScreen;
import com.andy.abyss.client.gui.RosterScreen;
import com.andy.abyss.client.gui.WheelScreen;
import com.andy.abyss.client.render.FxManager;
import com.andy.abyss.data.Ability;
import com.andy.abyss.net.C2SAction;
import com.andy.abyss.net.C2SPayload;
import com.andy.abyss.net.Net;
import com.andy.abyss.net.S2CWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Input, ticking, world rendering, camera shake and domain fog. */
@Mod.EventBusSubscriber(modid = Abyss.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean castHeld, sporeHeld, lensWas, jumpWas;
    public static Entity looked;
    public static float insideDomain;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { if (e.phase == TickEvent.Phase.END) { ClientState.reset(); SporeClient.ALL.clear(); FxManager.clear(); } return; }
        if (e.phase == TickEvent.Phase.START) {
            // while the lens is open, number keys are payloads, not hotbar slots
            if (SporeClient.lens) for (int i = 0; i < 9; i++) while (mc.options.keyHotbarSlots[i].consumeClick()) payload(i);
            return;
        }
        ClientState.ticks++;
        FxManager.tick();
        SporeClient.tick();
        boolean awake = ClientState.awakened();
        boolean free = mc.screen == null;
        // Triangle: the target mashes jump; the user mashes cast
        S2CWorld.Clash clash = ClientState.myClash();
        boolean jump = mc.options.keyJump.isDown();
        if (clash != null && clash.you() == 1 && jump && !jumpWas) Net.toServer(new C2SAction(C2SAction.CLASH_PUSH, 0));
        jumpWas = jump;
        if (awake && free) {
            while (Keys.WHEEL.consumeClick()) mc.setScreen(new WheelScreen());
            while (Keys.ROSTER.consumeClick()) mc.setScreen(new RosterScreen());
            while (Keys.PREV.consumeClick()) Net.toServer(new C2SAction(C2SAction.SWAP_PREV, 0));
            while (Keys.NEXT.consumeClick()) Net.toServer(new C2SAction(C2SAction.SWAP_NEXT, 0));
            while (Keys.MAIN.consumeClick()) Net.toServer(new C2SAction(C2SAction.SWAP_MAIN, 0));
            // cast: press = start (or push in a clash), release = release a charge
            boolean cast = Keys.CAST.isDown();
            if (cast && !castHeld) {
                if (clash == null && ClientState.selected() == Ability.RIFT.ordinal()) mc.setScreen(new RiftScreen(mc.player.isShiftKeyDown()));
                else Net.toServer(new C2SAction(C2SAction.CAST_START, 0));
            }
            if (!cast && castHeld) Net.toServer(new C2SAction(C2SAction.CAST_RELEASE, 0));
            castHeld = cast;
            boolean spore = Keys.SPORES.isDown();
            if (spore && !sporeHeld) Net.toServer(new C2SAction(mc.player.isShiftKeyDown() ? C2SAction.SPORE_MODE : C2SAction.SPREAD_START, 0));
            if (!spore && sporeHeld) Net.toServer(new C2SAction(C2SAction.SPREAD_STOP, 0));
            sporeHeld = spore;
            boolean lens = Keys.down(Keys.LENS);
            if (lens != lensWas) { SporeClient.lens = lens; if (!lens) SporeClient.clearSelection(); }
            lensWas = lens;
        } else {
            if (castHeld && !Keys.CAST.isDown()) { Net.toServer(new C2SAction(C2SAction.CAST_RELEASE, 0)); castHeld = false; }
            if (sporeHeld) { Net.toServer(new C2SAction(C2SAction.SPREAD_STOP, 0)); sporeHeld = false; }
            if (!awake) { SporeClient.lens = false; lensWas = false; }
        }
        SporeClient.pick(1f);
        // Angel Eyes: ask the server about what we look at
        looked = lookTarget(64);
        if (looked != null && (awake || Config.angelEyesHud.get())) {
            Long at = ClientState.CP_AT.get(looked.getId());
            if (awake && (at == null || ClientState.ticks - at > 20) && ClientState.ticks % 5 == 0) Net.toServer(new C2SAction(C2SAction.CP_QUERY, looked.getId()));
        }
        // domain fog / vignette
        float target = 0;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (S2CWorld.Dom d : ClientState.domains) if (cam.distanceTo(new Vec3(d.x(), d.y(), d.z())) < d.radius()) target = d.mine() ? 0.5f : 1f;
        insideDomain += (target - insideDomain) * 0.15f;
        if (ClientState.ticks - ClientState.worldAt > 40) { ClientState.domains = java.util.List.of(); ClientState.clashes = java.util.List.of(); ClientState.CORRODED.clear(); }
    }

    private static void payload(int key) {
        if (key >= com.andy.abyss.data.Payload.values().length) return;
        int[] spores = SporeClient.SELECTED.stream().mapToInt(Integer::intValue).toArray();
        int[] hosts = SporeClient.SELECTED_HOSTS.stream().mapToInt(Integer::intValue).toArray();
        if (spores.length == 0 && hosts.length == 0) {
            if (SporeClient.hoverSpore >= 0) spores = new int[]{SporeClient.hoverSpore};
            else if (SporeClient.hoverHost >= 0) hosts = new int[]{SporeClient.hoverHost};
            else return;
        }
        Net.toServer(new C2SPayload(key, spores, hosts));
        SporeClient.clearSelection();
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
    public static void mouse(InputEvent.MouseButton.Pre e) {
        if (!SporeClient.lens || Minecraft.getInstance().screen != null || e.getAction() != 1) return;
        if (e.getButton() == 0) { SporeClient.clickSelect(); e.setCanceled(true); }
        else if (e.getButton() == 1) { SporeClient.sweep(1); e.setCanceled(true); }
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        if (!SporeClient.lens || Minecraft.getInstance().screen != null) return;
        SporeClient.cycle += e.getScrollDelta() > 0 ? -1 : 1;
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        FxManager.render(e.getPoseStack(), e.getCamera(), e.getPartialTick());
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
        e.setFarPlaneDistance(e.getFarPlaneDistance() * (1 - insideDomain * 0.45f));
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { ClientState.reset(); SporeClient.ALL.clear(); FxManager.clear(); }

    private ClientEvents() {}
}
