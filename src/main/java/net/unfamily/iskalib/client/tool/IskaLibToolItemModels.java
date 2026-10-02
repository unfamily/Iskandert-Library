package net.unfamily.iskalib.client.tool;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.unfamily.iskalib.tool.IskaLibTools;

/**
 * Maps dynamic Library tool items to the iron pickaxe baked model (no per-id asset required).
 */
public final class IskaLibToolItemModels {
    private IskaLibToolItemModels() {}

    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel pick = event.getModels().get(
                ModelResourceLocation.inventory(Identifier.withDefaultNamespace("iron_pickaxe")));
        if (pick == null) {
            return;
        }
        for (DeferredItem<Item> tool : IskaLibTools.allItems()) {
            ModelResourceLocation key = ModelResourceLocation.inventory(tool.getId());
            event.getModels().put(key, pick);
        }
    }
}
