package net.unfamily.iskalib.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.unfamily.iskalib.crafting.RecipeBundleSplitter;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Splits recipe bundles into {@code path_N} children before KubeJS / other listeners see the map.
 * High mixin priority so this {@link WrapMethod} is outermost relative to typical KubeJS wraps.
 */
@Mixin(value = RecipeManager.class, priority = 2000)
public abstract class RecipeManagerApplyMixin {

    @WrapMethod(method = "apply")
    private void iskaLib$splitBundlesBeforeApply(
            Map<ResourceLocation, JsonElement> recipes,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            Operation<Void> original) {
        RecipeBundleSplitter.splitInPlace(recipes, ResourceLocation::parse, ResourceLocation::toString);
        original.call(recipes, resourceManager, profiler);
    }
}
