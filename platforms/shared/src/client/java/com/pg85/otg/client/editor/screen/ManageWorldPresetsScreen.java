package com.pg85.otg.client.editor.screen;

import com.pg85.otg.OTG;
import com.pg85.otg.client.editor.data.WorldPresetFileScanner;
import com.pg85.otg.client.editor.data.WorldPresetOperations;
import com.pg85.otg.client.editor.widget.ScrollableListWidget;
import com.pg85.otg.config.dimensions.WorldPresetConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists all WorldPreset YAMLs with summary and CRUD buttons.
 */
public class ManageWorldPresetsScreen extends Screen {

    private final Screen parent;
    private List<WorldPresetFileScanner.WorldPresetEntry> entries = new ArrayList<>();
    private int selectedIdx = -1;
    private ScrollableListWidget listWidget;
    private String statusMessage;

    public ManageWorldPresetsScreen(Screen parent) {
        super(Component.literal("Manage WorldPresets"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        reloadEntries();

        int listX = 10;
        int listY = 30;
        int listW = 240;
        int listH = height - 80;

        listWidget = new ScrollableListWidget(listX, listY, listW, listH, 16);
        listWidget.setItems(labels());
        listWidget.setSelectedIndex(selectedIdx);
        listWidget.setOnSelect(idx -> {
            selectedIdx = idx;
            rebuildWidgets();
        });

        int btnY = height - 46;
        int bx = 10;
        addRenderableWidget(Button.builder(Component.literal("New"), b ->
            minecraft.setScreen(new WorldPresetWizardScreen(this))
        ).bounds(bx, btnY, 56, 20).build());
        bx += 60;

        Button cloneBtn = Button.builder(Component.literal("Clone"), b -> doClone())
            .bounds(bx, btnY, 56, 20).build();
        cloneBtn.active = validSelected();
        addRenderableWidget(cloneBtn);
        bx += 60;

        Button editBtn = Button.builder(Component.literal("Edit"), b -> doEdit())
            .bounds(bx, btnY, 56, 20).build();
        editBtn.active = validSelected();
        addRenderableWidget(editBtn);
        bx += 60;

        Button deleteBtn = Button.builder(Component.literal("Delete"), b -> doDelete())
            .bounds(bx, btnY, 56, 20).build();
        deleteBtn.active = selectedIdx >= 0 && selectedIdx < entries.size();
        addRenderableWidget(deleteBtn);

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
            .bounds(width - 80, height - 30, 70, 20).build());
    }

    private void reloadEntries() {
        entries = WorldPresetFileScanner.scan(OTG.getEngine().getOTGRootFolder());
        if (selectedIdx >= entries.size()) selectedIdx = -1;
    }

    private List<String> labels() {
        List<String> out = new ArrayList<>();
        for (var e : entries) out.add(e.displayName());
        return out;
    }

    private boolean validSelected() {
        return selectedIdx >= 0 && selectedIdx < entries.size() && entries.get(selectedIdx).valid();
    }

    private void doEdit() {
        if (!validSelected()) return;
        var entry = entries.get(selectedIdx);
        minecraft.setScreen(new WorldPresetEditorScreen(entry.path(), this));
    }

    private void doClone() {
        if (!validSelected()) return;
        var entry = entries.get(selectedIdx);
        String origName = entry.config().DisplayName == null || entry.config().DisplayName.isBlank()
            ? "copy" : entry.config().DisplayName;
        String newName = origName + " (copy)";
        var result = WorldPresetOperations.cloneFrom(entry.path(),
            OTG.getEngine().getOTGRootFolder(), newName);
        if (result != null) {
            statusMessage = "Cloned to " + result.getFileName();
            reloadEntries();
            rebuildWidgets();
        } else {
            statusMessage = "Clone failed";
        }
    }

    private void doDelete() {
        if (selectedIdx < 0 || selectedIdx >= entries.size()) return;
        var entry = entries.get(selectedIdx);

        boolean matchesActive = isActiveWorldYaml(entry.path());
        Component message = matchesActive
            ? Component.literal("This WorldPreset is active in your current world. "
                + "Deleting won't affect the active world, but you won't be able to recreate it. "
                + "Delete " + entry.path().getFileName() + "?")
            : Component.literal("Delete " + entry.path().getFileName() + "?");

        minecraft.setScreen(new ConfirmScreen(
            confirmed -> {
                if (confirmed) {
                    if (WorldPresetOperations.delete(entry.path())) {
                        statusMessage = "Deleted " + entry.path().getFileName();
                        selectedIdx = -1;
                        reloadEntries();
                    } else {
                        statusMessage = "Delete failed";
                    }
                }
                minecraft.setScreen(this);
            },
            Component.literal("Delete WorldPreset"),
            message,
            Component.literal("Delete"),
            Component.literal("Cancel")
        ));
    }

    private boolean isActiveWorldYaml(Path yamlPath) {
        try {
            var dimMgr = com.pg85.otg.shared.commands.OTGCommandRegistrar.getDimensionManager();
            if (dimMgr == null) return false;
            Path activePath = dimMgr.getActiveWorldPresetConfigPath();
            if (activePath == null) return false;
            return Files.isSameFile(yamlPath, activePath);
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        if (listWidget != null) listWidget.render(g);

        if (validSelected()) {
            WorldPresetConfig c = entries.get(selectedIdx).config();
            int sx = 270;
            int sy = 40;
            g.drawString(font, "DisplayName: " + (c.DisplayName == null ? "" : c.DisplayName), sx, sy, 0xFFCCCCCC);
            sy += 12;
            g.drawString(font, "Description: " + (c.Description == null ? "" : c.Description), sx, sy, 0xFFCCCCCC);
            sy += 12;
            g.drawString(font, "Overworld:   " + describeSlot(c.Overworld), sx, sy, 0xFFCCCCCC);
            sy += 12;
            g.drawString(font, "Nether:      " + describeSlot(c.Nether), sx, sy, 0xFFCCCCCC);
            sy += 12;
            g.drawString(font, "End:         " + describeSlot(c.End), sx, sy, 0xFFCCCCCC);
            sy += 12;
            int dimCount = c.Dimensions == null ? 0 : c.Dimensions.size();
            g.drawString(font, "Custom Dimensions: " + dimCount, sx, sy, 0xFFCCCCCC);
        } else if (selectedIdx >= 0 && selectedIdx < entries.size()) {
            g.drawString(font, "(invalid YAML — Edit/Clone disabled)", 270, 40, 0xFFFF6666);
        }

        if (statusMessage != null) {
            g.drawString(font, statusMessage, 10, height - 12, 0xFFAAAA44);
        }
    }

    private String describeSlot(WorldPresetConfig.OTGDimension dim) {
        if (dim == null) return "vanilla";
        if (dim.isNonOTG()) return dim.NonOTGWorldType;
        if (dim.PresetFolderName == null || dim.PresetFolderName.isBlank()) return "vanilla";
        return dim.PresetFolderName;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listWidget != null && listWidget.mouseClicked(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (listWidget != null && listWidget.mouseScrolled(mouseX, mouseY, dy)) return true;
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
