package net.unfamily.iskalib.crafting;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Cross-mod bridge for recipe id gating (stages, KubeJS remove, pack filters).
 * Mods register {@link Provider}s; machines call {@link #isRecipeBlocked} when selecting a recipe.
 * Default with no providers: not blocked. Retrocompatible — does not alter recipe loading.
 * <p>
 * With providers registered: missing owner ({@code ownerUuid == null}) is blocked by the bridge
 * without consulting providers. With a present UUID (online or offline), providers decide via
 * {@link Provider#isBlocked(Identifier, UUID, Level, ServerPlayer)}; {@code onlinePlayer} may be
 * null when the owner is offline.
 */
public final class RecipeBlockBridge {
    public interface Provider {
        /**
         * Primary gate for a known owner UUID (online or offline).
         * Called only when {@code ownerUuid} is non-null.
         *
         * @param recipeId     definitive RecipeManager id ({@code namespace:path_N} after bundle split)
         * @param ownerUuid    machine owner UUID (non-null when invoked from the bridge)
         * @param level        world context; may be null
         * @param onlinePlayer owner when currently online; may be null if offline
         * @return true if this provider blocks the recipe for the given context
         */
        boolean isBlocked(
                Identifier recipeId,
                @Nullable UUID ownerUuid,
                @Nullable Level level,
                @Nullable ServerPlayer onlinePlayer);

        /**
         * @param recipeId definitive RecipeManager id ({@code namespace:path_N} after bundle split)
         * @param player   machine owner when online; may be null
         * @param level    world context; may be null
         * @return true if this provider blocks the recipe for the given context
         * @deprecated Prefer {@link #isBlocked(Identifier, UUID, Level, ServerPlayer)}.
         */
        @Deprecated
        default boolean isBlocked(
                Identifier recipeId, @Nullable ServerPlayer player, @Nullable Level level) {
            UUID ownerUuid = player != null ? player.getUUID() : null;
            return isBlocked(recipeId, ownerUuid, level, player);
        }
    }

    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private RecipeBlockBridge() {}

    public static void registerProvider(Provider provider) {
        PROVIDERS.add(Objects.requireNonNull(provider, "provider"));
    }

    public static void unregisterProvider(Provider provider) {
        PROVIDERS.remove(provider);
    }

    public static void clearProviders() {
        PROVIDERS.clear();
    }

    /**
     * True if this recipe id is blocked for the given owner.
     * No providers → not blocked. Providers present and {@code ownerUuid == null} → blocked
     * without calling providers. Otherwise providers decide for that UUID (offline allowed).
     */
    public static boolean isRecipeBlocked(
            Identifier recipeId, @Nullable UUID ownerUuid, @Nullable Level level) {
        if (recipeId == null) {
            return false;
        }
        if (PROVIDERS.isEmpty()) {
            return false;
        }
        if (ownerUuid == null) {
            return true;
        }
        ServerPlayer onlinePlayer = resolveOnlinePlayer(level, ownerUuid);
        for (Provider provider : PROVIDERS) {
            if (provider.isBlocked(recipeId, ownerUuid, level, onlinePlayer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if any registered provider blocks this recipe id.
     *
     * @deprecated Prefer {@link #isRecipeBlocked(Identifier, UUID, Level)}.
     */
    @Deprecated
    public static boolean isRecipeBlocked(
            Identifier recipeId, @Nullable ServerPlayer player, @Nullable Level level) {
        UUID ownerUuid = player != null ? player.getUUID() : null;
        return isRecipeBlocked(recipeId, ownerUuid, level);
    }

    /** True when at least one provider is registered (stages / external gates active). */
    public static boolean hasProviders() {
        return !PROVIDERS.isEmpty();
    }

    @Nullable
    private static ServerPlayer resolveOnlinePlayer(@Nullable Level level, @Nullable UUID ownerUuid) {
        if (level == null || ownerUuid == null || level.isClientSide()) {
            return null;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
    }
}
