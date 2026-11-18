package com.andy.abyss.registry;

import com.andy.abyss.Abyss;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Abyss.MODID);
    /** The floor of the Abyss: thrones, prisons and closed-domain arenas. Unbreakable. */
    public static final RegistryObject<Block> ABYSSAL_CRYSTAL = BLOCKS.register("abyssal_crystal", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK).strength(-1f, 3600000f).noLootTable().sound(SoundType.AMETHYST)
            .lightLevel(s -> 3).pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, t) -> false)));
    private ModBlocks() {}
}
