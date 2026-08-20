package com.mapartassistant.gui;

import com.mapartassistant.MapArtManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MapArtLogScreen extends Screen {
    private final Screen parent;
    
    public MapArtLogScreen(Screen parent) {
        super(Component.literal("Map Art Action Log"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int buttonY = this.height - 35;
        
        this.addRenderableWidget(Button.builder(
            Component.literal("Clear Logs"),
            (button) -> {
                MapArtManager.actionLog.clear();
            }
        ).bounds(centerX - 105, buttonY, 100, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("Back"),
            (button) -> {
                this.minecraft.setScreen(parent);
            }
        ).bounds(centerX + 5, buttonY, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        guiGraphics.drawCenteredString(this.font, Component.literal("§6§lAction Log"), this.width / 2, 15, 0xFFFFFFFF);
        
        int y = 40;
        // Draw logs from newest (index 0) to oldest
        for (int i = 0; i < MapArtManager.actionLog.size(); i++) {
            if (y > this.height - 50) break; // Stop if off screen
            guiGraphics.drawString(this.font, MapArtManager.actionLog.get(i), 20, y, 0xFFDDDDDD);
            y += 12;
        }
    }
}
