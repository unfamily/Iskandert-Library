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
 * <p>KubeJS injects at HEAD with mixin priority 1100. For multiple HEAD injectors, a
 * <em>lower</em> mixin priority is applied later and therefore runs <em>earlier</em> at runtime.
 * Priority 900 makes this split run before KubeJS {@code ServerEvents.recipes} so
 * {@code event.remove({ id: '..._N' })} can see post-split ids.
 *
 * <p>{@code remap = false}: NeoForge 1.21 uses Mojmap at runtime; Mixin AP cannot map {@code apply}
 * under MCP.
 */
@Mixin(value = RecipeManager.class, priority = 900)
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
