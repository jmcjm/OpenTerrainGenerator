package com.pg85.otg.client.editor.screen;

import com.pg85.otg.client.editor.data.BiomeFileScanner;
import com.pg85.otg.client.editor.widget.TreeListWidget;
import com.pg85.otg.client.editor.widget.Viewport3DRenderer;
import com.pg85.otg.client.preview.BOPreviewHelper;
import com.pg85.otg.client.preview.OrbitCamera;
import com.pg85.otg.client.preview.PreviewRenderer;
import com.pg85.otg.client.preview.world.PreviewBiomes;
import com.pg85.otg.client.preview.world.PreviewWorld;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.presets.DimensionPreset;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class BOBrowserScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger(BOBrowserScreen.class);

    private final DimensionPreset preset;
    private final Screen parent;
    private List<String> allObjectPaths = List.of();
    private TreeListWidget treeList;
    private EditBox searchBox;
    private String selectedPath;

    // 3D preview
    private PreviewWorld boPreviewWorld;
    private PreviewRenderer boRenderer;
    private OrbitCamera boCamera;
    private BOPreviewHelper.BOBounds lastBounds;
    private String loadedObjectName;
    private String statusMessage;

    // Viewport bounds (right 2/3)
    private int viewportX, viewportY, viewportW, viewportH;
    // Left panel width (left 1/3)
    private int leftPanelW;

    public BOBrowserScreen(DimensionPreset preset, Screen parent) {
        super(Component.literal("OTG Editor — Browse BO3/BO4"));
        this.preset = preset;
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Scan only once
        if (allObjectPaths.isEmpty()) {
            allObjectPaths = scanObjects();
        }

        // Layout: left 1/3 for tree list, right 2/3 for viewport
        leftPanelW = Math.max(180, width / 3);
        viewportX = leftPanelW;
        viewportY = 4;
        viewportW = width - leftPanelW;
        viewportH = height - 60; // leave room for metadata + bottom buttons

        // Search box
        searchBox = new EditBox(font, 10, 30, leftPanelW - 20, 16, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search objects..."));
        searchBox.setResponder(filter -> {
            if (treeList != null) {
                treeList.filter(filter);
            }
        });
        addRenderableWidget(searchBox);

        // Tree list (left panel, below search)
        treeList = new TreeListWidget(10, 52, leftPanelW - 20, height - 100, 14);
        treeList.buildFromPaths(allObjectPaths);
        treeList.setOnSelect(node -> {
            selectedPath = node.fullPath();
            loadSelectedObject(selectedPath);
        });

        // Back button
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
            .bounds(leftPanelW / 2 - 40, height - 30, 80, 20).build());

        // Assign to Biome button (viewport bottom area)
        addRenderableWidget(Button.builder(Component.literal("Assign to Biome"), btn -> {
            if (loadedObjectName == null) return;
            minecraft.setScreen(new BiomeSelectDialog(preset, this, biomeName -> {
                assignObjectToBiome(loadedObjectName, biomeName);
            }));
        }).bounds(viewportX, height - 30, 110, 20).build());

        // Initialize camera if not yet
        if (boCamera == null) {
            boCamera = new OrbitCamera();
            boCamera.setTheta((float) (Math.PI / 4));
            boCamera.setPhi((float) (Math.PI / 3));
        }

        // Direction buttons removed — mouse drag is sufficient for orbiting
    }

    private List<String> scanObjects() {
        Path objectsDir = preset.getFolder().resolve(Constants.OBJECTS_FOLDER);
        if (!Files.isDirectory(objectsDir)) {
            objectsDir = preset.getFolder().resolve(Constants.LEGACY_WORLD_OBJECTS_FOLDER);
        }
        if (!Files.isDirectory(objectsDir)) return List.of();

        try (var walk = Files.walk(objectsDir)) {
            final Path root = objectsDir;
            return walk.filter(Files::isRegularFile)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".bo3") || name.endsWith(".bo4") || name.endsWith(".bo2");
                })
                .map(p -> root.relativize(p).toString())
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            LOG.error("Failed to scan Objects folder", e);
            return List.of();
        }
    }

    /**
     * Load the selected BO into the 3D preview world.
     */
    private void loadSelectedObject(String path) {
        if (path == null || path.isBlank()) return;

        // Strip path to just object name (without extension)
        String objectName = path;
        int lastSlash = objectName.lastIndexOf('/');
        if (lastSlash < 0) lastSlash = objectName.lastIndexOf('\\');
        if (lastSlash >= 0) objectName = objectName.substring(lastSlash + 1);
        // Remove extension
        int dot = objectName.lastIndexOf('.');
        if (dot >= 0) objectName = objectName.substring(0, dot);

        // Skip if already loaded
        if (objectName.equals(loadedObjectName)) return;

        statusMessage = "Loading " + objectName + "...";

        // Create or clear preview world
        if (boPreviewWorld == null) {
            boPreviewWorld = new PreviewWorld();
        } else {
            boPreviewWorld.clear();
        }

        // Release old renderer buffers
        if (boRenderer != null) {
            boRenderer.releaseBuffers();
        }

        String presetName = preset.getFolderName();
        BOPreviewHelper.BOBounds bounds = BOPreviewHelper.loadObject(objectName, presetName, boPreviewWorld);

        if (bounds == null) {
            statusMessage = "Failed to load: " + objectName;
            loadedObjectName = null;
            lastBounds = null;
            return;
        }

        lastBounds = bounds;
        loadedObjectName = objectName;

        // BO objects carry no biome — assign a neutral temperate one so leaves/grass tint with colour.
        boPreviewWorld.fillBiome(PreviewBiomes.defaultForest());

        // Create renderer and compile
        boRenderer = new PreviewRenderer(boPreviewWorld);
        boRenderer.compileAll();

        // Fit camera to object
        boCamera.fitTo(bounds.center(), bounds.radius());

        statusMessage = objectName + " — " + bounds.blockCount() + " blocks ("
            + bounds.sizeX() + "x" + bounds.sizeY() + "x" + bounds.sizeZ() + ")";
    }

    private void assignObjectToBiome(String objectName, String biomeName) {
        BiomeFileScanner.BiomeEntry entry = BiomeFileScanner.scan(preset.getFolder()).stream()
            .filter(e -> e.name().equals(biomeName))
            .findFirst().orElse(null);
        if (entry == null) {
            statusMessage = "Biome not found: " + biomeName;
            return;
        }
        try {
            List<String> lines = Files.readAllLines(entry.path());
            lines.add("CustomObject(100, " + objectName + ")");
            Files.write(entry.path(), lines);
            statusMessage = "Assigned " + objectName + " to " + biomeName;
        } catch (IOException e) {
            statusMessage = "Failed: " + e.getMessage();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Left panel background
        graphics.fill(0, 0, leftPanelW, height, 0xCC000000);

        // Title
        graphics.drawCenteredString(font, title, leftPanelW / 2, 5, 0xFFFFFF);

        int fileCount = treeList != null ? treeList.getTotalFileCount() : 0;
        graphics.drawString(font, fileCount + " objects found", 10, 20, 0xFF888888);

        if (treeList != null) {
            treeList.render(graphics);
        }

        // Divider line
        graphics.fill(leftPanelW - 1, 0, leftPanelW, height, 0xFF333333);

        // 3D viewport
        if (boRenderer != null && !boRenderer.isEmpty()) {
            renderBOViewport(graphics, partialTick);
        } else {
            // Empty viewport placeholder
            graphics.fill(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH, 0xFF1A1A1A);
            graphics.drawCenteredString(font, "Select a BO3/BO4 to preview",
                viewportX + viewportW / 2, viewportY + viewportH / 2, 0xFF666666);
        }

        // Metadata below viewport
        int metaY = viewportY + viewportH + 4;
        if (loadedObjectName != null && lastBounds != null) {
            String size = lastBounds.sizeX() + "x" + lastBounds.sizeY() + "x" + lastBounds.sizeZ();
            String ext = selectedPath != null && selectedPath.contains(".")
                ? selectedPath.substring(selectedPath.lastIndexOf('.')) : "";
            graphics.drawString(font, loadedObjectName + ext + "  " + size + "  " + lastBounds.blockCount() + " blocks",
                viewportX + 6, metaY, 0xFFCCCCCC);
        } else if (statusMessage != null) {
            graphics.drawString(font, statusMessage, viewportX + 6, metaY, 0xFFAAAA44);
        }

        // Selected path below the tree
        if (selectedPath != null) {
            graphics.drawString(font, selectedPath, 10, height - 50, 0xFF66CC66);
        }
    }

    private void renderBOViewport(GuiGraphics graphics, float partialTick) {
        Viewport3DRenderer.render(graphics, viewportX, viewportY, viewportW, viewportH,
            boCamera, boRenderer);
    }

    // --- Input handling ---

    private boolean isInViewport(double mouseX, double mouseY) {
        return mouseX >= viewportX && mouseX < viewportX + viewportW
            && mouseY >= viewportY && mouseY < viewportY + viewportH;
    }

    private boolean isInLeftPanel(double mouseX, double mouseY) {
        return mouseX >= 0 && mouseX < leftPanelW
            && mouseY >= 0 && mouseY < height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isInLeftPanel(mouseX, mouseY)) {
            if (treeList != null && treeList.mouseClicked(mouseX, mouseY)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaH, double deltaV) {
        if (isInViewport(mouseX, mouseY) && boCamera != null) {
            boCamera.zoom((float) (deltaV * 120));
            return true;
        }
        if (isInLeftPanel(mouseX, mouseY)) {
            if (treeList != null && treeList.mouseScrolled(mouseX, mouseY, deltaV)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaH, deltaV);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isInViewport(mouseX, mouseY) && boCamera != null) {
            boCamera.rotate((float) (-dragX * 0.01), (float) (dragY * 0.01));
            return true;
        }
        if (isInLeftPanel(mouseX, mouseY)) {
            if (treeList != null && treeList.mouseDragged(mouseX, mouseY)) return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (treeList != null && treeList.mouseReleased()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        // Release VBO buffers
        if (boRenderer != null) {
            boRenderer.releaseBuffers();
            boRenderer = null;
        }
        // Clear preview world
        if (boPreviewWorld != null) {
            boPreviewWorld.clear();
            boPreviewWorld = null;
        }
        loadedObjectName = null;
        lastBounds = null;

        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
