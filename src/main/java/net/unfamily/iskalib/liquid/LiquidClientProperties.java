package net.unfamily.iskalib.liquid;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Optional client-only fluid appearance (overlay texture).
 * Still/flow/tint remain on {@link LiquidSpec}.
 */
public record LiquidClientProperties(@Nullable ResourceLocation overlayTexture) {
    public static final LiquidClientProperties NONE = new LiquidClientProperties(null);

    public static LiquidClientProperties withOverlay(ResourceLocation overlay) {
        return new LiquidClientProperties(overlay);
    }
}
