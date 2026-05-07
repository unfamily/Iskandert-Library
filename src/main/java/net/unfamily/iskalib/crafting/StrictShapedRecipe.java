package net.unfamily.iskalib.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

public final class StrictShapedRecipe extends NormalCraftingRecipe {
    public static final MapCodec<StrictShapedRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(
                            Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.commonInfo),
                            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(r -> r.bookInfo),
                            StrictShapedPattern.MAP_CODEC.forGetter(r -> r.pattern),
                            ItemStackTemplate.CODEC.fieldOf("result").forGetter(r -> r.result))
                    .apply(i, StrictShapedRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StrictShapedRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC,
            r -> r.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC,
            r -> r.bookInfo,
            StrictShapedPattern.STREAM_CODEC,
            r -> r.pattern,
            ItemStackTemplate.STREAM_CODEC,
            r -> r.result,
            StrictShapedRecipe::new);

    public static final RecipeSerializer<StrictShapedRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final StrictShapedPattern pattern;
    private final ItemStackTemplate result;

    public StrictShapedRecipe(
            Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, StrictShapedPattern pattern, ItemStackTemplate result) {
        super(commonInfo, bookInfo);
        this.pattern = pattern;
        this.result = result;
    }

    @Override
    public RecipeSerializer<StrictShapedRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.createFromOptionals(this.pattern.ingredients());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.pattern.matchesAbsolute(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return this.result.create();
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(
                new ShapedCraftingRecipeDisplay(
                        this.pattern.width(),
                        this.pattern.height(),
                        this.pattern.ingredients().stream()
                                .map(e -> e.map(Ingredient::display).orElse(SlotDisplay.Empty.INSTANCE))
                                .toList(),
                        new SlotDisplay.ItemStackSlotDisplay(this.result),
                        new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }
}
