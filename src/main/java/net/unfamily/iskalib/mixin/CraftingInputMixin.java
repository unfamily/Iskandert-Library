package net.unfamily.iskalib.mixin;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.unfamily.iskalib.crafting.StrictCraftingInputAccessor;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingInput.class)
public abstract class CraftingInputMixin implements StrictCraftingInputAccessor {
    @Unique
    private static final ThreadLocal<CraftingInputMixin.FullGrid> iska_lib$PENDING = new ThreadLocal<>();

    @Unique
    private @Nullable List<ItemStack> iska_lib$strictFullStacks;

    @Unique
    private int iska_lib$strictFullW;

    @Unique
    private int iska_lib$strictFullH;

    @Override
    public void iska_lib$attachStrictContext(@Nullable List<ItemStack> fullStacks, int fullWidth, int fullHeight) {
        this.iska_lib$strictFullStacks = fullStacks;
        this.iska_lib$strictFullW = fullWidth;
        this.iska_lib$strictFullH = fullHeight;
    }

    @Override
    public boolean iska_lib$hasStrictContext() {
        return this.iska_lib$strictFullStacks != null;
    }

    @Override
    public ItemStack iska_lib$getStrictSlot(int x, int y) {
        if (this.iska_lib$strictFullStacks == null || x < 0 || y < 0 || x >= this.iska_lib$strictFullW || y >= this.iska_lib$strictFullH) {
            return ItemStack.EMPTY;
        }
        return this.iska_lib$strictFullStacks.get(x + y * this.iska_lib$strictFullW);
    }

    @Inject(method = "ofPositioned", remap = false, at = @At("HEAD"))
    private static void iska_lib$captureHead(int width, int height, List<ItemStack> items, CallbackInfoReturnable<CraftingInput.Positioned> cir) {
        iska_lib$PENDING.set(new FullGrid(width, height, List.copyOf(items)));
    }

    @Inject(method = "ofPositioned", remap = false, at = @At("RETURN"))
    private static void iska_lib$attachOnReturn(int width, int height, List<ItemStack> items, CallbackInfoReturnable<CraftingInput.Positioned> cir) {
        try {
            FullGrid ctx = iska_lib$PENDING.get();
            if (ctx == null) {
                return;
            }
            CraftingInput.Positioned positioned = cir.getReturnValue();
            if (positioned == CraftingInput.Positioned.EMPTY) {
                return;
            }
            CraftingInput input = positioned.input();
            ((StrictCraftingInputAccessor) (Object) input).iska_lib$attachStrictContext(ctx.stacks(), ctx.width(), ctx.height());
        } finally {
            iska_lib$PENDING.remove();
        }
    }

    private record FullGrid(int width, int height, List<ItemStack> stacks) {}
}
