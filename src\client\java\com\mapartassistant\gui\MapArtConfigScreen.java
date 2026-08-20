package com.mapartassistant.gui;

import com.mapartassistant.MapArtManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MapArtConfigScreen extends Screen {

    private ChestListWidget listWidget;

    public MapArtConfigScreen() {
        super(Component.literal("Map Art Assistant Config"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        this.listWidget = new ChestListWidget(this.minecraft, this.width, this.height - 80, 40, 32, this);
        
        this.addRenderableWidget(this.listWidget);

        int buttonY = this.height - 35;
        
        this.addRenderableWidget(Button.builder(
            Component.literal(MapArtManager.isRunning() ? "§cStop Pathing" : "§aStart Pathing"),
            (button) -> {
                if (MapArtManager.isRunning()) {
                    MapArtManager.stop();
                    button.setMessage(Component.literal("§aStart Pathing"));
                } else {
                    MapArtManager.start();
                    button.setMessage(Component.literal("§cStop Pathing"));
                }
            }
        ).bounds(centerX - 200, buttonY, 90, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("Settings"),
            (button) -> {
                this.minecraft.setScreen(new MapArtSettingsScreen(this));
            }
        ).bounds(centerX - 100, buttonY, 90, 20).build());
        
        this.addRenderableWidget(Button.builder(
            Component.literal("Logs"),
            (button) -> {
                this.minecraft.setScreen(new MapArtLogScreen(this));
            }
        ).bounds(centerX, buttonY, 90, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("Done"),
            (button) -> {
                this.onClose();
            }
        ).bounds(centerX + 100, buttonY, 90, 20).build());
    }

    public void refreshList() {
        if (this.minecraft != null) {
            this.clearWidgets();
            this.init();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Draw solid dark background over the entire screen to fix transparency issues
        guiGraphics.fill(0, 0, this.width, this.height, 0xDD000000);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        guiGraphics.drawCenteredString(this.font, Component.literal("§6§lMap Art Assistant - Chest Configuration"), this.width / 2, 15, 0xFFFFFFFF);
        
        guiGraphics.drawString(this.font, Component.literal("§7Mapped Chests: §e" + MapArtManager.chests.size() + " / " + MapArtManager.ALL_SUPPORTED_BLOCKS.size()), 20, 25, 0xFFFFFFFF);
    }
}
