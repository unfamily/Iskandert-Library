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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-side world markers. Default owned/unowned billboards use a through-wall
 * {@link RenderType} ({@link CompareOp#ALWAYS_PASS}). Callers may request depth-tested
 * markers / boxes so solid geometry occludes them.
 */
public final class MarkRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarkRenderer.class);
    private static final AtomicBoolean THROUGH_WALL_TYPE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean DEPTH_TYPE_LOGGED = new AtomicBoolean();

    private static volatile RenderType markerDrawTypeThroughWall;
    private static volatile RenderType markerDrawTypeDepth;

    private static final MarkRenderer INSTANCE = new MarkRenderer();
    private final Map<BlockPos, MarkBlockData> highlightedBlocks = new ConcurrentHashMap<>();
    private final Map<BlockPos, MarkBlockData> billboardMarkers = new ConcurrentHashMap<>();
    /** Footprint preview markers keyed by builder block position. */
    private final Map<BlockPos, Map<BlockPos, MarkBlockData>> billboardMarkersByOwner = new ConcurrentHashMap<>();
    /** Multi-block filled boxes keyed by owner (list so multiple boxes may share a min corner). */
    private final Map<BlockPos, List<MarkBoxData>> ownedBoxesByOwner = new ConcurrentHashMap<>();

    private MarkRenderer() {}

    public static MarkRenderer getInstance() {
        return INSTANCE;
    }

    public void addHighlightedBlock(BlockPos pos, int color, int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        highlightedBlocks.put(pos, new MarkBlockData(color, mc.level.getGameTime() + durationTicks, true));
    }

    public void addHighlightedBlock(BlockPos pos, int color, int durationTicks, String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        highlightedBlocks.put(pos, new MarkBlockData(color, mc.level.getGameTime() + durationTicks, true, text));
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
        ownedBoxesByOwner.clear();
    }

    public void clearBillboardMarkersForOwner(BlockPos owner) {
        if (owner != null) {
            BlockPos key = owner.immutable();
            billboardMarkersByOwner.remove(key);
            ownedBoxesByOwner.remove(key);
        }
    }

    /** Clears short-lived item preview markers (not scoped to a machine owner block). */
    public void clearUnownedBillboardMarkers() {
        billboardMarkers.clear();
    }

    public void removeBillboardMarkerForOwner(BlockPos owner, BlockPos pos) {
        if (owner == null || pos == null) {
            return;
        }
        Map<BlockPos, MarkBlockData> worldMarkers = billboardMarkersByOwner.get(owner.immutable());
        if (worldMarkers != null) {
            worldMarkers.remove(pos.immutable());
            if (worldMarkers.isEmpty()) {
                billboardMarkersByOwner.remove(owner.immutable());
            }
        }
    }

    /** Through-wall owned marker (default; used by utils). */
    public void addBillboardMarker(BlockPos owner, BlockPos pos, int color, int durationTicks) {
        addBillboardMarker(owner, pos, color, durationTicks, true);
    }

    public void addBillboardMarker(BlockPos owner, BlockPos pos, int color, int durationTicks, boolean throughWalls) {
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
                    .put(pos.immutable(), new MarkBlockData(color, expire, throughWalls));
        };
        if (mc.level == null) {
            mc.execute(add);
        } else {
            add.run();
        }
    }

    /**
     * Filled AABB from inclusive {@code min} to inclusive {@code max} (one draw for many cells).
     */
    public void addOwnedBox(BlockPos owner, BlockPos min, BlockPos max, int color, int durationTicks, boolean throughWalls) {
        if (owner == null || min == null || max == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Runnable add = () -> {
            if (mc.level == null) {
                return;
            }
            long expire = durationTicks > 0 ? mc.level.getGameTime() + durationTicks : Long.MAX_VALUE;
            int x0 = Math.min(min.getX(), max.getX());
            int y0 = Math.min(min.getY(), max.getY());
            int z0 = Math.min(min.getZ(), max.getZ());
            int x1 = Math.max(min.getX(), max.getX());
            int y1 = Math.max(min.getY(), max.getY());
            int z1 = Math.max(min.getZ(), max.getZ());
            BlockPos lo = new BlockPos(x0, y0, z0);
            BlockPos hi = new BlockPos(x1, y1, z1);
            ownedBoxesByOwner
                    .computeIfAbsent(owner.immutable(), k -> new CopyOnWriteArrayList<>())
                    .add(new MarkBoxData(lo, hi, color, expire, throughWalls));
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

    public void renderWorldMarkers(float partialTick) {
        if (highlightedBlocks.isEmpty() && billboardMarkers.isEmpty()
                && billboardMarkersByOwner.isEmpty() && ownedBoxesByOwner.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        long currentTime = mc.level.getGameTime();
        billboardMarkersByOwner.entrySet().removeIf(entry -> mc.level.getBlockState(entry.getKey()).isAir());
        ownedBoxesByOwner.entrySet().removeIf(entry -> mc.level.getBlockState(entry.getKey()).isAir());
        highlightedBlocks.entrySet().removeIf(entry -> entry.getValue().expirationTime <= currentTime);
        billboardMarkers.entrySet().removeIf(entry -> entry.getValue().expirationTime <= currentTime);
        billboardMarkersByOwner.values().forEach(map ->
                map.entrySet().removeIf(entry -> entry.getValue().expirationTime <= currentTime));
        billboardMarkersByOwner.entrySet().removeIf(e -> e.getValue().isEmpty());
        ownedBoxesByOwner.values().forEach(list ->
                list.removeIf(box -> box.expirationTime <= currentTime));
        ownedBoxesByOwner.entrySet().removeIf(e -> e.getValue().isEmpty());

        if (highlightedBlocks.isEmpty() && billboardMarkers.isEmpty()
                && billboardMarkersByOwner.isEmpty() && ownedBoxesByOwner.isEmpty()) {
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
        if (!billboardMarkersByOwner.isEmpty() || !ownedBoxesByOwner.isEmpty()) {
            renderOwnedMarkers(mc, pose, true);
            renderOwnedMarkers(mc, pose, false);
        }
    }

    private void renderOwnedMarkers(Minecraft mc, PoseStack.Pose pose, boolean throughWallsPass) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean hasVertices = false;
        for (Map<BlockPos, MarkBlockData> worldMarkers : billboardMarkersByOwner.values()) {
            for (Map.Entry<BlockPos, MarkBlockData> entry : worldMarkers.entrySet()) {
                if (entry.getValue().throughWalls != throughWallsPass) {
                    continue;
                }
                drawSmallCube(bufferBuilder, pose, entry.getKey(), entry.getValue().color);
                hasVertices = true;
            }
        }
        for (List<MarkBoxData> boxes : ownedBoxesByOwner.values()) {
            for (MarkBoxData box : boxes) {
                if (box.throughWalls != throughWallsPass) {
                    continue;
                }
                drawFilledBox(bufferBuilder, pose, box.min, box.max, box.color);
                hasVertices = true;
            }
        }
        if (hasVertices) {
            try (MeshData mesh = bufferBuilder.buildOrThrow()) {
                (throughWallsPass ? markerDrawTypeThroughWall() : markerDrawTypeDepth()).draw(mesh);
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
                markerDrawTypeThroughWall().draw(mesh);
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
                markerDrawTypeThroughWall().draw(mesh);
            }
        }
    }

    private static RenderType markerDrawTypeThroughWall() {
        RenderType cached = markerDrawTypeThroughWall;
        if (cached != null) {
            return cached;
        }
        synchronized (MarkRenderer.class) {
            cached = markerDrawTypeThroughWall;
            if (cached != null) {
                return cached;
            }
            markerDrawTypeThroughWall = cached = createMarkerRenderType(true);
            return cached;
        }
    }

    private static RenderType markerDrawTypeDepth() {
        RenderType cached = markerDrawTypeDepth;
        if (cached != null) {
            return cached;
        }
        synchronized (MarkRenderer.class) {
            cached = markerDrawTypeDepth;
            if (cached != null) {
                return cached;
            }
            markerDrawTypeDepth = cached = createMarkerRenderType(false);
            return cached;
        }
    }

    @SuppressWarnings("deprecation")
    private static RenderType createMarkerRenderType(boolean throughWalls) {
        try {
            RenderPipeline base = RenderTypes.debugFilledBox().pipeline();
            DepthStencilState depth = throughWalls
                    ? new DepthStencilState(CompareOp.ALWAYS_PASS, false)
                    : new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false);
            RenderPipeline.Snippet snippet = new RenderPipeline.Snippet(
                    Optional.of(base.getVertexShader()),
                    Optional.of(base.getFragmentShader()),
                    Optional.of(base.getShaderDefines()),
                    Optional.of(base.getSamplers()),
                    Optional.of(base.getUniforms()),
                    Optional.of(base.getColorTargetState()),
                    Optional.of(depth),
                    Optional.of(base.getPolygonMode()),
                    Optional.of(base.isCull()),
                    Optional.of(base.getVertexFormat()),
                    Optional.of(base.getVertexFormatMode()));
            String pipeId = throughWalls ? "iska_lib:pipeline/markers_through_wall" : "iska_lib:pipeline/markers_depth";
            String typeName = throughWalls ? "iska_lib/through_wall_markers" : "iska_lib/depth_markers";
            RenderPipeline pipeline = RenderPipeline.builder(snippet)
                    .withLocation(Identifier.parse(pipeId))
                    .build();
            RenderSetup setup = RenderSetup.builder(pipeline).createRenderSetup();
            Method create = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
            create.setAccessible(true);
            return (RenderType) create.invoke(null, typeName, setup);
        } catch (Throwable t) {
            AtomicBoolean logged = throughWalls ? THROUGH_WALL_TYPE_LOGGED : DEPTH_TYPE_LOGGED;
            if (logged.compareAndSet(false, true)) {
                LOGGER.warn("Could not build marker RenderType (throughWalls={}); falling back to debugFilledBox()",
                        throughWalls, t);
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
        emitBoxQuads(bufferBuilder, pose, x, y, z, x + size, y + size, z + size, color);
    }

    private static void drawCube(BufferBuilder bufferBuilder, PoseStack.Pose pose, BlockPos pos, int color) {
        float x = pos.getX();
        float y = pos.getY();
        float z = pos.getZ();
        emitBoxQuads(bufferBuilder, pose, x, y, z, x + 1, y + 1, z + 1, color);
    }

    /** Inclusive block corners; inset 1/16 to reduce z-fight with solids. */
    private static void drawFilledBox(BufferBuilder bufferBuilder, PoseStack.Pose pose,
                                      BlockPos min, BlockPos max, int color) {
        float inset = 1.0f / 16.0f;
        float x0 = min.getX() + inset;
        float y0 = min.getY() + inset;
        float z0 = min.getZ() + inset;
        float x1 = max.getX() + 1.0f - inset;
        float y1 = max.getY() + 1.0f - inset;
        float z1 = max.getZ() + 1.0f - inset;
        emitBoxQuads(bufferBuilder, pose, x0, y0, z0, x1, y1, z1, color);
    }

    private static void emitBoxQuads(BufferBuilder bufferBuilder, PoseStack.Pose pose,
                                     float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255.0F;

        bufferBuilder.addVertex(pose, x0, y0, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x1, y0, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x1, y0, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x0, y0, z1).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x0, y1, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x0, y1, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x1, y1, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x1, y1, z0).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x0, y0, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x0, y1, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x1, y1, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x1, y0, z0).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x0, y0, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x1, y0, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x1, y1, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x0, y1, z1).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x0, y0, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x0, y0, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x0, y1, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x0, y1, z0).setColor(red, green, blue, alpha);

        bufferBuilder.addVertex(pose, x1, y0, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x1, y1, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x1, y1, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(pose, x1, y0, z1).setColor(red, green, blue, alpha);
    }

    private static final class MarkBlockData {
        final int color;
        final long expirationTime;
        final boolean throughWalls;
        final String text;

        MarkBlockData(int color, long expirationTime, boolean throughWalls) {
            this(color, expirationTime, throughWalls, null);
        }

        MarkBlockData(int color, long expirationTime, boolean throughWalls, String text) {
            this.color = color;
            this.expirationTime = expirationTime;
            this.throughWalls = throughWalls;
            this.text = text;
        }
    }

    private static final class MarkBoxData {
        final BlockPos min;
        final BlockPos max;
        final int color;
        final long expirationTime;
        final boolean throughWalls;

        MarkBoxData(BlockPos min, BlockPos max, int color, long expirationTime, boolean throughWalls) {
            this.min = min;
            this.max = max;
            this.color = color;
            this.expirationTime = expirationTime;
            this.throughWalls = throughWalls;
        }
    }
}
