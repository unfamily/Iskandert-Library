package net.unfamily.iskalib.crafting;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;

/** NeoForge 26: split recipe bundles via {@link ModifyRecipeJsonsEvent} (no mixin needed). */
public final class RecipeBundleSplitHooks {
    private RecipeBundleSplitHooks() {}

    /** HIGHEST so children + KubeJS mirrors exist before other listeners / KubeJS HEAD work. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onModifyRecipeJsons(ModifyRecipeJsonsEvent event) {
        RecipeBundleSplitter.splitInPlace(event.getRecipeJsons(), Identifier::parse, Identifier::toString);
    }
}
