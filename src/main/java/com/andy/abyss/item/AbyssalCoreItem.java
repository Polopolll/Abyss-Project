package com.andy.abyss.item;

import com.andy.abyss.server.Awakening;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Consume to become the Abyss. */
public class AbyssalCoreItem extends Item {
    public AbyssalCoreItem(Properties p) { super(p); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            String err = Awakening.awaken(sp, false);
            if (err != null) { sp.displayClientMessage(Component.literal("§5" + err), true); return InteractionResultHolder.fail(st); }
            if (!sp.isCreative()) st.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override public boolean isFoil(ItemStack s) { return true; }

    @Override
    public void appendHoverText(ItemStack s, Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.literal("§5An infinite dimension, folded small."));
        tip.add(Component.literal("§7Use to become its vessel."));
    }
}
