package com.mapartassistant;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.lwjgl.glfw.GLFW;
import com.mapartassistant.gui.MapArtConfigScreen;

public class MapArtModClient implements ClientModInitializer {
    
    private static KeyMapping configKeyBinding;
    private static KeyMapping startStopKeyBinding;
    private static KeyMapping pauseResumeKeyBinding;

    @Override
    public void onInitializeClient() {
        MapArtManager.loadConfig();

        KeyMapping.Category cat = new KeyMapping.Category(Identifier.parse("mapartassistant:category"));

        configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "Open MapArt Config",
            GLFW.GLFW_KEY_O,
            cat
        ));

        startStopKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "Start/Stop Pathing",
            GLFW.GLFW_KEY_P,
            cat
        ));
        
        pauseResumeKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "Pause/Resume Pathing",
            GLFW.GLFW_KEY_LEFT_BRACKET,
            cat
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (configKeyBinding.consumeClick()) {
                client.setScreen(new MapArtConfigScreen());
            }

            if (startStopKeyBinding.consumeClick()) {
                if (MapArtManager.isRunning()) {
                    MapArtManager.stop();
                    if (client.player != null) client.player.displayClientMessage(Component.literal("§c[MapArt] Pathing STOPPED."), false);
                } else {
                    MapArtManager.start();
                    if (client.player != null) client.player.displayClientMessage(Component.literal("§a[MapArt] Pathing STARTED."), false);
                }
            }
            
            if (pauseResumeKeyBinding.consumeClick()) {
                if (MapArtManager.isRunning()) {
                    if (MapArtManager.isPaused()) {
                        MapArtManager.resume();
                        if (client.player != null) client.player.displayClientMessage(Component.literal("§a[MapArt] Pathing RESUMED."), false);
                    } else {
                        MapArtManager.pause();
                        if (client.player != null) client.player.displayClientMessage(Component.literal("§e[MapArt] Pathing PAUSED."), false);
                    }
                }
            }

            MapArtManager.tick(client);
        });
        
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            String text = message.getString();
            if (text.startsWith("[Baritone]") || text.startsWith("> goto ")) {
                return false;
            }
            return true;
        });

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String prefix = MapArtManager.getCommandPrefix();
            if (message.startsWith(prefix)) {
                handleCommand(message, prefix);
                return false; // Cancel chat message sending to server
            }
            return true;
        });
        
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            if (MapArtManager.isRunning() && MapArtManager.isShowHudOverlay()) {
                Minecraft client = Minecraft.getInstance();
                int currentChest = MapArtManager.getCurrentChestIndex();
                if (currentChest >= 0 && currentChest < MapArtManager.chests.size()) {
                    String blockId = MapArtManager.chests.get(currentChest).blockId;
                    int count = MapArtManager.getInventoryCount(client, blockId);
                    
                    int width = client.getWindow().getGuiScaledWidth();
                    int height = client.getWindow().getGuiScaledHeight();
                    
                    int x = width / 2 + 100;
                    int y = height - 55;
                    
                    // Render background box
                    guiGraphics.fill(x - 2, y - 2, x + 60, y + 18, 0x90000000);
                    
                    // Render Item Icon
                    Identifier targetRl = Identifier.parse(blockId);
                    net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getValue(targetRl);
                    guiGraphics.renderItem(new ItemStack(item), x, y);
                    
                    // Render Text
                    int color = count > 0 ? 0xFFFFFFFF : 0xFFFF5555; // White or Red
                    guiGraphics.drawString(client.font, "x" + count, x + 20, y + 4, color);
                }
            }
        });
    }
    
    private void handleCommand(String message, String prefix) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        
        String[] args = message.split(" ");
        if (args.length == 1 || (args.length >= 2 && args[1].equalsIgnoreCase("help"))) {
            client.player.displayClientMessage(Component.literal("§6--- Map Art Assistant ---"), false);
            client.player.displayClientMessage(Component.literal("§e" + prefix + " setchest <block_id> §7- Registers the chest you are looking at."), false);
            client.player.displayClientMessage(Component.literal("§e" + prefix + " clean §7- Runs a cleanup pass to break double-stacked extra carpets."), false);
            client.player.displayClientMessage(Component.literal("§e" + prefix + " help §7- Shows this menu."), false);
            client.player.displayClientMessage(Component.literal("§7Press 'O' to open the config GUI."), false);
            return;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("setchest")) {
            String blockId = args[2].toLowerCase(); // e.g. minecraft:black_carpet
            
            // Check if player is looking at a block
            if (client.hitResult != null && client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                net.minecraft.world.phys.BlockHitResult blockHit = (net.minecraft.world.phys.BlockHitResult) client.hitResult;
                net.minecraft.core.BlockPos pos = blockHit.getBlockPos();
                
                MapArtManager.registerChest(blockId, pos.getX(), pos.getY(), pos.getZ());
                client.player.displayClientMessage(Component.literal("§a[MapArt] Registered chest for §e" + blockId + " §aat " + pos.toShortString()), false);
            } else {
                client.player.displayClientMessage(Component.literal("§c[MapArt] You must be looking at a block (chest) to use this command!"), false);
            }
        } else if (args.length >= 2 && args[1].equalsIgnoreCase("clean")) {
            MapArtManager.startCleanup();
            client.player.displayClientMessage(Component.literal("§a[MapArt] Starting cleanup pass for extra carpets..."), false);
        } else {
            client.player.displayClientMessage(Component.literal("§c[MapArt] Unknown command. Type " + prefix + " help for a list of commands."), false);
        }
    }
}
