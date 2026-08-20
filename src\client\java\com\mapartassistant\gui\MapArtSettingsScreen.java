package com.mapartassistant.gui;

import com.mapartassistant.MapArtManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MapArtSettingsScreen extends Screen {
    private final Screen parent;
    
    private EditBox startXEdit;
    private EditBox startZEdit;
    private EditBox pathWidthEdit;
    private EditBox mapLengthEdit;
    private EditBox mapWidthEdit;
    private EditBox commandPrefixEdit;

    public MapArtSettingsScreen(Screen parent) {
        super(Component.literal("Advanced Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int boxWidth = 200;
        int startY = centerY - 80;
        int leftX = centerX - boxWidth / 2;

        this.startXEdit = new EditBox(this.font, leftX + 10, startY + 20, 45, 20, Component.literal("Start X"));
        this.startXEdit.setValue(String.valueOf(MapArtManager.getStartX()));
        this.addRenderableWidget(this.startXEdit);

        this.startZEdit = new EditBox(this.font, leftX + 60, startY + 20, 45, 20, Component.literal("Start Z"));
        this.startZEdit.setValue(String.valueOf(MapArtManager.getStartZ()));
        this.addRenderableWidget(this.startZEdit);

        this.addRenderableWidget(Button.builder(
            Component.literal("Current Pos"),
            (button) -> {
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.startXEdit.setValue(String.valueOf(this.minecraft.player.getBlockX()));
                    this.startZEdit.setValue(String.valueOf(this.minecraft.player.getBlockZ()));
                }
            }
        ).bounds(leftX + 110, startY + 20, 80, 20).build());

        this.mapWidthEdit = new EditBox(this.font, leftX + 10, startY + 60, 50, 20, Component.literal("Map Width"));
        this.mapWidthEdit.setValue(String.valueOf(MapArtManager.getMapWidth()));
        this.addRenderableWidget(this.mapWidthEdit);

        this.mapLengthEdit = new EditBox(this.font, leftX + 70, startY + 60, 50, 20, Component.literal("Map Length"));
        this.mapLengthEdit.setValue(String.valueOf(MapArtManager.getMapLength()));
        this.addRenderableWidget(this.mapLengthEdit);

        this.pathWidthEdit = new EditBox(this.font, leftX + 130, startY + 60, 60, 20, Component.literal("Path Width"));
        this.pathWidthEdit.setValue(String.valueOf(MapArtManager.getPathWidth()));
        this.addRenderableWidget(this.pathWidthEdit);

        this.commandPrefixEdit = new EditBox(this.font, leftX + 10, startY + 100, 80, 20, Component.literal("Cmd Prefix"));
        this.commandPrefixEdit.setValue(MapArtManager.getCommandPrefix());
        this.addRenderableWidget(this.commandPrefixEdit);

        this.addRenderableWidget(Button.builder(
            Component.literal("HUD: " + (MapArtManager.isShowHudOverlay() ? "ON" : "OFF")),
            (button) -> {
                MapArtManager.setShowHudOverlay(!MapArtManager.isShowHudOverlay());
                button.setMessage(Component.literal("HUD: " + (MapArtManager.isShowHudOverlay() ? "ON" : "OFF")));
            }
        ).bounds(leftX + 110, startY + 100, 80, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("Save & Back"),
            (button) -> {
                saveValues();
                this.minecraft.setScreen(parent);
            }
        ).bounds(centerX - 100, startY + 140, 200, 20).build());
    }

    private void saveValues() {
        try {
            MapArtManager.setStartX(Integer.parseInt(startXEdit.getValue()));
            MapArtManager.setStartZ(Integer.parseInt(startZEdit.getValue()));
            MapArtManager.setPathWidth(Integer.parseInt(pathWidthEdit.getValue()));
            MapArtManager.setMapLength(Integer.parseInt(mapLengthEdit.getValue()));
            MapArtManager.setMapWidth(Integer.parseInt(mapWidthEdit.getValue()));
            MapArtManager.setCommandPrefix(commandPrefixEdit.getValue());
        } catch (NumberFormatException e) {
            // ignore
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int boxWidth = 200;
        int boxHeight = 180;
        int startY = centerY - 80;
        int leftX = centerX - boxWidth / 2;

        guiGraphics.fill(leftX, startY, leftX + boxWidth, startY + boxHeight, 0x90000000);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, Component.literal("§6§lAdvanced Map Settings"), centerX, startY - 20, -1);
        
        guiGraphics.drawString(this.font, Component.literal("§7Start X:"), leftX + 10, startY + 10, -1);
        guiGraphics.drawString(this.font, Component.literal("§7Start Z:"), leftX + 60, startY + 10, -1);
        
        guiGraphics.drawString(this.font, Component.literal("§7Map X-Width:"), leftX + 10, startY + 50, -1);
        guiGraphics.drawString(this.font, Component.literal("§7Map Z-Length:"), leftX + 70, startY + 50, -1);
        guiGraphics.drawString(this.font, Component.literal("§7Path Width:"), leftX + 130, startY + 50, -1);

        guiGraphics.drawString(this.font, Component.literal("§7Cmd Prefix:"), leftX + 10, startY + 90, -1);
    }
}
