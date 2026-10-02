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
 * Split recipe JSON bundles <strong>before</strong> any other RecipeManager.apply HEAD injects
 * (notably KubeJS at priority 1100). {@link WrapMethod} runs outside {@code @Inject(HEAD)}.
 *
 * <p>{@code remap = false}: NeoForge 1.21 uses Mojmap at runtime; Mixin AP cannot map {@code apply} under MCP.
 */
@Mixin(value = RecipeManager.class, priority = 2000)
public abstract class RecipeManagerApplyMixin {

    @WrapMethod(
            method =
                    "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            remap = false)
    private void iskaLib$splitBeforeKube(
            Map<ResourceLocation, JsonElement> recipes,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            Operation<Void> original) {
        RecipeBundleSplitter.splitInPlace(recipes, ResourceLocation::parse, ResourceLocation::toString);
        original.call(recipes, resourceManager, profiler);
    }
}
