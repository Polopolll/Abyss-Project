package com.andy.abyss.item;

import com.andy.abyss.Config;
import com.andy.abyss.data.AbyssData;
import com.andy.abyss.net.S2CFx;
import com.andy.abyss.registry.ModSounds;
import com.andy.abyss.server.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Abyssal Upgrade (creative only, no recipe): use to switch infinite output on or off. */
public class AbyssalUpgradeItem extends Item {
    public AbyssalUpgradeItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            AbyssData d = AbyssData.get(sp);
            if (!d.awakened) { sp.displayClientMessage(Component.literal("§5Only a vessel of the Abyss can take this."), true); return InteractionResultHolder.fail(st); }
            if (!Config.allowInfiniteOutput.get()) { sp.displayClientMessage(Component.literal("§5Infinite output is disabled on this server."), true); return InteractionResultHolder.fail(st); }
            d.infinite = !d.infinite;
            d.output = d.outputCap();
            d.dirty = true;
            sp.displayClientMessage(Component.literal(d.infinite ? "§dThe floodgates open. §fOutput: ∞" : "§5Output returns to its limit."), true);
            Util.fx(sp.serverLevel(), S2CFx.AWAKEN, sp.position().add(0, 1, 0), 1, 0, 0, sp.getId(), -1);
            Util.sound(sp.serverLevel(), sp.position(), ModSounds.DOMAIN.get(), 1.5f, d.infinite ? 0.7f : 0.4f);
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override public boolean isFoil(ItemStack s) { return true; }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("§5Removes the limit on how much abyss can pour out."));
        tip.add(Component.literal("§7Use to toggle infinite output. Creative only."));
    }
}
