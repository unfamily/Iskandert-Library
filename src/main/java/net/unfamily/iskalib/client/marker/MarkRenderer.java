package net.unfamily.iskalib.client.marker;

import com.mojang.blaze3d.systems.RenderSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Renderer for visible blocks through walls
 */
public class MarkRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarkRenderer.class);
    private static final MarkRenderer INSTANCE = new MarkRenderer();
    private final Map<BlockPos, MarkBlockData> highlightedBlocks = new ConcurrentHashMap<>();
    private final Map<BlockPos, MarkBlockData> billboardMarkers = new ConcurrentHashMap<>();
    private final Map<BlockPos, Map<BlockPos, MarkBlockData>> billboardMarkersByOwner = new ConcurrentHashMap<>();
    /** Multi-block filled boxes keyed by owner (list so multiple boxes may share a min corner). */
    private final Map<BlockPos, List<MarkBoxData>> ownedBoxesByOwner = new ConcurrentHashMap<>();

    private MarkRenderer() {}
    
    public static MarkRenderer getInstance() {
        return INSTANCE;
    }
    
    /**
     * Add a block to highlight
     * @param pos Block position
     * @param color Color in ARGB format (0xAARRGGBB)
     * @param durationTicks Duration in tick (20 tick = 1 second)
     */
    public void addHighlightedBlock(BlockPos pos, int color, int durationTicks) {
        // Add the block to the map
        highlightedBlocks.put(pos, new MarkBlockData(color, Minecraft.getInstance().level.getGameTime() + durationTicks));
    }
    
    /**
     * Add a block to highlight with tooltip text
     * @param pos Block position
     * @param color Color in ARGB format (0xAARRGGBB)
     * @param durationTicks Duration in tick (20 tick = 1 second)
     * @param text Optional text to display when looking at the block
     */
    public void addHighlightedBlock(BlockPos pos, int color, int durationTicks, String text) {
        // Add the block to the map with text
        highlightedBlocks.put(pos, new MarkBlockData(color, Minecraft.getInstance().level.getGameTime() + durationTicks, false, text));
    }
    
    /**
     * Add a billboard marker at the specified position
     * @param pos Block position
     * @param color Color tint in ARGB format (0xAARRGGBB)
     * @param durationTicks Duration in tick (20 tick = 1 second)
     */
    public void addBillboardMarker(BlockPos pos, int color, int durationTicks) {
        // Add the marker to the map, using a special flag to indicate it's a small cube marker
        Minecraft mc = Minecraft.getInstance();
        // Ensure level is available and get current game time
        if (mc.level == null) {
            // If level is not available, schedule for next tick
            mc.execute(() -> {
                if (mc.level != null) {
                    long currentTime = mc.level.getGameTime();
                    billboardMarkers.put(pos, new MarkBlockData(color, currentTime + durationTicks, true));
                }
            });
        } else {
            billboardMarkers.put(pos, new MarkBlockData(color, expirationTime(mc, durationTicks), true));
        }
    }
    
    /**
     * Add a billboard marker at the specified position with tooltip text
     * @param pos Block position
     * @param color Color tint in ARGB format (0xAARRGGBB)
     * @param durationTicks Duration in tick (20 tick = 1 second)
     * @param text Optional text to display when looking at the marker
     */
    public void addBillboardMarker(BlockPos pos, int color, int durationTicks, String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long expire = expirationTime(mc, durationTicks);
        billboardMarkers.put(pos, new MarkBlockData(color, expire, true, text));
    }

    public void clearBillboardMarkersForOwner(BlockPos owner) {
        if (owner != null) {
            BlockPos key = owner.immutable();
            billboardMarkersByOwner.remove(key);
            ownedBoxesByOwner.remove(key);
        }
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

    public void addBillboardMarker(BlockPos owner, BlockPos pos, int color, int durationTicks) {
        addBillboardMarker(owner, pos, color, durationTicks, true);
    }

    public void addBillboardMarker(BlockPos owner, BlockPos pos, int color, int durationTicks, boolean throughWalls) {
        if (owner == null || owner.equals(BlockPos.ZERO)) {
            addBillboardMarker(pos, color, durationTicks);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Runnable add = () -> {
            if (mc.level == null) {
                return;
            }
            long expire = expirationTime(mc, durationTicks);
            billboardMarkersByOwner
                    .computeIfAbsent(owner.immutable(), k -> new ConcurrentHashMap<>())
                    .put(pos.immutable(), new MarkBlockData(color, expire, true, throughWalls));
        };
        if (mc.level == null) {
            mc.execute(add);
        } else {
            add.run();
        }
    }

    public void addOwnedBox(BlockPos owner, BlockPos min, BlockPos max, int color, int durationTicks, boolean throughWalls) {
        if (owner == null || min == null || max == null || owner.equals(BlockPos.ZERO)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Runnable add = () -> {
            if (mc.level == null) {
                return;
            }
            long expire = expirationTime(mc, durationTicks);
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

    public void addBillboardMarker(BlockPos owner, BlockPos pos, int color, int durationTicks, String text) {
        if (owner == null || owner.equals(BlockPos.ZERO)) {
            addBillboardMarker(pos, color, durationTicks, text);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long expire = expirationTime(mc, durationTicks);
        billboardMarkersByOwner
                .computeIfAbsent(owner.immutable(), k -> new ConcurrentHashMap<>())
                .put(pos.immutable(), new MarkBlockData(color, expire, true, true, text));
    }

    private static long expirationTime(Minecraft mc, int durationTicks) {
        return durationTicks > 0 ? mc.level.getGameTime() + durationTicks : Long.MAX_VALUE;
    }
    
    /**
     * Remove a highlighted block
     */
    public void removeHighlightedBlock(BlockPos pos) {
        highlightedBlocks.remove(pos);
    }
    
    /**
     * Remove a billboard marker
     */
    public void removeBillboardMarker(BlockPos pos) {
        billboardMarkers.remove(pos);
    }
    
    /**
     * Remove all highlighted blocks and markers
     */
    public void clearHighlightedBlocks() {
        highlightedBlocks.clear();
        billboardMarkers.clear();
        billboardMarkersByOwner.clear();
        ownedBoxesByOwner.clear();
    }
    
    /**
     * Check if player is looking at a marked block and display its text if available
     * Should be called every tick from a client event handler
     */
    public void checkPlayerLookingAtMarker() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        
        // Distanza massima per il rilevamento dei marker (aumentata significativamente)
        double maxDistance = 256.0; // Raddoppiata a 256 blocchi
        
        // Get what the player is looking at
        HitResult hitResult = mc.player.pick(maxDistance, 0.0F, false);
        
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult)hitResult).getBlockPos();
            
            // Check if it's a highlighted block
            MarkBlockData data = highlightedBlocks.get(pos);
            if (data != null && data.text != null) {
                // Calcola la distanza per mostrarla nel messaggio
                double distance = mc.player.position().distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                mc.player.displayClientMessage(Component.literal(data.text + " (" + String.format("%.1f", distance) + "m)"), true);
                return;
            }
            
            // Check if it's a billboard marker
            data = billboardMarkers.get(pos);
            if (data != null && data.text != null) {
                // Calcola la distanza per mostrarla nel messaggio
                double distance = mc.player.position().distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                mc.player.displayClientMessage(Component.literal(data.text + " (" + String.format("%.1f", distance) + "m)"), true);
                return;
            }
        }
        
        // Se non stiamo guardando un blocco specifico, controlliamo tutti i marker
        // per vedere se stiamo guardando nella loro direzione generale
        checkDistantMarkers(mc, maxDistance);
    }
    
    /**
     * Verifica se il giocatore sta guardando nella direzione di un marker distante
     * e mostra il testo se disponibile
     */
    private void checkDistantMarkers(Minecraft mc, double maxDistance) {
        // Posizione della camera
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        // Direzione in cui sta guardando il giocatore
        Vec3 lookVec = mc.player.getViewVector(1.0F);
        
        // Blocco più vicino trovato
        BlockPos nearestBlockPos = null;
        double nearestDistance = Double.MAX_VALUE;
        String nearestText = null;
        boolean isNearestBillboard = false;
        
        // Verifica tutti i blocchi evidenziati - iterazione diretta su ConcurrentHashMap è thread-safe
        for (Map.Entry<BlockPos, MarkBlockData> entry : highlightedBlocks.entrySet()) {
            if (entry.getValue().text != null) {
                BlockPos pos = entry.getKey();
                
                // Verifica se il blocco esiste ancora (non è aria)
                // Per i blocchi evidenziati, controlliamo che non siano aria
                if (mc.level.getBlockState(pos).isAir()) {
                    continue; // Salta questo blocco se è aria
                }
                
                // Converti la posizione del blocco in un vettore
                Vec3 blockVec = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                // Calcola il vettore dalla camera al blocco
                Vec3 toBlock = blockVec.subtract(cameraPos);
                double distance = toBlock.length();
                
                // Se il blocco è entro la distanza massima
                if (distance <= maxDistance) {
                    // Normalizza il vettore
                    Vec3 toBlockNorm = toBlock.normalize();
                    // Calcola il prodotto scalare (dot product) tra la direzione di vista e il vettore verso il blocco
                    double dotProduct = lookVec.dot(toBlockNorm);
                    
                    // Calcola l'angolo di tolleranza in base alla distanza
                    double minDotProduct = calculateMinDotProduct(distance, maxDistance);
                    
                    // Se il dot product è maggiore del minimo, significa che stiamo guardando verso il blocco
                    if (dotProduct > minDotProduct) {
                        // Usa una formula di priorità che considera sia la distanza che quanto è centrato il marker
                        // Più è centrato e vicino, maggiore è la priorità
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
        
        // Verifica tutti i marker billboard
        for (Map.Entry<BlockPos, MarkBlockData> entry : billboardMarkers.entrySet()) {
            if (entry.getValue().text != null) {
                BlockPos pos = entry.getKey();
                
                // Per i billboard marker, non è necessario che ci sia un blocco
                // quindi non facciamo il controllo isAir()
                
                // Converti la posizione del blocco in un vettore
                Vec3 blockVec = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                // Calcola il vettore dalla camera al blocco
                Vec3 toBlock = blockVec.subtract(cameraPos);
                double distance = toBlock.length();
                
                // Se il blocco è entro la distanza massima
                if (distance <= maxDistance) {
                    // Normalizza il vettore
                    Vec3 toBlockNorm = toBlock.normalize();
                    // Calcola il prodotto scalare (dot product) tra la direzione di vista e il vettore verso il blocco
                    double dotProduct = lookVec.dot(toBlockNorm);
                    
                    // Calcola l'angolo di tolleranza in base alla distanza
                    double minDotProduct = calculateMinDotProduct(distance, maxDistance);
                    
                    // Se il dot product è maggiore del minimo, significa che stiamo guardando verso il blocco
                    if (dotProduct > minDotProduct) {
                        // Usa una formula di priorità che considera sia la distanza che quanto è centrato il marker
                        // Più è centrato e vicino, maggiore è la priorità
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
        
        // Se abbiamo trovato un blocco, mostra il testo
        if (nearestBlockPos != null && nearestText != null) {
            // Calcola la distanza reale per mostrarla nel messaggio
            double actualDistance = mc.player.position().distanceTo(
                new Vec3(nearestBlockPos.getX() + 0.5, nearestBlockPos.getY() + 0.5, nearestBlockPos.getZ() + 0.5));
            
            // Per i blocchi evidenziati, verifichiamo ancora una volta che non siano aria
            if (!isNearestBillboard && mc.level.getBlockState(nearestBlockPos).isAir()) {
                // Se il blocco è aria, mostra solo il testo senza distanza
                mc.player.displayClientMessage(Component.literal(nearestText), true);
            } else {
                // Altrimenti mostra il testo completo con distanza
                String distanceText = String.format("%.1f", actualDistance) + "m";
                mc.player.displayClientMessage(
                    Component.literal(nearestText + " (" + distanceText + ")"),
                    true);
            }
        }
    }
    
    /**
     * Calcola il valore minimo del prodotto scalare in base alla distanza
     * Più lontano è il blocco, minore sarà il valore richiesto (angolo di tolleranza maggiore)
     */
    private double calculateMinDotProduct(double distance, double maxDistance) {
        // Formula: cos(angolo) = minDotProduct
        // Vogliamo che l'angolo di tolleranza aumenti linearmente con la distanza
        
        // A 10 blocchi: tolleranza di circa 5 gradi (cos(5°) ≈ 0.996)
        // A 128 blocchi: tolleranza di circa 20 gradi (cos(20°) ≈ 0.94)
        
        // Interpolazione lineare tra questi due punti
        double minAngleDegrees = 5.0;
        double maxAngleDegrees = 20.0;
        double normalizedDistance = Math.min(distance, maxDistance) / maxDistance;
        double angleDegrees = minAngleDegrees + (maxAngleDegrees - minAngleDegrees) * normalizedDistance;
        
        // Converti l'angolo in radianti e calcola il coseno
        double angleRadians = Math.toRadians(angleDegrees);
        return Math.cos(angleRadians);
    }
    
    /**
     * Render all highlighted blocks and markers
     */
    public void render(PoseStack poseStack, float partialTick) {
        // Area borders require RenderLevelStageEvent — see VanillaWorldMarkerClientHooks.

        if (highlightedBlocks.isEmpty() && billboardMarkers.isEmpty()
                && billboardMarkersByOwner.isEmpty() && ownedBoxesByOwner.isEmpty()) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        
        long currentTime = mc.level.getGameTime();

        billboardMarkersByOwner.entrySet().removeIf(entry -> {
            BlockState state = mc.level.getBlockState(entry.getKey());
            return state.isAir();
        });
        ownedBoxesByOwner.entrySet().removeIf(entry -> mc.level.getBlockState(entry.getKey()).isAir());
        
        // Remove expired blocks - usando removeIf che è thread-safe su ConcurrentHashMap
        highlightedBlocks.entrySet().removeIf(entry -> entry.getValue().expirationTime <= currentTime);
        
        // Remove expired billboard markers - usando removeIf che è thread-safe su ConcurrentHashMap
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
        
        // Check if player is looking at a marked block to display text
        checkPlayerLookingAtMarker();
        
        // Get the camera position
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        
        // Render cube highlights
        if (!highlightedBlocks.isEmpty()) {
            renderCubeHighlights(poseStack, mc, cameraPos, currentTime);
        }
        
        // Render billboard markers
        if (!billboardMarkers.isEmpty()) {
            renderBillboardMarkers(poseStack, mc, cameraPos, currentTime);
        }

        if (!billboardMarkersByOwner.isEmpty() || !ownedBoxesByOwner.isEmpty()) {
            renderOwnedMarkers(poseStack, mc, cameraPos, true);
            renderOwnedMarkers(poseStack, mc, cameraPos, false);
        }
    }

    private void renderOwnedMarkers(PoseStack poseStack, Minecraft mc, Vec3 cameraPos, boolean throughWallsPass) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (throughWallsPass) {
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        boolean hasVertices = false;

        for (Map<BlockPos, MarkBlockData> worldMarkers : billboardMarkersByOwner.values()) {
            for (Map.Entry<BlockPos, MarkBlockData> entry : worldMarkers.entrySet()) {
                if (entry.getValue().throughWalls != throughWallsPass) {
                    continue;
                }
                drawSmallCube(bufferBuilder, matrix, entry.getKey(), cameraPos, entry.getValue().color);
                hasVertices = true;
            }
        }
        for (List<MarkBoxData> boxes : ownedBoxesByOwner.values()) {
            for (MarkBoxData box : boxes) {
                if (box.throughWalls != throughWallsPass) {
                    continue;
                }
                drawFilledBox(bufferBuilder, matrix, box.min, box.max, cameraPos, box.color);
                hasVertices = true;
            }
        }

        if (hasVertices) {
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
    
    /**
     * Render cube highlights
     */
    private void renderCubeHighlights(PoseStack poseStack, Minecraft mc, Vec3 cameraPos, long currentTime) {
        // Check if there are valid blocks to render
        boolean hasValidBlocks = false;
        for (Map.Entry<BlockPos, MarkBlockData> entry : highlightedBlocks.entrySet()) {
            if (!mc.level.getBlockState(entry.getKey()).isAir()) {
                hasValidBlocks = true;
                break;
            }
        }
        
        // If there are no valid blocks, exit without rendering
        if (!hasValidBlocks) {
            return;
        }
        
        // Prepare the rendering
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        // Start rendering
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        
        // Flag per tracciare se abbiamo aggiunto vertex al buffer
        boolean hasVertices = false;
        
        // Render each block - iterazione diretta su ConcurrentHashMap è thread-safe
        for (Map.Entry<BlockPos, MarkBlockData> entry : highlightedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            int color = entry.getValue().color;
            
            // Check if the block is still valid
            if (mc.level.getBlockState(pos).isAir()) {
                continue;
            }
            
            // Draw the cube
            drawCube(bufferBuilder, matrix, pos, cameraPos, color);
            hasVertices = true;
        }
        
        // Complete the rendering only if we have vertices
        if (hasVertices) {
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        }
        
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
    
    /**
     * Render billboard markers
     */
    private void renderBillboardMarkers(PoseStack poseStack, Minecraft mc, Vec3 cameraPos, long currentTime) {
        // Debug: log how many markers we're trying to render
        if (!billboardMarkers.isEmpty()) {
            LOGGER.info("Rendering {} billboard markers", billboardMarkers.size());
        }
        
        // Prepare the rendering for small cubes
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        
        // Start rendering
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();
        
        // Flag per tracciare se abbiamo aggiunto vertex al buffer
        boolean hasVertices = false;
        
        // Render each marker as a small cube - iterazione diretta su ConcurrentHashMap è thread-safe
        for (Map.Entry<BlockPos, MarkBlockData> entry : billboardMarkers.entrySet()) {
            BlockPos pos = entry.getKey();
            int color = entry.getValue().color;
            
            // Draw the small cube (12x12 pixels, centered in the block)
            drawSmallCube(bufferBuilder, matrix, pos, cameraPos, color);
            hasVertices = true;
        }
        
        // Complete the rendering only if we have vertices
        if (hasVertices) {
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        }
        
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
    
    /**
     * Draw a small cube (12x12 pixels) centered at the specified position
     */
    private void drawSmallCube(BufferBuilder bufferBuilder, Matrix4f matrix, BlockPos pos, Vec3 cameraPos, int color) {
        // Calculate the size of the cube (12/16 of a block = 0.75 blocks)
        float size = 12.0f / 16.0f;
        float halfSize = size / 2.0f;
        
        // Calculate the position (centered in the block)
        float x = pos.getX() + 0.5f - halfSize - (float)cameraPos.x;
        float y = pos.getY() + 0.5f - halfSize - (float)cameraPos.y;
        float z = pos.getZ() + 0.5f - halfSize - (float)cameraPos.z;
        
        // Extract color components
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255.0F;
        
        // Bottom face
        bufferBuilder.addVertex(x, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + size, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + size, y, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x, y, z + size).setColor(red, green, blue, alpha);
        
        // Top face
        bufferBuilder.addVertex(x, y + size, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x, y + size, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + size, y + size, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + size, y + size, z).setColor(red, green, blue, alpha);
        
        // North face
        bufferBuilder.addVertex(x, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x, y + size, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + size, y + size, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + size, y, z).setColor(red, green, blue, alpha);
        
        // South face
        bufferBuilder.addVertex(x, y, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + size, y, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + size, y + size, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x, y + size, z + size).setColor(red, green, blue, alpha);
        
        // West face
        bufferBuilder.addVertex(x, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x, y, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x, y + size, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x, y + size, z).setColor(red, green, blue, alpha);
        
        // East face
        bufferBuilder.addVertex(x + size, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + size, y + size, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + size, y + size, z + size).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + size, y, z + size).setColor(red, green, blue, alpha);
    }
    
    /**
     * Draw a cube at the specified position
     */
    private void drawCube(BufferBuilder bufferBuilder, Matrix4f matrix, BlockPos pos, Vec3 cameraPos, int color) {
        float x = pos.getX() - (float)cameraPos.x;
        float y = pos.getY() - (float)cameraPos.y;
        float z = pos.getZ() - (float)cameraPos.z;
        
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255.0F;
        
        // Bottom face
        bufferBuilder.addVertex(x, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + 1, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + 1, y, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x, y, z + 1).setColor(red, green, blue, alpha);
        
        // Top face
        bufferBuilder.addVertex(x, y + 1, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x, y + 1, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + 1, y + 1, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + 1, y + 1, z).setColor(red, green, blue, alpha);
        
        // North face
        bufferBuilder.addVertex(x, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x, y + 1, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + 1, y + 1, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + 1, y, z).setColor(red, green, blue, alpha);
        
        // South face
        bufferBuilder.addVertex(x, y, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + 1, y, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + 1, y + 1, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x, y + 1, z + 1).setColor(red, green, blue, alpha);
        
        // West face
        bufferBuilder.addVertex(x, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x, y, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x, y + 1, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x, y + 1, z).setColor(red, green, blue, alpha);
        
        // East face
        bufferBuilder.addVertex(x + 1, y, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + 1, y + 1, z).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + 1, y + 1, z + 1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x + 1, y, z + 1).setColor(red, green, blue, alpha);
    }
    
    private void drawFilledBox(BufferBuilder bufferBuilder, Matrix4f matrix, BlockPos min, BlockPos max,
                               Vec3 cameraPos, int color) {
        float inset = 1.0f / 16.0f;
        float x0 = min.getX() + inset - (float) cameraPos.x;
        float y0 = min.getY() + inset - (float) cameraPos.y;
        float z0 = min.getZ() + inset - (float) cameraPos.z;
        float x1 = max.getX() + 1.0f - inset - (float) cameraPos.x;
        float y1 = max.getY() + 1.0f - inset - (float) cameraPos.y;
        float z1 = max.getZ() + 1.0f - inset - (float) cameraPos.z;
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float alpha = ((color >> 24) & 0xFF) / 255.0F;
        bufferBuilder.addVertex(x0, y0, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x1, y0, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x1, y0, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x0, y0, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x0, y1, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x0, y1, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x1, y1, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x1, y1, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x0, y0, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x0, y1, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x1, y1, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x1, y0, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x0, y0, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x1, y0, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x1, y1, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x0, y1, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x0, y0, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x0, y0, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x0, y1, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x0, y1, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x1, y0, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x1, y1, z0).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x1, y1, z1).setColor(red, green, blue, alpha);
        bufferBuilder.addVertex(x1, y0, z1).setColor(red, green, blue, alpha);
    }

    private static class MarkBlockData {
        final int color;
        final long expirationTime;
        final boolean isSmallCube;
        final boolean throughWalls;
        final String text;
        
        MarkBlockData(int color, long expirationTime) {
            this(color, expirationTime, false, true, null);
        }
        
        MarkBlockData(int color, long expirationTime, boolean isSmallCube) {
            this(color, expirationTime, isSmallCube, true, null);
        }

        MarkBlockData(int color, long expirationTime, boolean isSmallCube, boolean throughWalls) {
            this(color, expirationTime, isSmallCube, throughWalls, null);
        }
        
        MarkBlockData(int color, long expirationTime, boolean isSmallCube, String text) {
            this(color, expirationTime, isSmallCube, true, text);
        }

        MarkBlockData(int color, long expirationTime, boolean isSmallCube, boolean throughWalls, String text) {
            this.color = color;
            this.expirationTime = expirationTime;
            this.isSmallCube = isSmallCube;
            this.throughWalls = throughWalls;
            this.text = text;
        }
    }

    private static class MarkBoxData {
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