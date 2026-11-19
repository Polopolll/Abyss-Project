package com.andy.abyss.net;

import com.andy.abyss.Abyss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(Abyss.id("main"), () -> V, V::equals, V::equals);

    public static void register() {
        int i = 0;
        CH.registerMessage(i++, C2SAction.class, C2SAction::write, C2SAction::read, C2SAction::handle);
        CH.registerMessage(i++, C2SPayload.class, C2SPayload::write, C2SPayload::read, C2SPayload::handle);
        CH.registerMessage(i++, C2SRift.class, C2SRift::write, C2SRift::read, C2SRift::handle);
        CH.registerMessage(i++, S2CSync.class, S2CSync::write, S2CSync::read, S2CSync::handle);
        CH.registerMessage(i++, S2CVisual.class, S2CVisual::write, S2CVisual::read, S2CVisual::handle);
        CH.registerMessage(i++, S2CSpores.class, S2CSpores::write, S2CSpores::read, S2CSpores::handle);
        CH.registerMessage(i++, S2CFx.class, S2CFx::write, S2CFx::read, S2CFx::handle);
        CH.registerMessage(i++, S2CCp.class, S2CCp::write, S2CCp::read, S2CCp::handle);
        CH.registerMessage(i++, S2CInfection.class, S2CInfection::write, S2CInfection::read, S2CInfection::handle);
        CH.registerMessage(i++, C2SInput.class, C2SInput::write, C2SInput::read, C2SInput::handle);
        CH.registerMessage(i++, C2SRealmTarget.class, C2SRealmTarget::write, C2SRealmTarget::read, C2SRealmTarget::handle);
        CH.registerMessage(i++, S2CMove.class, S2CMove::write, S2CMove::read, S2CMove::handle);
        CH.registerMessage(i++, S2CHit.class, S2CHit::write, S2CHit::read, S2CHit::handle);
        CH.registerMessage(i++, S2CSouls.class, S2CSouls::write, S2CSouls::read, S2CSouls::handle);
        CH.registerMessage(i++, S2CWorld.class, S2CWorld::write, S2CWorld::read, S2CWorld::handle);
        CH.registerMessage(i++, S2CDebris.class, S2CDebris::write, S2CDebris::read, S2CDebris::handle);
    }

    public static void toServer(Object msg) { CH.sendToServer(msg); }
    public static void to(ServerPlayer p, Object msg) { CH.send(PacketDistributor.PLAYER.with(() -> p), msg); }
    public static void around(Entity e, Object msg) { CH.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), msg); }
    public static void near(ServerLevel l, Vec3 p, double r, Object msg) {
        CH.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(p.x, p.y, p.z, r, l.dimension())), msg);
    }
    public static void dim(ServerLevel l, Object msg) { CH.send(PacketDistributor.DIMENSION.with(l::dimension), msg); }

    private Net() {}
}
