package com.pg85.otg.client.editor.widget;

import com.pg85.otg.config.dimensions.WorldPresetConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Expandable card for a custom dimension in the Dimensions tab.
 * Collapsed: header with PresetFolderName + Remove button.
 * Expanded: full DimensionSlotWidget + Edit GameRules button.
 */
public class DimensionAccordionCard {

    public static final int COLLAPSED_HEIGHT = 22;
    public static final int EXPANDED_HEIGHT = 220;

    private final WorldPresetConfig.OTGDimension dim;
    private final Runnable onRemove;
    private final Runnable onOpenGameRules;
    private final Runnable onToggleExpand;
    private final List<String> otgPresets;
    private final Runnable onChanged;
    private final Runnable onStructureChanged;

    private final boolean expanded;

    private int x, y;
    private int renderedWidth;
    private DimensionSlotWidget slotWidget;

    public DimensionAccordionCard(WorldPresetConfig.OTGDimension dim,
                                    boolean expanded,
                                    Runnable onRemove,
                                    Runnable onOpenGameRules,
                                    Runnable onToggleExpand,
                                    List<String> otgPresets,
                                    Runnable onChanged,
                                    Runnable onStructureChanged) {
        this.dim = dim;
        this.expanded = expanded;
        this.onRemove = onRemove;
        this.onOpenGameRules = onOpenGameRules;
        this.onToggleExpand = onToggleExpand;
        this.otgPresets = otgPresets;
        this.onChanged = onChanged;
        this.onStructureChanged = onStructureChanged;
    }

    public int getHeight() {
        return expanded ? EXPANDED_HEIGHT : COLLAPSED_HEIGHT;
    }

    public void init(int x, int y, int width, Consumer<Button> addButton, Consumer<EditBox> addEditBox) {
        this.x = x;
        this.y = y;
        this.renderedWidth = width;

        String label = (expanded ? "▼ " : "▶ ") +
            (dim.isNonOTG() ? "Non-OTG: " + dim.NonOTGWorldType
                : dim.PresetFolderName == null ? "(unset)" : dim.PresetFolderName);
        Button headerBtn = Button.builder(
            Component.literal(label),
            b -> onToggleExpand.run()
        ).bounds(x, y, width - 80, 18).build();
        addButton.accept(headerBtn);

        Button removeBtn = Button.builder(
            Component.literal("- Remove"),
            b -> onRemove.run()
        ).bounds(x + width - 76, y, 74, 18).build();
        addButton.accept(removeBtn);

        if (expanded) {
            slotWidget = new DimensionSlotWidget(
                dim, false, false, otgPresets, onChanged, onStructureChanged
            );
            slotWidget.init(x + 4, y + 22, width - 8, addButton, addEditBox);

            Button gameRulesBtn = Button.builder(
                Component.literal("Edit GameRules Override"),
                b -> onOpenGameRules.run()
            ).bounds(x + 4, y + EXPANDED_HEIGHT - 22, width - 8, 18).build();
            addButton.accept(gameRulesBtn);
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(x, y + 20, x + renderedWidth, y + getHeight(), 0xFF1E1E1E);
        if (expanded && slotWidget != null) {
            slotWidget.render(g, mouseX, mouseY);
        }
    }

    public boolean isExpanded() { return expanded; }
}
