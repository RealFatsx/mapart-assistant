package com.mapartassistant.gui;

import com.mapartassistant.MapArtManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Optional;

public class ChestEditScreen extends Screen {
    private final Screen parent;
    private final String blockId;

    private EditBox xEdit;
    private EditBox yEdit;
    private EditBox zEdit;

    public ChestEditScreen(Screen parent, String blockId) {
        super(Component.literal("Edit Chest: " + blockId));
        this.parent = parent;
        this.blockId = blockId;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        Optional<MapArtManager.ChestEntry> mapped = MapArtManager.chests.stream().filter(c -> c.blockId.equals(blockId)).findFirst();
        String initX = mapped.isPresent() ? String.valueOf(mapped.get().x) : "";
        String initY = mapped.isPresent() ? String.valueOf(mapped.get().y) : "";
        String initZ = mapped.isPresent() ? String.valueOf(mapped.get().z) : "";

        this.xEdit = new EditBox(this.font, centerX - 80, centerY - 20, 50, 20, Component.literal("X"));
        this.xEdit.setValue(initX);
        this.addRenderableWidget(this.xEdit);

        this.yEdit = new EditBox(this.font, centerX - 25, centerY - 20, 50, 20, Component.literal("Y"));
        this.yEdit.setValue(initY);
        this.addRenderableWidget(this.yEdit);

        this.zEdit = new EditBox(this.font, centerX + 30, centerY - 20, 50, 20, Component.literal("Z"));
        this.zEdit.setValue(initZ);
        this.addRenderableWidget(this.zEdit);

        this.addRenderableWidget(Button.builder(
            Component.literal("Use Current Pos"),
            (button) -> {
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.xEdit.setValue(String.valueOf(this.minecraft.player.getBlockX()));
                    this.yEdit.setValue(String.valueOf(this.minecraft.player.getBlockY()));
                    this.zEdit.setValue(String.valueOf(this.minecraft.player.getBlockZ()));
                }
            }
        ).bounds(centerX - 80, centerY + 10, 160, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("Save"),
            (button) -> {
                save();
                this.minecraft.setScreen(parent);
            }
        ).bounds(centerX - 80, centerY + 40, 75, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("Cancel / Unmap"),
            (button) -> {
                // Clear the mapping
                MapArtManager.chests.removeIf(c -> c.blockId.equals(blockId));
                MapArtManager.saveConfig();
                if (parent instanceof MapArtConfigScreen) {
                    ((MapArtConfigScreen) parent).refreshList();
                }
                this.minecraft.setScreen(parent);
            }
        ).bounds(centerX + 5, centerY + 40, 75, 20).build());
    }

    private void save() {
        try {
            int x = Integer.parseInt(xEdit.getValue());
            int y = Integer.parseInt(yEdit.getValue());
            int z = Integer.parseInt(zEdit.getValue());
            MapArtManager.registerChest(blockId, x, y, z);
            if (parent instanceof MapArtConfigScreen) {
                ((MapArtConfigScreen) parent).refreshList();
            }
        } catch (NumberFormatException e) {
            MapArtManager.chests.removeIf(c -> c.blockId.equals(blockId));
            MapArtManager.saveConfig();
            if (parent instanceof MapArtConfigScreen) {
                ((MapArtConfigScreen) parent).refreshList();
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xDD000000);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, Component.literal("Edit Chest for " + blockId), this.width / 2, 20, 0xFFFFFFFF);
        
        graphics.drawString(this.font, Component.literal("X Coordinate:"), this.width / 2 - 100, 60, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.literal("Y Coordinate:"), this.width / 2 - 100, 100, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.literal("Z Coordinate:"), this.width / 2 - 100, 140, 0xFFFFFFFF);
    }
}
