package com.mapartassistant.gui;

import com.mapartassistant.MapArtManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ChestListWidget extends ContainerObjectSelectionList<ChestListWidget.ChestEntry> {

    private final Screen parent;

    public ChestListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight, Screen parent) {
        super(minecraft, width, height, y, itemHeight);
        this.parent = parent;

        for (String blockId : MapArtManager.ALL_SUPPORTED_BLOCKS) {
            this.addEntry(new ChestEntry(blockId));
        }
    }

    @Override
    public int getRowWidth() {
        return 380;
    }

    protected int getScrollbarPosition() {
        return this.getX() + this.width - 6;
    }

    public class ChestEntry extends ContainerObjectSelectionList.Entry<ChestEntry> {
        private final String blockId;
        private final net.minecraft.world.item.Item item;
        private final Button editButton;

        public ChestEntry(String blockId) {
            this.blockId = blockId;
            this.item = BuiltInRegistries.ITEM.get(Identifier.parse(blockId)).get().value();
            
            this.editButton = Button.builder(Component.literal("Edit"), button -> {
                Minecraft.getInstance().setScreen(new ChestEditScreen(parent, blockId));
            }).bounds(0, 0, 50, 20).build();
        }

        @Override
        public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return Collections.singletonList(editButton);
        }

        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return Collections.singletonList(editButton);
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean isHovered, float partialTick) {
            int left = ChestListWidget.this.width / 2 - 150;
            int top = this.getY();
            
            // Render Icon
            graphics.renderItem(new ItemStack(item), left + 5, top + 6);
            
            // Render Block ID
            graphics.drawString(Minecraft.getInstance().font, Component.literal(blockId), left + 30, top + 10, 0xFFFFFFFF);
            
            // Render chest coordinate if mapped
            Optional<MapArtManager.ChestEntry> mapped = MapArtManager.chests.stream().filter(c -> c.blockId.equals(blockId)).findFirst();
            if (mapped.isPresent()) {
                String coord = String.format("Mapped: %d, %d, %d", mapped.get().x, mapped.get().y, mapped.get().z);
                graphics.drawString(Minecraft.getInstance().font, Component.literal("\u00A7a" + coord), left + 150, top + 10, 0xFF55FF55);
            } else {
                graphics.drawString(Minecraft.getInstance().font, Component.literal("\u00A7cUnmapped"), left + 150, top + 10, 0xFFFF5555);
            }

            // Buttons bounds update (must be absolute)
            editButton.setX(left + 315);
            editButton.setY(top + 6);
            editButton.render(graphics, mouseX, mouseY, partialTick);
        }
    }
}
