package net.unfamily.iskalib;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.iskalib.crafting.StrictShapedRecipe;

public final class IskaLibRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, IskaLib.MOD_ID);

    static {
        SERIALIZERS.register("strict_shaped", () -> StrictShapedRecipe.SERIALIZER);
    }

    private IskaLibRecipes() {}

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
