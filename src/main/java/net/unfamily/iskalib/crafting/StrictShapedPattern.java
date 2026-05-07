package net.unfamily.iskalib.crafting;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.chars.CharArraySet;
import it.unimi.dsi.fastutil.chars.CharSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * Like {@link ShapedRecipePattern} but does not shrink the pattern or mirror-match; the JSON layout maps 1:1 to
 * crafting grid coordinates when {@link StrictCraftingInputAccessor} is present on the {@link CraftingInput}.
 */
public final class StrictShapedPattern {
    public static final MapCodec<StrictShapedPattern> MAP_CODEC = ShapedRecipePattern.Data.MAP_CODEC
            .flatXmap(
                    StrictShapedPattern::unpackStrict,
                    pattern -> pattern.data.map(DataResult::success)
                            .orElseGet(() -> DataResult.error(() -> "Cannot encode strict shaped pattern without data")));

    public static final StreamCodec<RegistryFriendlyByteBuf, StrictShapedPattern> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            p -> p.width,
            ByteBufCodecs.VAR_INT,
            p -> p.height,
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
            p -> p.ingredients,
            StrictShapedPattern::fromNetwork);

    private final int width;
    private final int height;
    private final List<Optional<Ingredient>> ingredients;
    private final Optional<ShapedRecipePattern.Data> data;
    private final int ingredientCount;

    private StrictShapedPattern(int width, int height, List<Optional<Ingredient>> ingredients, Optional<ShapedRecipePattern.Data> data) {
        this.width = width;
        this.height = height;
        this.ingredients = ingredients;
        this.data = data;
        this.ingredientCount = (int) ingredients.stream().flatMap(Optional::stream).count();
    }

    private static StrictShapedPattern fromNetwork(Integer width, Integer height, List<Optional<Ingredient>> ingredients) {
        return new StrictShapedPattern(width, height, ingredients, Optional.empty());
    }

    private static DataResult<StrictShapedPattern> unpackStrict(ShapedRecipePattern.Data data) {
        List<String> rows = data.pattern();
        int height = rows.size();
        int width = rows.getFirst().length();
        if (width != ShapedRecipePattern.getMaxWidth() || height != ShapedRecipePattern.getMaxHeight()) {
            return DataResult.error(
                    () -> "iska_lib:strict_shaped requires a full %dx%d pattern (spaces allowed, no auto-trim)"
                            .formatted(ShapedRecipePattern.getMaxWidth(), ShapedRecipePattern.getMaxHeight()));
        }

        List<Optional<Ingredient>> list = new ArrayList<>(width * height);
        CharSet unusedSymbols = new CharArraySet(data.key().keySet());

        for (int y = 0; y < height; y++) {
            String line = rows.get(y);
            for (int x = 0; x < width; x++) {
                char symbol = line.charAt(x);
                if (symbol == ' ') {
                    list.add(Optional.empty());
                } else {
                    Ingredient ingredient = data.key().get(symbol);
                    if (ingredient == null) {
                        return DataResult.error(() -> "Pattern references symbol '" + symbol + "' but it's not defined in the key");
                    }
                    unusedSymbols.remove(symbol);
                    list.add(Optional.of(ingredient));
                }
            }
        }

        if (!unusedSymbols.isEmpty()) {
            return DataResult.error(() -> "Key defines symbols that aren't used in pattern: " + unusedSymbols);
        }

        return DataResult.success(new StrictShapedPattern(width, height, list, Optional.of(data)));
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public List<Optional<Ingredient>> ingredients() {
        return this.ingredients;
    }

    public Optional<ShapedRecipePattern.Data> data() {
        return this.data;
    }

    public boolean matchesAbsolute(CraftingInput input) {
        if (input.ingredientCount() != this.ingredientCount) {
            return false;
        }
        if (input instanceof StrictCraftingInputAccessor acc && acc.iska_lib$hasStrictContext()) {
            for (int y = 0; y < this.height; y++) {
                for (int x = 0; x < this.width; x++) {
                    Optional<Ingredient> expected = this.ingredients.get(x + y * this.width);
                    ItemStack actual = acc.iska_lib$getStrictSlot(x, y);
                    if (!Ingredient.testOptionalIngredient(expected, actual)) {
                        return false;
                    }
                }
            }
            return true;
        }

        if (input.width() == this.width && input.height() == this.height) {
            for (int y = 0; y < this.height; y++) {
                for (int x = 0; x < this.width; x++) {
                    Optional<Ingredient> expected = this.ingredients.get(x + y * this.width);
                    ItemStack actual = input.getItem(x, y);
                    if (!Ingredient.testOptionalIngredient(expected, actual)) {
                        return false;
                    }
                }
            }
            return true;
        }

        return false;
    }
}
