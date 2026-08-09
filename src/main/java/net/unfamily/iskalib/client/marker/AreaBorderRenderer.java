package net.unfamily.iskalib.client.marker;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders AABB edge borders as solid beams with world-space thickness matching
 * {@code 2} texels on a {@code 16×16} block texture ({@code 2/16} of a block).
 * <p>
 * Uses the NeoForge {@link RenderLevelStageEvent} pattern: translate the event
 * {@link PoseStack} by {@code -camera}, then submit <strong>world</strong> coordinates.
 * Do not subtract the camera from vertices and do not force an identity model-view —
 * that makes the box follow the player.
 */
public final class AreaBorderRenderer {
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
     * @deprecated use {@link #render(RenderLevelStageEvent)}
     */
    @Deprecated
    public void render(PoseStack poseStack, float partialTick) {
        // Legacy entry: cannot anchor correctly without the stage event matrices/camera.
    }

    public void render(RenderLevelStageEvent event) {
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

        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.debugFilledBox();
        VertexConsumer consumer = buffers.getBuffer(type);

        RenderSystem.disableDepthTest();
        try {
            for (AreaBorder border : borders.values()) {
                drawThickEdgeBox(poseStack, consumer, border.corner1, border.corner2, border.color);
            }
            buffers.endBatch(type);
        } finally {
            RenderSystem.enableDepthTest();
            poseStack.popPose();
        }
    }

    private static void drawThickEdgeBox(PoseStack poseStack, VertexConsumer consumer,
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

        // Bottom face edges
        drawBeam(poseStack, consumer, minX, minY, minZ, maxX, minY, minZ, 0, ht, ht, red, green, blue, alpha);
        drawBeam(poseStack, consumer, minX, minY, maxZ, maxX, minY, maxZ, 0, ht, ht, red, green, blue, alpha);
        drawBeam(poseStack, consumer, minX, minY, minZ, minX, minY, maxZ, ht, ht, 0, red, green, blue, alpha);
        drawBeam(poseStack, consumer, maxX, minY, minZ, maxX, minY, maxZ, ht, ht, 0, red, green, blue, alpha);
        // Top face edges
        drawBeam(poseStack, consumer, minX, maxY, minZ, maxX, maxY, minZ, 0, ht, ht, red, green, blue, alpha);
        drawBeam(poseStack, consumer, minX, maxY, maxZ, maxX, maxY, maxZ, 0, ht, ht, red, green, blue, alpha);
        drawBeam(poseStack, consumer, minX, maxY, minZ, minX, maxY, maxZ, ht, ht, 0, red, green, blue, alpha);
        drawBeam(poseStack, consumer, maxX, maxY, minZ, maxX, maxY, maxZ, ht, ht, 0, red, green, blue, alpha);
        // Vertical edges
        drawBeam(poseStack, consumer, minX, minY, minZ, minX, maxY, minZ, ht, 0, ht, red, green, blue, alpha);
        drawBeam(poseStack, consumer, maxX, minY, minZ, maxX, maxY, minZ, ht, 0, ht, red, green, blue, alpha);
        drawBeam(poseStack, consumer, minX, minY, maxZ, minX, maxY, maxZ, ht, 0, ht, red, green, blue, alpha);
        drawBeam(poseStack, consumer, maxX, minY, maxZ, maxX, maxY, maxZ, ht, 0, ht, red, green, blue, alpha);
    }

    private static void drawBeam(PoseStack poseStack, VertexConsumer consumer,
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

        LevelRenderer.addChainedFilledBoxVertices(
                poseStack, consumer, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
    }

    private record AreaBorder(BlockPos corner1, BlockPos corner2, int color, long expirationTime) {}
}
