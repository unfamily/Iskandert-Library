package net.unfamily.iskalib.client.marker;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renders AABB edge borders as solid beams with world-space thickness matching
 * {@code 2} texels on a {@code 16×16} block texture ({@code 2/16} of a block).
 * <p>
 * Uses the stage-event {@link PoseStack} translated by {@code -camera}, then world-space
 * vertices — same convention as vanilla block outlines. Do not bake {@code -camera} into a
 * private PoseStack while the level model-view is still active (that makes the box follow
 * the player).
 */
public final class AreaBorderRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaBorderRenderer.class);
    private static final AtomicBoolean LOGGED = new AtomicBoolean();

    /** Semi-transparent magenta used by machine area previews. */
    public static final int DEFAULT_MACHINE_COLOR = 0x80FF00FF;

    /** Cross-section size in blocks: 2 texels on a 16×16 texture. */
    private static final float BORDER_THICKNESS = 2.0f / 16.0f;

    private static final AreaBorderRenderer INSTANCE = new AreaBorderRenderer();
    private final Map<Object, AreaBorder> borders = new ConcurrentHashMap<>();

    private AreaBorderRenderer() {}

    public static AreaBorderRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * Shows a border between two inclusive block corners.
     *
     * @param key            identity for replace/clear (e.g. owner BlockPos)
     * @param corner1        first corner
     * @param corner2        second corner
     * @param colorArgb      ARGB color ({@link #DEFAULT_MACHINE_COLOR} if unsure)
     * @param durationTicks  duration; {@code <= 0} means until cleared
     */
    public void showArea(Object key, BlockPos corner1, BlockPos corner2, int colorArgb, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || key == null || corner1 == null || corner2 == null) {
            return;
        }
        long expire = durationTicks > 0 ? mc.level.getGameTime() + durationTicks : Long.MAX_VALUE;
        borders.put(key, new AreaBorder(corner1.immutable(), corner2.immutable(), colorArgb, expire));
    }

    /** Convenience with {@link #DEFAULT_MACHINE_COLOR}. */
    public void showArea(Object key, BlockPos corner1, BlockPos corner2, int durationTicks) {
        showArea(key, corner1, corner2, DEFAULT_MACHINE_COLOR, durationTicks);
    }

    /** Parses a hex string ({@code #AARRGGBB}, {@code AARRGGBB}, or {@code RRGGBB} with opaque alpha). */
    public static int parseHexColor(String hex, int fallback) {
        if (hex == null || hex.isBlank()) {
            return fallback;
        }
        String s = hex.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        try {
            if (s.length() == 6) {
                return 0xFF000000 | Integer.parseUnsignedInt(s, 16);
            }
            if (s.length() == 8) {
                return (int) Long.parseUnsignedLong(s, 16);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return fallback;
    }

    public void clearArea(Object key) {
        if (key != null) {
            borders.remove(key);
        }
    }

    public void clearAll() {
        borders.clear();
    }

    /**
     * @deprecated use {@link #render(RenderLevelStageEvent.AfterTranslucentBlocks)}
     */
    @Deprecated
    public void renderWorldBorders(float partialTick) {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.warn("AreaBorderRenderer.renderWorldBorders(float) ignored; pass AfterTranslucentBlocks event");
        }
    }

    public void render(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (borders.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        borders.entrySet().removeIf(e -> e.getValue().expirationTime <= now);
        if (borders.isEmpty()) {
            return;
        }

        Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose pose = poseStack.last();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // QUADS + no cull so thin beams remain visible from any angle
        RenderType type = RenderTypes.debugQuads();
        VertexConsumer consumer = buffers.getBuffer(type);

        try {
            for (AreaBorder border : borders.values()) {
                drawThickEdgeBox(consumer, pose, border.corner1, border.corner2, border.color);
            }
            buffers.endBatch(type);
        } finally {
            poseStack.popPose();
        }
    }

    private static void drawThickEdgeBox(VertexConsumer consumer, PoseStack.Pose pose,
                                         BlockPos a, BlockPos b, int color) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX()) + 1;
        int maxY = Math.max(a.getY(), b.getY()) + 1;
        int maxZ = Math.max(a.getZ(), b.getZ()) + 1;

        float ht = BORDER_THICKNESS * 0.5f;
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255.0F;

        drawBeam(consumer, pose, minX, minY, minZ, maxX, minY, minZ, 0, ht, ht, red, green, blue, alpha);
        drawBeam(consumer, pose, minX, minY, maxZ, maxX, minY, maxZ, 0, ht, ht, red, green, blue, alpha);
        drawBeam(consumer, pose, minX, minY, minZ, minX, minY, maxZ, ht, ht, 0, red, green, blue, alpha);
        drawBeam(consumer, pose, maxX, minY, minZ, maxX, minY, maxZ, ht, ht, 0, red, green, blue, alpha);
        drawBeam(consumer, pose, minX, maxY, minZ, maxX, maxY, minZ, 0, ht, ht, red, green, blue, alpha);
        drawBeam(consumer, pose, minX, maxY, maxZ, maxX, maxY, maxZ, 0, ht, ht, red, green, blue, alpha);
        drawBeam(consumer, pose, minX, maxY, minZ, minX, maxY, maxZ, ht, ht, 0, red, green, blue, alpha);
        drawBeam(consumer, pose, maxX, maxY, minZ, maxX, maxY, maxZ, ht, ht, 0, red, green, blue, alpha);
        drawBeam(consumer, pose, minX, minY, minZ, minX, maxY, minZ, ht, 0, ht, red, green, blue, alpha);
        drawBeam(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, ht, 0, ht, red, green, blue, alpha);
        drawBeam(consumer, pose, minX, minY, maxZ, minX, maxY, maxZ, ht, 0, ht, red, green, blue, alpha);
        drawBeam(consumer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, ht, 0, ht, red, green, blue, alpha);
    }

    private static void drawBeam(VertexConsumer consumer, PoseStack.Pose pose,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float hx, float hy, float hz,
                                 float red, float green, float blue, float alpha) {
        float minX = Math.min(x0, x1) - hx;
        float maxX = Math.max(x0, x1) + hx;
        float minY = Math.min(y0, y1) - hy;
        float maxY = Math.max(y0, y1) + hy;
        float minZ = Math.min(z0, z1) - hz;
        float maxZ = Math.max(z0, z1) + hz;

        if (maxX - minX < BORDER_THICKNESS && hx > 0) {
            float mid = (minX + maxX) * 0.5f;
            minX = mid - hx;
            maxX = mid + hx;
        }
        if (maxY - minY < BORDER_THICKNESS && hy > 0) {
            float mid = (minY + maxY) * 0.5f;
            minY = mid - hy;
            maxY = mid + hy;
        }
        if (maxZ - minZ < BORDER_THICKNESS && hz > 0) {
            float mid = (minZ + maxZ) * 0.5f;
            minZ = mid - hz;
            maxZ = mid + hz;
        }

        drawFilledBox(consumer, pose, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
    }

    private static void drawFilledBox(VertexConsumer consumer, PoseStack.Pose pose,
                                      float minX, float minY, float minZ,
                                      float maxX, float maxY, float maxZ,
                                      float red, float green, float blue, float alpha) {
        consumer.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);

        consumer.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);

        consumer.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);

        consumer.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);

        consumer.addVertex(pose, minX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, minX, minY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, minX, maxY, minZ).setColor(red, green, blue, alpha);

        consumer.addVertex(pose, maxX, minY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, maxX, minY, maxZ).setColor(red, green, blue, alpha);
    }

    private record AreaBorder(BlockPos corner1, BlockPos corner2, int color, long expirationTime) {}
}
