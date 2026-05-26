package net.unfamily.iskalib.client.marker;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-side world markers using a {@link RenderType} cloned from {@link RenderTypes#debugFilledBox()} with a depth
 * state that does not occlude behind terrain ({@link CompareOp#ALWAYS_PASS}, no depth write) so markers stay visible
 * through solid blocks.
 * <p>
 * Vertices use {@link com.mojang.blaze3d.vertex.VertexConsumer#addVertex(com.mojang.blaze3d.vertex.PoseStack.Pose, float, float, float)}
 * with <strong>world-space</strong> block coordinates and a {@link PoseStack} that first applies {@code translate(-camera)}
 * (same convention as vanilla world rendering). {@link net.minecraft.client.renderer.rendertype.RenderType#draw} still
 * applies {@link com.mojang.blaze3d.systems.RenderSystem#getModelViewMatrix()} for the active pass.
 * <p>
 * Legacy {@code block_display} markers: {@link net.unfamily.iskalib.marker.ScannerMarkerCleanup}.
 */
public final class MarkRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarkRenderer.class);
    private static final AtomicBoolean THROUGH_WALL_TYPE_LOGGED = new AtomicBoolean();

    private static volatile RenderType markerDrawType;

    private static final MarkRenderer INSTANCE = new MarkRenderer();
    private final Map<BlockPos, MarkBlockData> highlightedBlocks = new ConcurrentHashMap<>();
    private final Map<BlockPos, MarkBlockData> billboardMarkers = new ConcurrentHashMap<>();
    /** Footprint preview markers keyed by builder block position. */
    private final Map<BlockPos, Map<BlockPos, MarkBlockData>> billboardMarkersByOwner = new ConcurrentHashMap<>();

    private MarkRenderer() {}

    public static MarkRenderer getInstance() {
        return INSTANCE;
    }

    public void addHighlightedBlock(BlockPos pos, int color, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        highlightedBlocks.put(pos, new MarkBlockData(color, mc.level.getGameTime() + durationTicks));
    }

    public void addHighlightedBlock(BlockPos pos, int color, int durationTicks, String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        highlightedBlocks.put(pos, new MarkBlockData(color, mc.level.getGameTime() + durationTicks, false, text));
    }

    public void addBillboardMarker(BlockPos pos, int color, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            mc.execute(() -> {
                if (Minecraft.getInstance().level != null) {
                    long t = Minecraft.getInstance().level.getGameTime();
                    billboardMarkers.put(pos, new MarkBlockData(color, t + durationTicks, true));
                }
            });
        } else {
            long t = mc.level.getGameTime();
            billboardMarkers.put(pos, new MarkBlockData(color, t + durationTicks, true));
        }
    }

    public void addBillboardMarker(BlockPos pos, int color, int durationTicks, String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        billboardMarkers.put(pos, new MarkBlockData(color, mc.level.getGameTime() + durationTicks, true, text));
    }

    public void removeHighlightedBlock(BlockPos pos) {
        highlightedBlocks.remove(pos);
    }

    public void removeBillboardMarker(BlockPos pos) {
        billboardMarkers.remove(pos);
    }

    public void clearHighlightedBlocks() {
        highlightedBlocks.clear();
        billboardMarkers.clear();
        billboardMarkersByOwner.clear();
    }

    public void clearBillboardMarkersForOwner(BlockPos owner) {
        if (owner != null) {
            billboardMarkersByOwner.remove(owner.immutable());
        }
    }

    public void addBillboardMarker(BlockPos owner, BlockPos pos, int color, int durationTicks) {
        if (owner == null) {
            addBillboardMarker(pos, color, durationTicks);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Runnable add = () -> {
            if (mc.level == null) {
                return;
            }
            long expire = durationTicks > 0 ? mc.level.getGameTime() + durationTicks : Long.MAX_VALUE;
            billboardMarkersByOwner
                    .computeIfAbsent(owner.immutable(), k -> new ConcurrentHashMap<>())
                    .put(pos.immutable(), new MarkBlockData(color, expire, true));
        };
        if (mc.level == null) {
            mc.execute(add);
        } else {
            add.run();
        }
    }

    public void checkPlayerLookingAtMarker() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        double maxDistance = 256.0;
        HitResult hitResult = mc.player.pick(maxDistance, 0.0F, false);

        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hitResult).getBlockPos();

            MarkBlockData data = highlightedBlocks.get(pos);
            if (data != null && data.text != null) {
                double distance = mc.player.position().distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                mc.player.sendOverlayMessage(Component.literal(data.text + " (" + String.format("%.1f", distance) + "m)"));
                return;
            }

            data = billboardMarkers.get(pos);
            if (data != null && data.text != null) {
                double distance = mc.player.position().distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                mc.player.sendOverlayMessage(Component.literal(data.text + " (" + String.format("%.1f", distance) + "m)"));
                return;
            }
        }

        checkDistantMarkers(mc, maxDistance);
    }

    private void checkDistantMarkers(Minecraft mc, double maxDistance) {
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
        Vec3 lookVec = mc.player.getViewVector(1.0F);

        BlockPos nearestBlockPos = null;
        double nearestDistance = Double.MAX_VALUE;
        String nearestText = null;
        boolean isNearestBillboard = false;

        for (Map.Entry<BlockPos, MarkBlockData> entry : highlightedBlocks.entrySet()) {
            if (entry.getValue().text != null) {
                BlockPos pos = entry.getKey();
                if (mc.level.getBlockState(pos).isAir()) {
                    continue;
                }

                Vec3 blockVec = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                Vec3 toBlock = blockVec.subtract(cameraPos);
                double distance = toBlock.length();

                if (distance <= maxDistance) {
                    Vec3 toBlockNorm = toBlock.normalize();
                    double dotProduct = lookVec.dot(toBlockNorm);
                    double minDotProduct = calculateMinDotProduct(distance, maxDistance);

                    if (dotProduct > minDotProduct) {
                        double priority = dotProduct / (distance * 0.1);
                        if (nearestBlockPos == null || priority > nearestDistance) {
                            nearestDistance = priority;
                            nearestBlockPos = pos;
                            nearestText = entry.getValue().text;
                            isNearestBillboard = false;
                        }
                    }
                }
            }
        }

        for (Map.Entry<BlockPos, MarkBlockData> entry : billboardMarkers.entrySet()) {
            if (entry.getValue().text != null) {
                BlockPos pos = entry.getKey();
                Vec3 blockVec = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                Vec3 toBlock = blockVec.subtract(cameraPos);
                double distance = toBlock.length();

                if (distance <= maxDistance) {
                    Vec3 toBlockNorm = toBlock.normalize();
                    double dotProduct = lookVec.dot(toBlockNorm);
                    double minDotProduct = calculateMinDotProduct(distance, maxDistance);

                    if (dotProduct > minDotProduct) {
                        double priority = dotProduct / (distance * 0.1);
                        if (nearestBlockPos == null || priority > nearestDistance) {
                            nearestDistance = priority;
                            nearestBlockPos = pos;
                            nearestText = entry.getValue().text;
                            isNearestBillboard = true;
                        }
                    }
                }
            }
        }

        if (nearestBlockPos != null && nearestText != null) {
            double actualDistance = mc.player.position().distanceTo(
                    new Vec3(nearestBlockPos.getX() + 0.5, nearestBlockPos.getY() + 0.5, nearestBlockPos.getZ() + 0.5));

            if (!isNearestBillboard && mc.level.getBlockState(nearestBlockPos).isAir()) {
                mc.player.sendOverlayMessage(Component.literal(nearestText));
            } else {
                String distanceText = String.format("%.1f", actualDistance) + "m";
                mc.player.sendOverlayMessage(Component.literal(nearestText + " (" + distanceText + ")"));
            }
        }
    }

    private double calculateMinDotProduct(double distance, double maxDistance) {
        double minAngleDegrees = 5.0;
        double maxAngleDegrees = 20.0;
        double normalizedDistance = Math.min(distance, maxDistance) / maxDistance;
        double angleDegrees = minAngleDegrees + (maxAngleDegrees - minAngleDegrees) * normalizedDistance;
        return Math.cos(Math.toRadians(angleDegrees));
    }

    /**
     * Called from the level render pipeline (e.g. after translucent blocks). Builds a short-lived pose stack with
     * {@code translate(-camera)} so block-space coordinates anchor correctly.
     *
     * @param partialTick reserved for future interpolation
     */
    public void renderWorldMarkers(float partialTick) {
        if (highlightedBlocks.isEmpty() && billboardMarkers.isEmpty() && billboardMarkersByOwner.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        long currentTime = mc.level.getGameTime();
        highlightedBlocks.entrySet().removeIf(entry -> entry.getValue().expirationTime <= currentTime);
        billboardMarkers.entrySet().removeIf(entry -> entry.getValue().expirationTime <= currentTime);
        billboardMarkersByOwner.values().forEach(map ->
                map.entrySet().removeIf(entry -> entry.getValue().expirationTime <= currentTime));
        billboardMarkersByOwner.entrySet().removeIf(e -> e.getValue().isEmpty());

        if (highlightedBlocks.isEmpty() && billboardMarkers.isEmpty() && billboardMarkersByOwner.isEmpty()) {
            return;
        }

        checkPlayerLookingAtMarker();

        PoseStack poseStack = new PoseStack();
        var cam = mc.gameRenderer.getMainCamera().position();
        poseStack.translate(-(float) cam.x, -(float) cam.y, -(float) cam.z);
        PoseStack.Pose pose = poseStack.last();

        if (!highlightedBlocks.isEmpty()) {
            renderCubeHighlights(mc, pose);
        }
        if (!billboardMarkers.isEmpty()) {
            renderBillboardMarkers(mc, pose);
        }
        if (!billboardMarkersByOwner.isEmpty()) {
            renderOwnedBillboardMarkers(mc, pose);
        }
    }

    private void renderOwnedBillboardMarkers(Minecraft mc, PoseStack.Pose pose) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean hasVertices = false;
        for (Map<BlockPos, MarkBlockData> worldMarkers : billboardMarkersByOwner.values()) {
            for (Map.Entry<BlockPos, MarkBlockData> entry : worldMarkers.entrySet()) {
                drawSmallCube(bufferBuilder, pose, entry.getKey(), entry.getValue().color);
                hasVertices = true;
            }
        }
        if (hasVertices) {
            try (MeshData mesh = bufferBuilder.buildOrThrow()) {
                markerDrawType().draw(mesh);
            }
        }
    }

    private void renderCubeHighlights(Minecraft mc, PoseStack.Pose pose) {
        boolean hasValidBlocks = false;
        for (Map.Entry<BlockPos, MarkBlockData> entry : highlightedBlocks.entrySet()) {
            if (!mc.level.getBlockState(entry.getKey()).isAir()) {
                hasValidBlocks = true;
                break;
            }
        }
        if (!hasValidBlocks) {
            return;
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean hasVertices = false;

        for (Map.Entry<BlockPos, MarkBlockData> entry : highlightedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            int color = entry.getValue().color;
            if (mc.level.getBlockState(pos).isAir()) {
                continue;
            }
            drawCube(bufferBuilder, pose, pos, color);
            hasVertices = true;
        }

        if (hasVertices) {
            try (MeshData mesh = bufferBuilder.buildOrThrow()) {
                markerDrawType().draw(mesh);
            }
        }
    }

    private void renderBillboardMarkers(Minecraft mc, PoseStack.Pose pose) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean hasVertices = false;

        for (Map.Entry<BlockPos, MarkBlockData> entry : billboardMarkers.entrySet()) {
            BlockPos pos = entry.getKey();
            int color = entry.getValue().color;
            drawSmallCube(bufferBuilder, pose, pos, color);
            hasVertices = true;
        }

        if (hasVertices) {
            try (MeshData mesh = bufferBuilder.buildOrThrow()) {
                markerDrawType().draw(mesh);
            }
        }
    }

    /**
     * Same shaders and vertex layout as {@link RenderTypes#debugFilledBox()}, but depth test always passes and depth
     * is not written so filled marker quads are not hidden behind blocks.
     */
    private static RenderType markerDrawType() {
        RenderType cached = markerDrawType;
        if (cached != null) {
            return cached;
        }
        synchronized (MarkRenderer.class) {
            cached = markerDrawType;
            if (cached != null) {
                return cached;
            }
            markerDrawType = cached = createThroughWallMarkerRenderType();
            return cached;
        }
    }

    @SuppressWarnings("deprecation")
    private static RenderType createThroughWallMarkerRenderType() {
        try {
            RenderPipeline base = RenderTypes.debugFilledBox().pipeline();
            RenderPipeline.Snippet snippet = new RenderPipeline.Snippet(
                    Optional.of(base.getVertexShader()),
                    Optional.of(base.getFragmentShader()),
                    Optional.of(base.getShaderDefines()),
                    Optional.of(base.getSamplers()),
                    Optional.of(base.getUniforms()),
                    Optional.of(base.getColorTargetState()),
                    Optional.of(new DepthStencilState(CompareOp.ALWAYS_PASS, false)),
                    Optional.of(base.getPolygonMode()),
                    Optional.of(base.isCull()),
                    Optional.of(base.getVertexFormat()),
                    Optional.of(base.getVertexFormatMode()));
            RenderPipeline pipeline = RenderPipeline.builder(snippet)
                    .withLocation(Identifier.parse("iska_lib:pipeline/markers_through_wall"))
                    .build();
            RenderSetup setup = RenderSetup.builder(pipeline).createRenderSetup();
            Method create = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
            create.setAccessible(true);
            return (RenderType) create.invoke(null, "iska_lib/through_wall_markers", setup);
        } catch (Throwable t) {
            if (THROUGH_WALL_TYPE_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn("Could not build through-wall marker RenderType; falling back to debugFilledBox()", t);
            }
            return RenderTypes.debugFilledBox();
        }
    }

    private static void drawSmallCube(BufferBuilder bufferBuilder, PoseStack.Pose pose, BlockPos pos, int color) {
        float size = 12.0f / 16.0f;
        float halfSize = size / 2.0f;
        float x = pos.getX() + 0.5f - halfSize;
        float y = pos.getY() + 0.5f - halfSize;
        float z = pos.getZ() + 0.5f - halfSize;

        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255.0F;

        bufferBuilder.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + size, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + size, y, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x, y, z + size).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x, y + size, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x, y + size, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + size, y + size, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + size, y + size, z).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x, y + size, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + size, y + size, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + size, y, z).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x, y, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + size, y, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + size, y + size, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x, y + size, z + size).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x, y, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x, y + size, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x, y + size, z).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x + size, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + size, y + size, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + size, y + size, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + size, y, z + size).setColor(red, green, blue, alpha);
    }

    private static void drawCube(BufferBuilder bufferBuilder, PoseStack.Pose pose, BlockPos pos, int color) {
        float x = pos.getX();
        float y = pos.getY();
        float z = pos.getZ();

        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255.0F;

        bufferBuilder.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + 1, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + 1, y, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x, y, z + 1).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x, y + 1, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x, y + 1, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + 1, y + 1, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + 1, y + 1, z).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x, y + 1, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + 1, y + 1, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + 1, y, z).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x, y, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + 1, y, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + 1, y + 1, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x, y + 1, z + 1).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x, y, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x, y + 1, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x, y + 1, z).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x + 1, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + 1, y + 1, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + 1, y + 1, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x + 1, y, z + 1).setColor(red, green, blue, alpha);
    }

    private static final class MarkBlockData {
        final int color;
        final long expirationTime;
        @SuppressWarnings("unused")
        final boolean isSmallCube;
        final String text;

        MarkBlockData(int color, long expirationTime) {
            this.color = color;
            this.expirationTime = expirationTime;
            this.isSmallCube = false;
            this.text = null;
        }

        MarkBlockData(int color, long expirationTime, boolean isSmallCube) {
            this.color = color;
            this.expirationTime = expirationTime;
            this.isSmallCube = isSmallCube;
            this.text = null;
        }

        MarkBlockData(int color, long expirationTime, boolean isSmallCube, String text) {
            this.color = color;
            this.expirationTime = expirationTime;
            this.isSmallCube = isSmallCube;
            this.text = text;
        }
    }
}
