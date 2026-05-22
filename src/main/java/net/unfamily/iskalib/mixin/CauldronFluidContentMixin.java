package net.unfamily.iskalib.mixin;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.CauldronFluidContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jspecify.annotations.Nullable;

/**
 * NeoForge 26 can run {@link CauldronFluidContent#init()} more than once during resource reload;
 * skip duplicate block/fluid registrations instead of crashing.
 */
@Mixin(value = CauldronFluidContent.class, remap = false)
public class CauldronFluidContentMixin {

    @Inject(method = "register", at = @At("HEAD"), cancellable = true, remap = false)
    private static void iskaLib$skipDuplicateRegister(
            Block block,
            Fluid fluid,
            int totalAmount,
            @Nullable IntegerProperty levelProperty,
            CallbackInfo ci
    ) {
        if (CauldronFluidContent.getForBlock(block) != null || CauldronFluidContent.getForFluid(fluid) != null) {
            ci.cancel();
        }
    }
}
