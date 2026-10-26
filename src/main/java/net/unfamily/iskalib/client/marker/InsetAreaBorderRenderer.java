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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thick AABB edge border with the entire beam cross-section inside the volume
 * (no half-outside overhang like {@link AreaBorderRenderer}).
 * <p>
 * Thickness is {@code 6/16} of a block. Inclusive block corners map to hull
 * {@code [min, max+1]} in world space.
 */
public final class InsetAreaBorderRenderer {
    /** Semi-transparent magenta used by machine area previews. */
    public static final int DEFAULT_MACHINE_COLOR = 0x80FF00FF;

    /** Cross-section size in blocks: 6 texels on a 16×16 texture. */
    private static final float BORDER_THICKNESS = 6.0f / 16.0f;

    /**
     * Expand the outer hull slightly past the inclusive block AABB so the border sits
     * just outside shell faces (avoids coplanar z-fight without hiding the frame).
     */
    private static final float HULL_OUTSET = 0.01f;

    private static final InsetAreaBorderRenderer INSTANCE = new InsetAreaBorderRenderer();
    private final Map<Object, InsetBorder> borders = new ConcurrentHashMap<>();

    private InsetAreaBorderRenderer() {}

    public static InsetAreaBorderRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * Shows an inset border between two inclusive block corners.
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
        borders.put(key, new InsetBorder(corner1.immutable(), corner2.immutable(), colorArgb, expire));
    }

    /** Convenience with {@link #DEFAULT_MACHINE_COLOR}. */
    public void showArea(Object key, BlockPos corner1, BlockPos corner2, int durationTicks) {
        showArea(key, corner1, corner2, DEFAULT_MACHINE_COLOR, durationTicks);
    }

    public void clearArea(Object key) {
        if (key != null) {
            borders.remove(key);
        }
    }

    public void clearAll() {
        borders.clear();
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
        RenderType type = RenderTypes.debugQuads();
        VertexConsumer consumer = buffers.getBuffer(type);

        try {
            for (InsetBorder border : borders.values()) {
                drawInsetEdgeBox(consumer, pose, border.corner1, border.corner2, border.color);
            }
            buffers.endBatch(type);
        } finally {
            poseStack.popPose();
        }
    }

    private static void drawInsetEdgeBox(VertexConsumer consumer, PoseStack.Pose pose,
                                         BlockPos a, BlockPos b, int color) {
        float minX = Math.min(a.getX(), b.getX()) - HULL_OUTSET;
        float minY = Math.min(a.getY(), b.getY()) - HULL_OUTSET;
        float minZ = Math.min(a.getZ(), b.getZ()) - HULL_OUTSET;
        float maxX = Math.max(a.getX(), b.getX()) + 1 + HULL_OUTSET;
        float maxY = Math.max(a.getY(), b.getY()) + 1 + HULL_OUTSET;
        float maxZ = Math.max(a.getZ(), b.getZ()) + 1 + HULL_OUTSET;

        float t = Math.min(BORDER_THICKNESS, Math.min((maxX - minX) * 0.5f,
                Math.min((maxY - minY) * 0.5f, (maxZ - minZ) * 0.5f)));
        if (t <= 0.0f) {
            return;
        }

        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255.0F;

        // Bottom face edges (grow +Y / inward on Z)
        drawFilledBox(consumer, pose, minX, minY, minZ, maxX, minY + t, minZ + t, red, green, blue, alpha);
        drawFilledBox(consumer, pose, minX, minY, maxZ - t, maxX, minY + t, maxZ, red, green, blue, alpha);
        drawFilledBox(consumer, pose, minX, minY, minZ + t, minX + t, minY + t, maxZ - t, red, green, blue, alpha);
        drawFilledBox(consumer, pose, maxX - t, minY, minZ + t, maxX, minY + t, maxZ - t, red, green, blue, alpha);

        // Top face edges (grow -Y / inward on Z)
        drawFilledBox(consumer, pose, minX, maxY - t, minZ, maxX, maxY, minZ + t, red, green, blue, alpha);
        drawFilledBox(consumer, pose, minX, maxY - t, maxZ - t, maxX, maxY, maxZ, red, green, blue, alpha);
        drawFilledBox(consumer, pose, minX, maxY - t, minZ + t, minX + t, maxY, maxZ - t, red, green, blue, alpha);
        drawFilledBox(consumer, pose, maxX - t, maxY - t, minZ + t, maxX, maxY, maxZ - t, red, green, blue, alpha);

        // Vertical edges between bottom and top rings (avoid double-covering corners already drawn)
        drawFilledBox(consumer, pose, minX, minY + t, minZ, minX + t, maxY - t, minZ + t, red, green, blue, alpha);
        drawFilledBox(consumer, pose, maxX - t, minY + t, minZ, maxX, maxY - t, minZ + t, red, green, blue, alpha);
        drawFilledBox(consumer, pose, minX, minY + t, maxZ - t, minX + t, maxY - t, maxZ, red, green, blue, alpha);
        drawFilledBox(consumer, pose, maxX - t, minY + t, maxZ - t, maxX, maxY - t, maxZ, red, green, blue, alpha);
    }

    private static void drawFilledBox(VertexConsumer consumer, PoseStack.Pose pose,
                                      float minX, float minY, float minZ,
                                      float maxX, float maxY, float maxZ,
                                      float red, float green, float blue, float alpha) {
        if (maxX <= minX || maxY <= minY || maxZ <= minZ) {
            return;
        }
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

    private record InsetBorder(BlockPos corner1, BlockPos corner2, int color, long expirationTime) {}
}
