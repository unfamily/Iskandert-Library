package net.unfamily.iskalib.explosion;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modular lag-friendly progressive elliptical explosions (shared library implementation).
 */
public final class ExplosionSystem {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<UUID, ExplosionData> ACTIVE_EXPLOSIONS = new ConcurrentHashMap<>();

    /**
     * Subscribed from {@link net.unfamily.iskalib.IskaLib} via {@code NeoForge.EVENT_BUS}.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        Iterator<Map.Entry<UUID, ExplosionData>> iterator = ACTIVE_EXPLOSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ExplosionData> entry = iterator.next();
            ExplosionData explosion = entry.getValue();

            explosion.tickCount++;

            if (explosion.tickInterval == 0) {
                while (explosion.currentRadius <= explosion.maxRadius) {
                    processExplosionLayer(explosion);
                }
                iterator.remove();
                LOGGER.debug("Instant explosion {} completed", entry.getKey());
            } else if (explosion.tickCount >= explosion.tickInterval) {
                explosion.tickCount = 0;

                if (processExplosionLayer(explosion)) {
                    iterator.remove();
                    LOGGER.debug("Explosion {} completed", entry.getKey());
                }
            }
        }
    }

    public static UUID createExplosion(
            ServerLevel level,
            BlockPos center,
            int horizontalRadius,
            int verticalRadius,
            int tickInterval) {
        return createExplosion(level, center, horizontalRadius, verticalRadius, tickInterval, 0.0f, false);
    }

    public static UUID createExplosion(
            ServerLevel level,
            BlockPos center,
            int horizontalRadius,
            int verticalRadius,
            int tickInterval,
            float explosionDamage,
            boolean breakUnbreakable) {

        ExplosionData explosion = new ExplosionData(
                UUID.randomUUID(),
                level,
                center,
                horizontalRadius,
                verticalRadius,
                tickInterval,
                explosionDamage,
                breakUnbreakable);

        ACTIVE_EXPLOSIONS.put(explosion.id, explosion);

        LOGGER.info(
                "Created explosion {} at center {} with radii {}x{}, interval {} ticks, damage {}, break unbreakable: {}",
                explosion.id,
                center,
                horizontalRadius,
                verticalRadius,
                tickInterval,
                explosionDamage,
                breakUnbreakable);

        return explosion.id;
    }

    public static int stopAllExplosions() {
        int count = ACTIVE_EXPLOSIONS.size();
        ACTIVE_EXPLOSIONS.clear();
        LOGGER.info("Stopped {} active explosions", count);
        return count;
    }

    public static int getActiveExplosionCount() {
        return ACTIVE_EXPLOSIONS.size();
    }

    private static boolean processExplosionLayer(ExplosionData explosion) {
        if (explosion.currentRadius > explosion.maxRadius) {
            return true;
        }

        int blocksProcessed = 0;
        int entitiesKilled = 0;

        List<BlockPos> currentLayerBlocks = calculateLayerBlocks(explosion, explosion.currentRadius);

        for (BlockPos pos : currentLayerBlocks) {
            if (explosion.level.isInWorldBounds(pos)) {
                BlockState currentState = explosion.level.getBlockState(pos);

                boolean shouldDestroy;
                if (explosion.breakUnbreakable) {
                    shouldDestroy = !currentState.isAir();
                } else {
                    shouldDestroy = !currentState.is(Blocks.BEDROCK)
                            && !currentState.is(Blocks.BARRIER)
                            && currentState.getDestroySpeed(explosion.level, pos) >= 0;
                }

                if (shouldDestroy) {
                    explosion.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    blocksProcessed++;
                }

                if (explosion.explosionDamage > 0) {
                    AABB blockAABB = new AABB(pos);
                    List<Entity> entities = explosion.level.getEntitiesOfClass(Entity.class, blockAABB);

                    for (Entity entity : entities) {
                        if (entity instanceof LivingEntity livingEntity) {
                            DamageSource explosionDamageSource = explosion.level.damageSources().explosion(null, null);
                            livingEntity.hurt(explosionDamageSource, explosion.explosionDamage);
                            entitiesKilled++;
                        }
                    }
                }
            }
        }

        explosion.currentRadius++;

        String logMessage = "Processed radius {}/{} of explosion {} - {} blocks destroyed";
        if (explosion.explosionDamage > 0) {
            logMessage += ", {} entities damaged";
            LOGGER.debug(logMessage, explosion.currentRadius, explosion.maxRadius, explosion.id, blocksProcessed, entitiesKilled);
        } else {
            LOGGER.debug(logMessage, explosion.currentRadius, explosion.maxRadius, explosion.id, blocksProcessed);
        }

        return explosion.currentRadius > explosion.maxRadius;
    }

    private static List<BlockPos> calculateLayerBlocks(ExplosionData explosion, int radius) {
        List<BlockPos> layerBlocks = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) > explosion.horizontalRadius
                            || Math.abs(y) > explosion.verticalRadius
                            || Math.abs(z) > explosion.horizontalRadius) {
                        continue;
                    }

                    double distanceX = (double) x / explosion.horizontalRadius;
                    double distanceY = (double) y / explosion.verticalRadius;
                    double distanceZ = (double) z / explosion.horizontalRadius;

                    double ellipseDistance = distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ;

                    if (ellipseDistance <= 1.0) {
                        double euclideanDistance = Math.sqrt(x * x + y * y + z * z);

                        if (Math.abs(euclideanDistance - radius) < 0.5) {
                            BlockPos pos = explosion.center.offset(x, y, z);
                            layerBlocks.add(pos);
                        }
                    }
                }
            }
        }

        return layerBlocks;
    }

    private static final class ExplosionData {
        final UUID id;
        final ServerLevel level;
        final BlockPos center;
        final int horizontalRadius;
        final int verticalRadius;
        final int tickInterval;
        final float explosionDamage;
        final boolean breakUnbreakable;

        final int maxRadius;
        int currentRadius = 0;
        int tickCount = 0;

        ExplosionData(
                UUID id,
                ServerLevel level,
                BlockPos center,
                int horizontalRadius,
                int verticalRadius,
                int tickInterval,
                float explosionDamage,
                boolean breakUnbreakable) {
            this.id = id;
            this.level = level;
            this.center = center;
            this.horizontalRadius = horizontalRadius;
            this.verticalRadius = verticalRadius;
            this.tickInterval = tickInterval;
            this.explosionDamage = explosionDamage;
            this.breakUnbreakable = breakUnbreakable;
            this.maxRadius = Math.max(horizontalRadius, verticalRadius);
        }
    }

    private ExplosionSystem() {}
}
