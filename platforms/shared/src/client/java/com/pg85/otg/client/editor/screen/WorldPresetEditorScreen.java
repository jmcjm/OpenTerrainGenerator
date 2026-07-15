package com.pg85.otg.client.editor.screen;

import com.pg85.otg.OTG;
import com.pg85.otg.client.editor.data.PresetReloader;
import com.pg85.otg.client.editor.data.WorldPresetYamlIO;
import com.pg85.otg.client.editor.widget.CategoryTabsWidget;
import com.pg85.otg.client.editor.widget.DimensionAccordionCard;
import com.pg85.otg.client.editor.widget.DimensionSlotWidget;
import com.pg85.otg.client.editor.widget.GameRulesListWidget;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import com.pg85.otg.presets.DimensionPreset;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Tabs-based editor for a single WorldPreset YAML.
 * Tabs: Metadata | Overworld | Nether | End | Dimensions | Settings | GameRules
 */
public class WorldPresetEditorScreen extends Screen {

    private static final Logger LOG = LoggerFactory.getLogger(WorldPresetEditorScreen.class);

    private static final int TAB_METADATA = 0;
    private static final int TAB_OVERWORLD = 1;
    private static final int TAB_NETHER = 2;
    private static final int TAB_END = 3;
    private static final int TAB_DIMENSIONS = 4;
    private static final int TAB_SETTINGS = 5;
    private static final int TAB_GAMERULES = 6;

    private static final List<String> TAB_NAMES = List.of(
        "Metadata", "Overworld", "Nether", "End", "Dimensions", "Settings", "GameRules"
    );

    private final Path yamlPath;
    private final Screen parent;

    private WorldPresetConfig original;
    private WorldPresetConfig working;
    private boolean dirty;
    private String statusMessage;

    private CategoryTabsWidget tabs;
    private int activeTab = 0;

    private List<String> otgPresetFolders;

    // Active tab widgets (kept for mouse delegation)
    private DimensionSlotWidget slotWidget;
    private final List<DimensionAccordionCard> dimensionCards = new ArrayList<>();
    private final BitSet expandedDimensions = new BitSet();
    private GameRulesListWidget gameRulesList;

    public WorldPresetEditorScreen(Path yamlPath, Screen parent) {
        super(Component.literal("Edit WorldPreset"));
        this.yamlPath = yamlPath;
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (working == null) {
            original = WorldPresetYamlIO.load(yamlPath);
            if (original == null) {
                original = new WorldPresetConfig();
                original.Version = 1;
            }
            working = original.clone();
        }

        if (otgPresetFolders == null) {
            otgPresetFolders = new ArrayList<>();
            var engine = OTG.getEngine();
            if (engine != null) {
                for (DimensionPreset p : engine.getDimensionPresetLoader().getAllDimensionPresets()) {
                    otgPresetFolders.add(p.getFolderName());
                }
            }
        }

        // Tabs
        tabs = new CategoryTabsWidget(10, 24, width - 20, 18);
        tabs.setCategories(TAB_NAMES);
        tabs.setSelectedIndex(activeTab);
        tabs.setOnSelect(idx -> {
            activeTab = idx;
            rebuildWidgets();
        });

        // Bottom bar
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> save())
            .bounds(10, height - 30, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Revert"), b -> revert())
            .bounds(95, height - 30, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
            .bounds(width - 90, height - 30, 80, 20).build());

        slotWidget = null;
        dimensionCards.clear();
        gameRulesList = null;

        buildActiveTab();
    }

    private void buildActiveTab() {
        switch (activeTab) {
            case TAB_METADATA   -> buildMetadataTab();
            case TAB_OVERWORLD  -> buildOverworldTab();
            case TAB_NETHER     -> buildNetherTab();
            case TAB_END        -> buildEndTab();
            case TAB_DIMENSIONS -> buildDimensionsTab();
            case TAB_SETTINGS   -> buildSettingsTab();
            case TAB_GAMERULES  -> buildGameRulesTab();
        }
    }

    // --- Metadata ---

    private void buildMetadataTab() {
        int x = 20;
        int y = 56;
        int labelW = 110;
        int fieldW = Math.min(300, width - 40 - labelW);

        EditBox displayName = new EditBox(font, x + labelW, y, fieldW, 18, Component.empty());
        displayName.setMaxLength(128);
        displayName.setValue(working.DisplayName == null ? "" : working.DisplayName);
        displayName.setResponder(v -> { working.DisplayName = v; markDirty(); });
        addRenderableWidget(displayName);

        EditBox description = new EditBox(font, x + labelW, y + 24, fieldW, 18, Component.empty());
        description.setMaxLength(512);
        description.setValue(working.Description == null ? "" : working.Description);
        description.setResponder(v -> { working.Description = v; markDirty(); });
        addRenderableWidget(description);

        EditBox modpackName = new EditBox(font, x + labelW, y + 48, fieldW, 18, Component.empty());
        modpackName.setMaxLength(128);
        modpackName.setValue(working.ModpackName == null ? "" : working.ModpackName);
        modpackName.setResponder(v -> { working.ModpackName = v; markDirty(); });
        addRenderableWidget(modpackName);
    }

    // --- Settings ---

    private void buildSettingsTab() {
        if (working.Settings == null) working.Settings = new WorldPresetConfig.Settings();
        var settings = working.Settings;

        int x = 20;
        int y = 56;

        Button generateStructuresBtn = Button.builder(
            Component.literal((settings.GenerateStructures ? "[✓] " : "[ ] ") + "GenerateStructures"),
            b -> {
                settings.GenerateStructures = !settings.GenerateStructures;
                markDirty();
                rebuildWidgets();
            }
        ).bounds(x, y, 240, 20).build();
        addRenderableWidget(generateStructuresBtn);

        Button bonusChestBtn = Button.builder(
            Component.literal((settings.BonusChest ? "[✓] " : "[ ] ") + "BonusChest"),
            b -> {
                settings.BonusChest = !settings.BonusChest;
                markDirty();
                rebuildWidgets();
            }
        ).bounds(x, y + 24, 240, 20).build();
        addRenderableWidget(bonusChestBtn);
    }

    // --- Overworld/Nether/End ---

    private void buildOverworldTab() {
        if (working.Overworld == null) {
            working.Overworld = new WorldPresetConfig.OTGOverWorld(null, 0, null, null);
        }
        slotWidget = new DimensionSlotWidget(
            working.Overworld, true, false, otgPresetFolders, this::markDirty, this::rebuildWidgets);
        slotWidget.init(20, 56, width - 40, this::addRenderableWidget, this::addRenderableWidget);
    }

    private void buildNetherTab() {
        if (working.Nether == null) {
            working.Nether = new WorldPresetConfig.OTGDimension(null, 0);
        }
        slotWidget = new DimensionSlotWidget(
            working.Nether, false, true, otgPresetFolders, this::markDirty, this::rebuildWidgets);
        slotWidget.init(20, 56, width - 40, this::addRenderableWidget, this::addRenderableWidget);
    }

    private void buildEndTab() {
        if (working.End == null) {
            working.End = new WorldPresetConfig.OTGDimension(null, 0);
        }
        slotWidget = new DimensionSlotWidget(
            working.End, false, true, otgPresetFolders, this::markDirty, this::rebuildWidgets);
        slotWidget.init(20, 56, width - 40, this::addRenderableWidget, this::addRenderableWidget);
    }

    // --- Dimensions ---

    private void buildDimensionsTab() {
        if (working.Dimensions == null) working.Dimensions = new ArrayList<>();
        dimensionCards.clear();

        int x = 10;
        int y = 56;

        for (int i = 0; i < working.Dimensions.size(); i++) {
            final int idx = i;
            var dim = working.Dimensions.get(idx);
            boolean expanded = expandedDimensions.get(idx);

            Runnable onRemove = () -> {
                working.Dimensions.remove(idx);
                expandedDimensions.clear(idx);
                markDirty();
                rebuildWidgets();
            };
            Runnable onOpenGameRules = () -> {
                // Edit on a clone — only commit to dim on Save, so Cancel truly discards changes.
                WorldPresetConfig.GameRules editTarget = dim.GameRules != null
                    ? dim.GameRules.clone()
                    : new WorldPresetConfig.GameRules();
                minecraft.setScreen(new GameRulesEditorScreen(
                    editTarget, this,
                    updated -> {
                        // Only attach to dim if user clicked Save AND something is actually set
                        dim.GameRules = isAnyRuleSet(updated) ? updated : null;
                        markDirty();
                    },
                    "GameRules — " + (dim.isNonOTG()
                        ? (dim.DimensionName != null ? dim.DimensionName : "Non-OTG: " + dim.NonOTGWorldType)
                        : dim.PresetFolderName == null ? "(unset)" : dim.PresetFolderName)
                ));
            };
            Runnable onToggleExpand = () -> {
                expandedDimensions.flip(idx);
                rebuildWidgets();
            };

            var card = new DimensionAccordionCard(
                dim, expanded, onRemove, onOpenGameRules, onToggleExpand, otgPresetFolders, this::markDirty, this::rebuildWidgets
            );
            card.init(x, y, width - 20, this::addRenderableWidget, this::addRenderableWidget);
            dimensionCards.add(card);
            y += card.getHeight() + 4;
        }

        Button addBtn = Button.builder(
            Component.literal("+ Add Dimension"),
            b -> {
                var newDim = new WorldPresetConfig.OTGDimension(
                    otgPresetFolders.isEmpty() ? null : otgPresetFolders.get(0), 0);
                working.Dimensions.add(newDim);
                markDirty();
                rebuildWidgets();
            }
        ).bounds(x, y, 160, 20).build();
        addRenderableWidget(addBtn);
    }

    // --- GameRules ---

    private void buildGameRulesTab() {
        if (working.GameRules == null) working.GameRules = new WorldPresetConfig.GameRules();
        gameRulesList = new GameRulesListWidget(10, 56, width - 20, height - 96, working.GameRules, this::rebuildWidgets);
        addRenderableWidget(gameRulesList.getSearchEditBox());
        for (var eb : gameRulesList.collectActiveEditBoxes()) {
            addRenderableWidget(eb);
        }
    }

    // --- Save / Revert ---

    private void save() {
        if (working == null) return;
        if (WorldPresetYamlIO.save(yamlPath, working)) {
            original = working.clone();
            dirty = false;
            PresetReloader.reload();
            statusMessage = "Saved. Changes visible in Create World GUI next time you open it.";
            LOG.info("Saved WorldPreset: {}", yamlPath.getFileName());
        } else {
            statusMessage = "Save failed — check logs";
        }
    }

    private void revert() {
        working = original.clone();
        dirty = false;
        statusMessage = "Reverted to last saved state";
        rebuildWidgets();
    }

    void markDirty() {
        dirty = true;
    }

    /** Returns true if at least one GameRules field is non-null. */
    private static boolean isAnyRuleSet(WorldPresetConfig.GameRules rules) {
        if (rules == null) return false;
        try {
            for (var f : WorldPresetConfig.GameRules.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                if (f.get(rules) != null) return true;
            }
        } catch (IllegalAccessException ignored) {}
        return false;
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        String titleText = "WorldPreset Editor" + (dirty ? "  ●" : "") +
            (working != null && working.DisplayName != null && !working.DisplayName.isBlank()
                ? " — " + working.DisplayName : "");
        g.drawCenteredString(font, titleText, width / 2, 8, 0xFFFFFF);

        if (tabs != null) tabs.render(g);

        // Tab-specific text labels + delegated render
        switch (activeTab) {
            case TAB_METADATA -> {
                int x = 20; int y = 56;
                g.drawString(font, "Display Name:", x, y + 5, 0xFFAAAAAA);
                g.drawString(font, "Description:",  x, y + 29, 0xFFAAAAAA);
                g.drawString(font, "Modpack Name:", x, y + 53, 0xFFAAAAAA);
            }
            case TAB_OVERWORLD, TAB_NETHER, TAB_END -> {
                if (slotWidget != null) slotWidget.render(g, mouseX, mouseY);
            }
            case TAB_DIMENSIONS -> {
                for (var card : dimensionCards) card.render(g, mouseX, mouseY);
                if (dimensionCards.isEmpty()) {
                    g.drawCenteredString(font, "No custom dimensions. Click + Add Dimension.",
                        width / 2, 80, 0xFF888888);
                }
            }
            case TAB_GAMERULES -> {
                if (gameRulesList != null) gameRulesList.render(g, mouseX, mouseY);
            }
            default -> {}
        }

        if (statusMessage != null) {
            g.drawString(font, statusMessage, 10, height - 46, 0xFFAAAA44);
        }
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tabs != null && tabs.mouseClicked(mouseX, mouseY)) return true;
        if (activeTab == TAB_GAMERULES && gameRulesList != null) {
            int result = gameRulesList.mouseClickedResult(mouseX, mouseY);
            if (result == 1) {
                markDirty();
                // No rebuild needed — data change in place via reflection
                return true;
            } else if (result == 2) {
                // Scrollbar click — onStructureChanged already triggered rebuild, don't mark dirty
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (activeTab == TAB_GAMERULES && gameRulesList != null && gameRulesList.mouseDragged(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeTab == TAB_GAMERULES && gameRulesList != null && gameRulesList.mouseReleased()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (activeTab == TAB_GAMERULES && gameRulesList != null
            && gameRulesList.mouseScrolled(mouseX, mouseY, dy)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public void onClose() {
        if (dirty) {
            minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        minecraft.setScreen(parent);
                    } else {
                        minecraft.setScreen(this);
                    }
                },
                Component.literal("Discard unsaved changes?"),
                Component.literal("You have unsaved changes in this WorldPreset. Discard them?"),
                Component.literal("Discard"),
                Component.literal("Keep editing")
            ));
        } else {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
