package net.unfamily.iskalib.integration.ftbchunks;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Optional FTB Chunks bridge for claim checks.
 *
 * <p>No hard references to FTB Chunks types so the class loads when the mod is absent.
 * Prefers {@code FTBChunksAPI.API#getOwningTeam}; falls back to {@code ClaimedChunkManager#getChunk}.
 */
public final class FtbChunksBridge {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String MOD_ID = "ftbchunks";
    private static final String CLS_API = "dev.ftb.mods.ftbchunks.api.FTBChunksAPI";
    private static final String CLS_API_IFACE = "dev.ftb.mods.ftbchunks.api.FTBChunksAPI$API";
    private static final String CLS_CHUNK_DIM_POS = "dev.ftb.mods.ftblibrary.math.ChunkDimPos";

    private static Boolean available;
    private static Method apiMethod;
    private static Method isManagerLoadedMethod;
    private static Method getOwningTeamMethod;
    private static Method getManagerMethod;
    private static Method getChunkMethod;
    private static Constructor<?> chunkDimPosCtor;

    private FtbChunksBridge() {}

    public static boolean isAvailable() {
        if (available != null) {
            return available;
        }
        try {
            if (!ModList.get().isLoaded(MOD_ID)) {
                available = false;
                return false;
            }
            Class<?> apiClass = Class.forName(CLS_API, false, FtbChunksBridge.class.getClassLoader());
            Class<?> apiIface = Class.forName(CLS_API_IFACE, false, FtbChunksBridge.class.getClassLoader());
            apiMethod = apiClass.getMethod("api");
            isManagerLoadedMethod = apiIface.getMethod("isManagerLoaded");
            getManagerMethod = apiIface.getMethod("getManager");
            try {
                getOwningTeamMethod = apiIface.getMethod("getOwningTeam", Level.class, ChunkPos.class);
            } catch (NoSuchMethodException ignored) {
                getOwningTeamMethod = null;
            }
            Class<?> chunkDimPosClass = Class.forName(CLS_CHUNK_DIM_POS, false, FtbChunksBridge.class.getClassLoader());
            try {
                chunkDimPosCtor = chunkDimPosClass.getConstructor(Level.class, BlockPos.class);
            } catch (NoSuchMethodException ignored) {
                chunkDimPosCtor = chunkDimPosClass.getConstructor(net.minecraft.resources.ResourceKey.class, int.class, int.class);
            }
            available = true;
            return true;
        } catch (Throwable t) {
            LOGGER.debug("FTB Chunks bridge unavailable: {}", t.getMessage());
            available = false;
            return false;
        }
    }

    /**
     * @return true if FTB Chunks reports an owning team for the chunk containing {@code pos}
     */
    public static boolean isChunkClaimed(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !isAvailable()) {
            return false;
        }
        try {
            Object api = apiMethod.invoke(null);
            Object loaded = isManagerLoadedMethod.invoke(api);
            if (!(loaded instanceof Boolean b) || !b) {
                return false;
            }
            if (getOwningTeamMethod != null) {
                Object result = getOwningTeamMethod.invoke(api, level, ChunkPos.containing(pos));
                if (result instanceof Optional<?> optional) {
                    return optional.isPresent();
                }
                if (result != null) {
                    return true;
                }
            }
            return isClaimedViaManager(api, level, pos);
        } catch (Throwable t) {
            LOGGER.debug("FTB Chunks claim check failed: {}", t.getMessage());
            return false;
        }
    }

    private static boolean isClaimedViaManager(Object api, ServerLevel level, BlockPos pos) throws Exception {
        Object manager = getManagerMethod.invoke(api);
        if (getChunkMethod == null) {
            for (Method method : manager.getClass().getMethods()) {
                if ("getChunk".equals(method.getName()) && method.getParameterCount() == 1) {
                    getChunkMethod = method;
                    break;
                }
            }
        }
        if (getChunkMethod == null || chunkDimPosCtor == null) {
            return false;
        }
        Object chunkDimPos;
        if (chunkDimPosCtor.getParameterCount() == 2) {
            chunkDimPos = chunkDimPosCtor.newInstance(level, pos);
        } else {
            ChunkPos chunkPos = ChunkPos.containing(pos);
            chunkDimPos = chunkDimPosCtor.newInstance(level.dimension(), chunkPos.x(), chunkPos.z());
        }
        return getChunkMethod.invoke(manager, chunkDimPos) != null;
    }
}
