package com.pg85.otg.client.editor.widget;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Edits a single dimension slot (Overworld, Nether, End, or custom).
 *
 * Modes:
 *  - Overworld: allowNonOTG=true (OTG preset OR vanilla/modded world type)
 *  - Nether/End: allowVanilla=true (OTG preset OR null = vanilla)
 *  - Custom: neither (OTG preset only, always required)
 *
 * Uses cycle buttons (click to advance) for preset/type selection — no overlay dropdowns.
 */
public class DimensionSlotWidget {

    private static final List<String> BUILT_IN_NON_OTG_TYPES = List.of(
        "normal", "flat", "amplified", "large_biomes"
    );

    private final WorldPresetConfig.OTGDimension dim;
    private final boolean allowNonOTG;
    private final boolean allowVanilla;
    private final List<String> otgPresets;
    private final Runnable onChanged;
    private final Runnable onStructureChanged;

    // State
    private int x, y;
    private boolean usingNonOTG;
    private boolean usingVanilla;

    public DimensionSlotWidget(WorldPresetConfig.OTGDimension dim,
                               boolean allowNonOTG, boolean allowVanilla,
                               List<String> otgPresets,
                               Runnable onChanged,
                               Runnable onStructureChanged) {
        this.dim = dim;
        this.allowNonOTG = allowNonOTG;
        this.allowVanilla = allowVanilla;
        this.otgPresets = otgPresets;
        this.onChanged = onChanged;
        this.onStructureChanged = onStructureChanged;

        if (dim instanceof WorldPresetConfig.OTGOverWorld ow) {
            this.usingNonOTG = ow.NonOTGWorldType != null && !ow.NonOTGWorldType.isBlank();
        }
        this.usingVanilla = !usingNonOTG && (dim.PresetFolderName == null || dim.PresetFolderName.isBlank());
    }

    /** Builds widgets at given position, registering them via the given adders. */
    public void init(int x, int y, int width, Consumer<Button> addButton, Consumer<EditBox> addEditBox) {
        this.x = x;
        this.y = y;

        var font = Minecraft.getInstance().font;
        int row = y;

        if (!allowNonOTG && dim.isNonOTG()) {
            String name = dim.DimensionName != null ? dim.DimensionName + " — " : "";
            Button label = Button.builder(
                Component.literal(name + "Non-OTG: " + dim.NonOTGWorldType),
                b -> {}
            ).bounds(x, row, width, 18).build();
            label.active = false;
            addButton.accept(label);
            return; // read-only: editing non-OTG entries is a follow-up feature
        }

        // Mode selector (radios as buttons)
        if (allowNonOTG) {
            Button otgBtn = Button.builder(
                Component.literal((usingNonOTG ? "( ) " : "(●) ") + "OTG Preset"),
                b -> setUsingNonOTG(false)
            ).bounds(x, row, 130, 18).build();
            Button nonOtgBtn = Button.builder(
                Component.literal((usingNonOTG ? "(●) " : "( ) ") + "Non-OTG Type"),
                b -> setUsingNonOTG(true)
            ).bounds(x + 135, row, 140, 18).build();
            addButton.accept(otgBtn);
            addButton.accept(nonOtgBtn);
            row += 24;
        } else if (allowVanilla) {
            Button vanillaBtn = Button.builder(
                Component.literal((usingVanilla ? "[✓] " : "[ ] ") + "Use vanilla"),
                b -> setUsingVanilla(!usingVanilla)
            ).bounds(x, row, 160, 18).build();
            addButton.accept(vanillaBtn);
            row += 24;
        }

        // Preset selector
        if (usingNonOTG) {
            String label = "Type: " + currentNonOTGType();
            Button typeBtn = Button.builder(
                Component.literal(label),
                b -> cycleNonOTGType()
            ).bounds(x, row, 240, 18).build();
            addButton.accept(typeBtn);
            row += 24;

            // NonOTGGeneratorSettings text field (optional JSON for flat)
            var ow = (WorldPresetConfig.OTGOverWorld) dim;
            EditBox settingsBox = new EditBox(font, x + 160, row, 200, 16, Component.empty());
            settingsBox.setMaxLength(4096);
            settingsBox.setValue(ow.NonOTGGeneratorSettings == null ? "" : ow.NonOTGGeneratorSettings);
            settingsBox.setResponder(v -> { ow.NonOTGGeneratorSettings = v.isEmpty() ? null : v; onChanged.run(); });
            addEditBox.accept(settingsBox);
            row += 24;
        } else if (!usingVanilla) {
            if (!dim.isNonOTG()
                    && (dim.PresetFolderName == null || dim.PresetFolderName.isBlank())
                    && !otgPresets.isEmpty()) {
                dim.PresetFolderName = otgPresets.get(0);
                onChanged.run();
            }
            String label = "Preset: " + (dim.PresetFolderName == null ? "(none)" : dim.PresetFolderName);
            Button presetBtn = Button.builder(
                Component.literal(label),
                b -> cycleOtgPreset()
            ).bounds(x, row, 240, 18).build();
            addButton.accept(presetBtn);
            row += 24;
        }

        // Seed
        EditBox seedBox = new EditBox(font, x + 60, row, 180, 16, Component.empty());
        seedBox.setMaxLength(32);
        seedBox.setValue(String.valueOf(dim.Seed));
        seedBox.setResponder(val -> {
            try { dim.Seed = Long.parseLong(val); onChanged.run(); } catch (NumberFormatException ignored) {}
        });
        addEditBox.accept(seedBox);
        row += 24;

        // Portal config
        row = portalField(font, x, row, dim.PortalBlocks,          v -> { dim.PortalBlocks = blankToNull(v); onChanged.run(); }, addEditBox);
        row = portalField(font, x, row, dim.PortalColor,           v -> { dim.PortalColor = blankToNull(v); onChanged.run(); }, addEditBox);
        row = portalField(font, x, row, dim.PortalMob,             v -> { dim.PortalMob = blankToNull(v); onChanged.run(); }, addEditBox);
        row = portalField(font, x, row, dim.PortalIgnitionSource,  v -> { dim.PortalIgnitionSource = blankToNull(v); onChanged.run(); }, addEditBox);

        // Respawn checkbox
        boolean respawn = Boolean.TRUE.equals(dim.RespawnInDimension);
        Button respawnBtn = Button.builder(
            Component.literal((respawn ? "[✓] " : "[ ] ") + "RespawnInDimension"),
            b -> { dim.RespawnInDimension = !Boolean.TRUE.equals(dim.RespawnInDimension); onChanged.run(); onStructureChanged.run(); }
        ).bounds(x, row, 200, 18).build();
        addButton.accept(respawnBtn);
    }

    private int portalField(net.minecraft.client.gui.Font font, int x, int row,
                             String value, Consumer<String> setter, Consumer<EditBox> addEditBox) {
        EditBox box = new EditBox(font, x + 90, row, 220, 16, Component.empty());
        box.setMaxLength(128);
        box.setValue(value == null ? "" : value);
        box.setResponder(setter);
        addEditBox.accept(box);
        return row + 20;
    }

    private String currentNonOTGType() {
        if (!(dim instanceof WorldPresetConfig.OTGOverWorld ow)) return "normal";
        return ow.NonOTGWorldType == null ? "normal" : ow.NonOTGWorldType;
    }

    private void cycleNonOTGType() {
        if (!(dim instanceof WorldPresetConfig.OTGOverWorld ow)) return;
        String current = currentNonOTGType();
        int idx = BUILT_IN_NON_OTG_TYPES.indexOf(current);
        // If value is custom (e.g. "biomesoplenty"), cycle starts from 0
        int next = (idx + 1) % BUILT_IN_NON_OTG_TYPES.size();
        ow.NonOTGWorldType = BUILT_IN_NON_OTG_TYPES.get(next);
        onChanged.run();
        onStructureChanged.run();
    }

    private void cycleOtgPreset() {
        if (otgPresets.isEmpty()) return;
        int idx = otgPresets.indexOf(dim.PresetFolderName);
        int next = (idx + 1) % otgPresets.size();
        dim.PresetFolderName = otgPresets.get(next);
        onChanged.run();
        onStructureChanged.run();
    }

    private void setUsingNonOTG(boolean useNonOTG) {
        if (!(dim instanceof WorldPresetConfig.OTGOverWorld ow)) return;
        this.usingNonOTG = useNonOTG;
        if (useNonOTG) {
            if (ow.NonOTGWorldType == null) ow.NonOTGWorldType = "normal";
            ow.PresetFolderName = null;
            this.usingVanilla = false;
        } else {
            ow.NonOTGWorldType = null;
            ow.NonOTGGeneratorSettings = null;
            if ((ow.PresetFolderName == null || ow.PresetFolderName.isBlank()) && !otgPresets.isEmpty()) {
                ow.PresetFolderName = otgPresets.get(0);
            }
        }
        onChanged.run();
        onStructureChanged.run();
    }

    private void setUsingVanilla(boolean vanilla) {
        this.usingVanilla = vanilla;
        if (vanilla) {
            dim.PresetFolderName = null;
        } else if (!otgPresets.isEmpty()) {
            dim.PresetFolderName = otgPresets.get(0);
        }
        onChanged.run();
        onStructureChanged.run();
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (!allowNonOTG && dim.isNonOTG()) {
            return; // read-only label already rendered as a widget button in init()
        }

        var font = Minecraft.getInstance().font;
        int row = y;

        // Mode selector row (OTG/Non-OTG or Use vanilla) — mirrors init()
        if (allowNonOTG || allowVanilla) row += 24;

        // Preset/type row — ONLY when not vanilla
        if (!usingVanilla) {
            row += 24;
            if (usingNonOTG) {
                g.drawString(font, "Generator Settings (JSON):", x, row + 4, 0xFF888888);
                row += 24;
            }
        }

        // Seed
        g.drawString(font, "Seed:", x, row + 4, 0xFFAAAAAA);
        row += 24;

        // Portal config (always rendered — matches init())
        g.drawString(font, "Portal Blocks:",   x, row + 4, 0xFFAAAAAA); row += 20;
        g.drawString(font, "Portal Color:",    x, row + 4, 0xFFAAAAAA); row += 20;
        g.drawString(font, "Portal Mob:",      x, row + 4, 0xFFAAAAAA); row += 20;
        g.drawString(font, "Ignition Source:", x, row + 4, 0xFFAAAAAA);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
