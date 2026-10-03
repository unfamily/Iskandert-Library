package net.unfamily.iskalib.mixin;

import com.google.gson.JsonElement;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.unfamily.iskalib.crafting.RecipeBundleSplitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Split recipe JSON bundles before KubeJS processes {@code RecipeManager.apply}.
 *
 * <p>KubeJS injects at HEAD with priority 1100; this mixin uses priority 2000 so the map is
 * mutated first. Needed for NeoForge 1.21.1 (no {@code ModifyRecipeJsonsEvent}).
 *
 * <p>{@code remap = false}: NeoForge 1.21 uses Mojmap at runtime; Mixin AP cannot map {@code apply}
 * under MCP.
 */
@Mixin(value = RecipeManager.class, priority = 2000)
public abstract class RecipeManagerApplyMixin {

    @Inject(
            method =
                    "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"),
            remap = false)
    private void iskaLib$splitBeforeKube(
            Map<ResourceLocation, JsonElement> recipes,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo ci) {
        RecipeBundleSplitter.splitInPlace(recipes, ResourceLocation::parse, ResourceLocation::toString);
    }
}
