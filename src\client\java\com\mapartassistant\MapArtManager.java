package com.mapartassistant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MapArtManager {
    private static boolean running = false;
    private static boolean paused = false;

    private static int startX = 0;
    private static int startZ = 0;
    private static int pathWidth = 5;
    private static int mapLength = 128;
    private static int mapWidth = 128;
    private static boolean showHudOverlay = true;
    private static String commandPrefix = "@mapart";

    public static final List<String> ALL_SUPPORTED_BLOCKS = List.of(
        "minecraft:white_carpet", "minecraft:orange_carpet", "minecraft:magenta_carpet", "minecraft:light_blue_carpet",
        "minecraft:yellow_carpet", "minecraft:lime_carpet", "minecraft:pink_carpet", "minecraft:gray_carpet",
        "minecraft:light_gray_carpet", "minecraft:cyan_carpet", "minecraft:purple_carpet", "minecraft:blue_carpet",
        "minecraft:brown_carpet", "minecraft:green_carpet", "minecraft:red_carpet", "minecraft:black_carpet",
        "minecraft:oak_pressure_plate", "minecraft:spruce_pressure_plate", "minecraft:birch_pressure_plate",
        "minecraft:jungle_pressure_plate", "minecraft:acacia_pressure_plate", "minecraft:dark_oak_pressure_plate",
        "minecraft:mangrove_pressure_plate", "minecraft:cherry_pressure_plate", "minecraft:bamboo_pressure_plate",
        "minecraft:crimson_pressure_plate", "minecraft:warped_pressure_plate", "minecraft:stone_pressure_plate",
        "minecraft:polished_blackstone_pressure_plate", "minecraft:heavy_weighted_pressure_plate", "minecraft:light_weighted_pressure_plate"
    );

    public static List<ChestEntry> chests = new ArrayList<>();
    public static List<String> actionLog = new ArrayList<>();
    
    private static final Path DEBUG_LOG_PATH = FabricLoader.getInstance().getGameDir().resolve("mapart_debug.log");

    public static void debugLog(String message) {
        try (FileWriter writer = new FileWriter(DEBUG_LOG_PATH.toFile(), true)) {
            writer.write(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date()) + " - " + message + "\n");
        } catch (Exception e) {}
    }
    
    public static void log(String message) {
        debugLog("[INFO] " + message.replaceAll("Â§[0-9a-fk-or]", ""));
        actionLog.add(0, new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()) + " - " + message);
        if (actionLog.size() > 100) actionLog.remove(actionLog.size() - 1);
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Â§e[MapArt] Â§f" + message), false);
        }
    }
    
    private static int currentChestIndex = 0;
    private static int currentPathIndex = 0;
    private static int currentPassTotalBlocksNeeded = 0;
    private static List<int[]> currentPathWaypoints = new ArrayList<>();
    private static boolean currentChestFrontFound = false;
    private static long chestOpenTime = 0;
    private static long cleanupBreakWaitStart = 0;
    private static boolean isCurrentlyBreaking = false;
    private static int lastRestockInventoryCount = -1;
    private static int failedRestockClickCount = 0;
    private static int lastDepositInventoryCount = -1;
    private static int failedDepositClickCount = 0;
    
    private enum State {
        IDLE,
        GOING_TO_CHEST,
        RESTOCKING,
        PATHING_TO_WAYPOINT,
        WAITING_FOR_BARITONE,
        RETURNING_TO_CHEST,
        DEPOSITING,
        DROPPING
    }
    
    private static State state = State.IDLE;
    private static State lastState = State.IDLE;
    private static long stateWaitStart = 0;
    private static long lastBaritoneCheckTime = 0;
    private static double lastPosX, lastPosY, lastPosZ;
    private static int idleTicks = 0;
    private static int restockSlotIndex = 0;
    private static int depositSlotIndex = -1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("mapart.json");

    public static void loadConfig() {
        File file = CONFIG_PATH.toFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    if (data.startX != null) startX = data.startX;
                    if (data.startZ != null) startZ = data.startZ;
                    if (data.pathWidth != null) pathWidth = data.pathWidth;
                    if (data.mapLength != null) mapLength = data.mapLength;
                    if (data.mapWidth != null) mapWidth = data.mapWidth;
                    if (data.showHudOverlay != null) showHudOverlay = data.showHudOverlay;
                    if (data.commandPrefix != null) commandPrefix = data.commandPrefix;
                    if (data.chests != null) {
                        chests = data.chests;
                        for (ChestEntry c : chests) {
                            c.originalY = c.y;
                        }
                    }
                }
            } catch (Exception e) {}
        }
    }

    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            ConfigData data = new ConfigData();
            data.startX = startX;
            data.startZ = startZ;
            data.pathWidth = pathWidth;
            data.mapLength = mapLength;
            data.mapWidth = mapWidth;
            data.showHudOverlay = showHudOverlay;
            data.commandPrefix = commandPrefix;
            data.chests = chests;
            GSON.toJson(data, writer);
        } catch (Exception e) {}
    }

    public static void registerChest(String blockId, int x, int y, int z) {
        chests.removeIf(c -> c.blockId.equals(blockId));
        chests.add(new ChestEntry(blockId, x, y, z));
        saveConfig();
    }

    public static boolean isRunning() { return running; }
    public static boolean isPaused() { return paused; }

    public static void start() {
        running = true;
        paused = false;
        currentChestIndex = 0;
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client.player != null) {
            MapArtConfigValidator.validate(client);
            client.player.connection.sendChat("#allowPlace false");
            client.player.connection.sendChat("#allowBreak false");
        }
        beginPass(0);
    }

    public static void stop() {
        running = false;
        state = State.IDLE;
        cancelBaritone();
    }

    public static void pause() {
        paused = true;
        cancelBaritone();
    }

    public static void resume() {
        paused = false;
        // Re-issue current goal
        if (state == State.WAITING_FOR_BARITONE) {
            if (currentPathIndex < currentPathWaypoints.size()) {
                int[] wp = currentPathWaypoints.get(currentPathIndex);
                net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                int targetY = client.player != null ? client.player.getBlockY() : wp[1];
                sendBaritoneGoto(wp[0], targetY, wp[2]);
            }
        }
    }

    private static boolean generatePathWaypoints() {
        currentPathWaypoints.clear();
        
        try {
            net.minecraft.world.level.Level schematicLevel = fi.dy.masa.litematica.world.SchematicWorldHandler.getSchematicWorld();
            net.minecraft.world.level.Level clientLevel = net.minecraft.client.Minecraft.getInstance().level;
            
            if (schematicLevel != null && clientLevel != null) {
                String targetBlockId = (currentChestIndex >= 0 && currentChestIndex < chests.size()) 
                    ? chests.get(currentChestIndex).blockId : null;
                    
                int searchY = net.minecraft.client.Minecraft.getInstance().player != null ? net.minecraft.client.Minecraft.getInstance().player.getBlockY() : 64;
                List<int[]> allMissing = new ArrayList<>();
                currentPassTotalBlocksNeeded = 0;
                for (int yOffset = -2; yOffset <= 2; yOffset++) {
                    int y = searchY + yOffset;
                    for (int z = 0; z < mapLength; z++) {
                        int absoluteZ = startZ + z;
                        for (int x = 0; x < mapWidth; x++) {
                            int absoluteX = startX + x;
                            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(absoluteX, y, absoluteZ);
                            net.minecraft.world.level.block.state.BlockState schematicState = schematicLevel.getBlockState(pos);
                            
                            if (isCleaningUp) {
                                net.minecraft.world.level.block.state.BlockState worldState = clientLevel.getBlockState(pos);
                                if (worldState.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock && schematicState.isAir()) {
                                    allMissing.add(new int[]{absoluteX, y, absoluteZ});
                                }
                            } else if (!schematicState.isAir()) {
                                net.minecraft.world.level.block.state.BlockState worldState = clientLevel.getBlockState(pos);
                                if (!schematicState.getBlock().equals(worldState.getBlock())) {
                                    String missingId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(schematicState.getBlock()).toString();
                                    if (targetBlockId == null || targetBlockId.equals(missingId)) {
                                        currentPassTotalBlocksNeeded++;
                                        // Cluster by pathWidth to prevent over-generating points next to each other
                                        boolean tooClose = false;
                                        for (int[] existing : allMissing) {
                                            if (Math.abs(existing[0] - absoluteX) < pathWidth && Math.abs(existing[2] - absoluteZ) < pathWidth) {
                                                tooClose = true;
                                                break;
                                            }
                                        }
                                        if (!tooClose) {
                                            allMissing.add(new int[]{absoluteX, y, absoluteZ});
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // TSP Nearest Neighbor Sorting
                if (!allMissing.isEmpty()) {
                    int currentX = net.minecraft.client.Minecraft.getInstance().player != null ? net.minecraft.client.Minecraft.getInstance().player.getBlockX() : startX;
                    int currentZ = net.minecraft.client.Minecraft.getInstance().player != null ? net.minecraft.client.Minecraft.getInstance().player.getBlockZ() : startZ;
                    
                    while (!allMissing.isEmpty()) {
                        int bestIndex = -1;
                        double bestDist = Double.MAX_VALUE;
                        for (int i = 0; i < allMissing.size(); i++) {
                            int[] pt = allMissing.get(i);
                            double dist = Math.pow(pt[0] - currentX, 2) + Math.pow(pt[2] - currentZ, 2);
                            if (dist < bestDist) {
                                bestDist = dist;
                                bestIndex = i;
                            }
                        }
                        int[] nextWp = allMissing.remove(bestIndex);
                        currentPathWaypoints.add(nextWp);
                        currentX = nextWp[0];
                        currentZ = nextWp[2];
                    }
                    
                    log("Â§aFound " + currentPathWaypoints.size() + " efficient waypoints from Litematica scan!");
                    return true; 
                }
                
                log("Â§eNo missing blocks found for " + targetBlockId + "! Skipping color...");
                return false;
            }
        } catch (Throwable t) {
            System.err.println("[MapArt] Litematica pathing failed or not installed: " + t.getMessage());
        }
        
        // Fallback to blind snake pattern
        log("Â§eFalling back to blind grid pathing...");
        int fallbackY = net.minecraft.client.Minecraft.getInstance().player != null ? net.minecraft.client.Minecraft.getInstance().player.getBlockY() : 64;
        for (int z = 0; z < mapLength; z += pathWidth) {
            int absoluteZ = startZ + z;
            currentPathWaypoints.add(new int[]{startX, fallbackY, absoluteZ});
            currentPathWaypoints.add(new int[]{startX + mapWidth - 1, fallbackY, absoluteZ});
            z += pathWidth;
            if (z >= mapLength) break;
            int absoluteZNext = startZ + z;
            currentPathWaypoints.add(new int[]{startX + mapWidth - 1, fallbackY, absoluteZNext});
            currentPathWaypoints.add(new int[]{startX, fallbackY, absoluteZNext});
        }
        return true;
    }

    private static void beginPass(int chestIndex) {
        if (chestIndex >= chests.size()) {
            if (chests.isEmpty() && chestIndex == 0) {
                // If there are no chests, just run the pathing once
                currentChestIndex = -1;
                currentPathIndex = 0;
                generatePathWaypoints();
                state = State.PATHING_TO_WAYPOINT;
                if (!currentPathWaypoints.isEmpty()) {
                    int[] wp = currentPathWaypoints.get(0);
                    net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                    int targetY = client.player != null ? client.player.getBlockY() : wp[1];
                    sendBaritoneGoto(wp[0], targetY, wp[2]);
                    state = State.WAITING_FOR_BARITONE;
                    log("Â§aStarting chestless pathing pass!");
                } else {
                    stop();
                }
                return;
            }
            stop();
            log("Â§aPathing finished!");
            return;
        }
        if (chestIndex >= 0 && chestIndex < chests.size()) {
            ChestEntry chest = chests.get(chestIndex);
            if (chest.originalY != -1) chest.y = chest.originalY;
        }
        
        currentChestIndex = chestIndex;
        currentPathIndex = 0;
        
        boolean hasWaypoints = generatePathWaypoints();
        if (!hasWaypoints) {
            if (isCleaningUp) {
                isCleaningUp = false;
                log("Â§aCleanup pass finished!");
                stop();
                return;
            }
            beginPass(chestIndex + 1);
            return;
        }
        
        if (isCleaningUp) {
            log("Â§eFound " + currentPathWaypoints.size() + " cleanup waypoints! Starting cleanup pass...");
            state = State.PATHING_TO_WAYPOINT;
            stateWaitStart = System.currentTimeMillis();
            return;
        }
        
        ChestEntry targetChest = chests.get(currentChestIndex);
        
        // Pre-check inventory
        if (hasRequiredBlock(net.minecraft.client.Minecraft.getInstance(), targetChest.blockId)) {
            state = State.PATHING_TO_WAYPOINT;
            stateWaitStart = System.currentTimeMillis();
            log("Â§eAlready have " + targetChest.blockId + " in inventory, skipping restock.");
        } else {
            currentChestFrontFound = false;
            sendChestGoto(targetChest.x, targetChest.y, targetChest.z);
            state = State.GOING_TO_CHEST;
            stateWaitStart = System.currentTimeMillis();
            log("Â§ePathing to Chest " + (chestIndex + 1) + " for block " + targetChest.blockId);
        }
    }
    
    private static void sendChestGoto(int cx, int cy, int cz) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client.level != null) {
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(cx, cy, cz);
            net.minecraft.world.level.block.state.BlockState state = client.level.getBlockState(pos);
            if (!state.isAir() && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                net.minecraft.core.Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                int targetX = cx + facing.getStepX();
                int targetZ = cz + facing.getStepZ();
                
                int floorY = cy;
                while (floorY > cy - 20 && client.level.getBlockState(new net.minecraft.core.BlockPos(targetX, floorY - 1, targetZ)).isAir()) {
                    floorY--;
                }
                
                sendBaritoneGoto(targetX, floorY, targetZ);
                currentChestFrontFound = true;
                return;
            }
        }
        sendBaritoneGoto(cx, cy, cz);
    }
    
    private static void startBuildingPass(net.minecraft.client.Minecraft client) {
        state = State.PATHING_TO_WAYPOINT;
        stateWaitStart = System.currentTimeMillis();
    }

    public static void tick(net.minecraft.client.Minecraft client) {
        if (client.player != null) MapArtConfigValidator.checkAuth(client);
        if (!running || paused || client.player == null) return;
        
        if (state != lastState) {
            debugLog("[STATE] Transitioned from " + lastState + " to " + state);
            lastState = state;
        }

        double currentMove = client.player.distanceToSqr(lastPosX, lastPosY, lastPosZ);
        if (currentMove < 0.001) {
            idleTicks++;
        } else {
            idleTicks = 0;
        }
        lastPosX = client.player.getX();
        lastPosY = client.player.getY();
        lastPosZ = client.player.getZ();

        switch (state) {
            case IDLE:
                break;
            case GOING_TO_CHEST:
                ChestEntry currentChest = chests.get(currentChestIndex);
                
                boolean isBaritoneActive = (idleTicks < 15);
                
                if (!currentChestFrontFound && client.player.tickCount % 10 == 0) {
                    sendChestGoto(currentChest.x, currentChest.y, currentChest.z);
                }
                
                if (!isBaritoneActive) {
                    double dist = client.player.distanceToSqr(currentChest.x + 0.5, currentChest.y + 0.5, currentChest.z + 0.5);
                    if (dist < 25.0) { // Within 5 blocks (Baritone goal reached)
                        // Face chest and interact
                        double dX = currentChest.x + 0.5 - client.player.getX();
                        double dY = currentChest.y + 0.5 - (client.player.getY() + client.player.getEyeHeight());
                        double dZ = currentChest.z + 0.5 - client.player.getZ();
                        client.player.setYRot((float) (Math.atan2(dZ, dX) * 180.0D / Math.PI) - 90.0F);
                        client.player.setXRot((float) -(Math.atan2(dY, Math.sqrt(dX * dX + dZ * dZ)) * 180.0D / Math.PI));
                        
                        if (System.currentTimeMillis() - stateWaitStart > 300) {
                            net.minecraft.world.phys.BlockHitResult hitResult = new net.minecraft.world.phys.BlockHitResult(
                                new net.minecraft.world.phys.Vec3(currentChest.x + 0.5, currentChest.y + 0.5, currentChest.z + 0.5),
                                net.minecraft.core.Direction.UP,
                                new net.minecraft.core.BlockPos(currentChest.x, currentChest.y, currentChest.z),
                                false
                            );
                            client.gameMode.useItemOn(client.player, net.minecraft.world.InteractionHand.MAIN_HAND, hitResult);
                            
                            state = State.RESTOCKING;
                            restockSlotIndex = 0;
                            stateWaitStart = System.currentTimeMillis();
                        }
                    } else if (System.currentTimeMillis() - stateWaitStart > 15000) {
                        log("Â§cFailed to reach chest! Resuming pathing...");
                        state = State.PATHING_TO_WAYPOINT;
                        stateWaitStart = System.currentTimeMillis();
                    }
                } else {
                    stateWaitStart = System.currentTimeMillis();
                }
                break;
            case RESTOCKING:
                if (client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
                    if (chestOpenTime == 0) chestOpenTime = System.currentTimeMillis();
                    
                    net.minecraft.client.gui.screens.inventory.AbstractContainerScreen screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) client.screen;
                    net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
                    
                    net.minecraft.resources.Identifier targetRl = net.minecraft.resources.Identifier.parse(chests.get(currentChestIndex).blockId);
                    
                    if (System.currentTimeMillis() - chestOpenTime > 400) {
                        int currentlyHave = getInventoryCount(client, targetRl.toString());
                        
                        if (lastRestockInventoryCount == currentlyHave) {
                            failedRestockClickCount++;
                        } else {
                            failedRestockClickCount = 0;
                            lastRestockInventoryCount = currentlyHave;
                        }

                        int targetAmount = currentPassTotalBlocksNeeded > 0 ? currentPassTotalBlocksNeeded : 64 * 36;
                        if (currentlyHave >= targetAmount || !hasInventorySpace(client, targetRl.toString()) || failedRestockClickCount > 5) {
                            if (failedRestockClickCount > 5) {
                                log("Â§eFailed to restock after multiple attempts. Proceeding with what we have...");
                            } else {
                                log("Â§aSmart Restock complete or inventory full! Have " + currentlyHave + "/" + targetAmount + " needed blocks.");
                            }
                            client.player.closeContainer();
                            client.setScreen(null);
                            state = State.PATHING_TO_WAYPOINT;
                            stateWaitStart = System.currentTimeMillis();
                            chestOpenTime = 0;
                            failedRestockClickCount = 0;
                            lastRestockInventoryCount = -1;
                            return;
                        }

                        boolean clicked = false;
                        int slotsChecked = 0;
                        int totalChestSlots = menu.slots.size() - 36;
                        
                        while (slotsChecked < totalChestSlots) {
                            if (restockSlotIndex >= totalChestSlots) restockSlotIndex = 0; // Loop around
                            
                            net.minecraft.world.inventory.Slot slot = menu.slots.get(restockSlotIndex);
                            restockSlotIndex++;
                            slotsChecked++;
                            
                            if (slot.hasItem() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).equals(targetRl)) {
                                debugLog("[CLICK] QUICK_MOVE on slot " + restockSlotIndex + " (chest slot " + (restockSlotIndex - 1) + ")");
                        client.gameMode.handleInventoryMouseClick(menu.containerId, restockSlotIndex - 1, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, client.player);
                                clicked = true;
                                break;
                            }
                        }
                        
                        if (!clicked) {
                            log("Â§eRestock finished (chest empty of this block). Checking if enough blocks obtained...");
                            client.player.closeContainer();
                            client.setScreen(null);
                            chestOpenTime = 0;
                            failedRestockClickCount = 0;
                            lastRestockInventoryCount = -1;
                            
                            if (!hasRequiredBlock(client, targetRl.toString())) {
                                currentChest = chests.get(currentChestIndex);
                                net.minecraft.core.BlockPos abovePos = new net.minecraft.core.BlockPos(currentChest.x, currentChest.y + 1, currentChest.z);
                                net.minecraft.world.level.block.state.BlockState aboveState = client.level.getBlockState(abovePos);
                                
                                if (aboveState.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
                                    if (currentChest.y - currentChest.originalY >= 4) {
                                        log("Â§cChest column empty up to max height (5 chests high)! Skipping to next color.");
                                        beginPass(currentChestIndex + 1);
                                        return;
                                    }
                                    log("Â§eChest is empty! Moving to chest directly above it...");
                                    currentChest.y = currentChest.y + 1;
                                    
                                    currentChestFrontFound = false;
                                    sendChestGoto(currentChest.x, currentChest.y, currentChest.z);
                                    state = State.GOING_TO_CHEST;
                                    stateWaitStart = System.currentTimeMillis();
                                } else {
                                    log("Â§cChest column is empty of " + targetRl.toString() + "! Skipping to next color.");
                                    beginPass(currentChestIndex + 1);
                                }
                            } else {
                                state = State.PATHING_TO_WAYPOINT;
                                stateWaitStart = System.currentTimeMillis();
                            }
                        } else {
                            chestOpenTime = System.currentTimeMillis() - 100; // Next tick will be 400 - 100 = 300ms wait
                        }
                    }
                } else {
                    chestOpenTime = 0;
                    if (System.currentTimeMillis() - stateWaitStart > 4000) {
                        log("Â§cFailed to open chest! Resuming pathing...");
                        state = State.PATHING_TO_WAYPOINT;
                        stateWaitStart = System.currentTimeMillis();
                    }
                }
                break;
            case PATHING_TO_WAYPOINT:
                // Wait 500ms after closing chest to ensure Baritone can take over safely
                if (System.currentTimeMillis() - stateWaitStart > 500) {
                    if (currentPathIndex < currentPathWaypoints.size()) {
                        int[] wp = currentPathWaypoints.get(currentPathIndex);
                        double nDx = wp[0] + 0.5 - client.player.getX();
                        double nDy = wp[1] + 0.03 - client.player.getEyeY();
                        double nDz = wp[2] + 0.5 - client.player.getZ();
                        double nextDist = nDx * nDx + nDy * nDy + nDz * nDz;
                        if (nextDist > 16.0) {
                            sendBaritoneGoto(wp[0], client.player.getBlockY(), wp[2]); 
                        }
                        state = State.WAITING_FOR_BARITONE;
                        lastBaritoneCheckTime = System.currentTimeMillis();
                        log("Â§aResuming pathing pass...");
                    } else {
                        ChestEntry targetChest = chests.get(currentChestIndex);
                        currentChestFrontFound = false;
                        sendChestGoto(targetChest.x, targetChest.y, targetChest.z);
                        state = State.RETURNING_TO_CHEST;
                        log("Â§aFinished color! Returning to chest to deposit leftovers...");
                    }
                }
                break;
            case WAITING_FOR_BARITONE:
                // Check if we reached the current waypoint
                int[] wp = currentPathWaypoints.get(currentPathIndex);
                double distToWp = client.player.distanceToSqr(wp[0], client.player.getY(), wp[2]);
                
                // If we ran out of the required block, we need to restock mid-pass
                if (client.player.tickCount % 20 == 0 && currentChestIndex != -1) { // Check every 20 ticks reliably
                    if ((state == State.PATHING_TO_WAYPOINT || state == State.WAITING_FOR_BARITONE) && !hasRequiredBlock(client, chests.get(currentChestIndex).blockId)) {
                        ChestEntry targetChest = chests.get(currentChestIndex);
                        currentChestFrontFound = false;
                        sendChestGoto(targetChest.x, targetChest.y, targetChest.z);
                        state = State.GOING_TO_CHEST;
                        log("Â§cOut of blocks! Pathing to chest...");
                        return; // Pause pathing
                    }
                }

                double dxWp = wp[0] + 0.5 - client.player.getX();
                double dyWp = wp[1] + 0.03 - client.player.getEyeY();
                double dzWp = wp[2] + 0.5 - client.player.getZ();
                double trueDistSqr = dxWp * dxWp + dyWp * dyWp + dzWp * dzWp;

                if (trueDistSqr <= 16.0) { // Arrived at waypoint (true 3D radius 4.0 blocks)
                    if (isCleaningUp && client.gameMode != null) {
                        net.minecraft.core.BlockPos cleanPos = new net.minecraft.core.BlockPos(wp[0], wp[1], wp[2]);
                        
                        double diffXZ = Math.sqrt(dxWp * dxWp + dzWp * dzWp);
                        float targetYaw = (float) (Math.toDegrees(Math.atan2(dzWp, dxWp)) - 90.0F);
                        float targetPitch = (float) -Math.toDegrees(Math.atan2(dyWp, diffXZ));
                        
                        client.player.setYRot(targetYaw);
                        client.player.setXRot(targetPitch);
                        
                        if (cleanupBreakWaitStart == 0) {
                            cleanupBreakWaitStart = System.currentTimeMillis();
                        }
                        long waitTime = System.currentTimeMillis() - cleanupBreakWaitStart;
                        if (waitTime < 50) {
                            return; // Wait 50ms while looking at the block
                        }
                        
                        net.minecraft.world.level.block.state.BlockState targetState = client.level.getBlockState(cleanPos);
                        if (targetState.isAir()) {
                            cleanupBreakWaitStart = 0;
                            isCurrentlyBreaking = false;
                            cancelBaritone();
                            currentPathIndex++;
                            idleTicks = 0;
                            
                            if (currentPathIndex < currentPathWaypoints.size()) {
                                int[] nextWp = currentPathWaypoints.get(currentPathIndex);
                                double nDx = nextWp[0] + 0.5 - client.player.getX();
                                double nDy = nextWp[1] + 0.03 - client.player.getEyeY();
                                double nDz = nextWp[2] + 0.5 - client.player.getZ();
                                double nextDist = nDx * nDx + nDy * nDy + nDz * nDz;
                                if (nextDist > 16.0) {
                                    sendBaritoneGoto(nextWp[0], client.player.getBlockY(), nextWp[2]);
                                }
                            } else {
                                log("Â§aCleanup pass finished!");
                                isCleaningUp = false;
                                stop();
                            }
                        } else {
                            client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                            if (!isCurrentlyBreaking) {
                                client.gameMode.startDestroyBlock(cleanPos, net.minecraft.core.Direction.UP);
                                isCurrentlyBreaking = true;
                            } else {
                                client.gameMode.continueDestroyBlock(cleanPos, net.minecraft.core.Direction.UP);
                            }
                        }
                        
                        return; // Stay on this tick, do not increment idleTicks
                    }
                    
                    currentPathIndex++;
                    idleTicks = 0;
                    if (currentPathIndex < currentPathWaypoints.size()) {
                        int[] nextWp = currentPathWaypoints.get(currentPathIndex);
                        double nDx = nextWp[0] + 0.5 - client.player.getX();
                        double nDy = nextWp[1] + 0.03 - client.player.getEyeY();
                        double nDz = nextWp[2] + 0.5 - client.player.getZ();
                        double nextDist = nDx * nDx + nDy * nDy + nDz * nDz;
                        if (nextDist > 16.0) {
                            sendBaritoneGoto(nextWp[0], client.player.getBlockY(), nextWp[2]);
                        }
                    } else {
                        // Finished pass! Time to deposit leftovers.
                        if (isCleaningUp) {
                            log("Â§aCleanup pass finished!");
                            isCleaningUp = false;
                            stop();
                        } else {
                            ChestEntry targetChest = chests.get(currentChestIndex);
                            currentChestFrontFound = false;
                            sendChestGoto(targetChest.x, targetChest.y, targetChest.z);
                            state = State.RETURNING_TO_CHEST;
                            log("Â§aFinished color! Returning to chest to deposit leftovers...");
                        }
                    }
                } else {
                    if (idleTicks > 160) { // 8 seconds stuck
                        log("Â§cBaritone got stuck! Skipping to next waypoint...");
                        cancelBaritone();
                        currentPathIndex++;
                        idleTicks = 0;
                        if (currentPathIndex < currentPathWaypoints.size()) {
                            int[] nextWp = currentPathWaypoints.get(currentPathIndex);
                            double nDx = nextWp[0] + 0.5 - client.player.getX();
                            double nDy = nextWp[1] + 0.03 - client.player.getEyeY();
                            double nDz = nextWp[2] + 0.5 - client.player.getZ();
                            double nextDist = nDx * nDx + nDy * nDy + nDz * nDz;
                            if (nextDist > 16.0) {
                                sendBaritoneGoto(nextWp[0], client.player.getBlockY(), nextWp[2]);
                            }
                        } else {
                            if (isCleaningUp) {
                                log("Â§aCleanup pass finished!");
                                isCleaningUp = false;
                                stop();
                            } else {
                                ChestEntry targetChest = chests.get(currentChestIndex);
                                currentChestFrontFound = false;
                                sendChestGoto(targetChest.x, targetChest.y, targetChest.z);
                                state = State.RETURNING_TO_CHEST;
                                log("Â§aFinished color! Returning to chest to deposit leftovers...");
                            }
                        }
                    }
                }
                break;
            case RETURNING_TO_CHEST:
                ChestEntry returnChest = chests.get(currentChestIndex);
                
                boolean isBaritoneActiveRet = (idleTicks < 15);
                
                if (!currentChestFrontFound && client.player.tickCount % 10 == 0) {
                    sendChestGoto(returnChest.x, returnChest.y, returnChest.z);
                }
                
                if (!isBaritoneActiveRet) {
                    double dist = client.player.distanceToSqr(returnChest.x + 0.5, returnChest.y + 0.5, returnChest.z + 0.5);
                    if (dist < 25.0) { // Within 5 blocks
                        // Face chest and interact
                        double dX = returnChest.x + 0.5 - client.player.getX();
                        double dY = returnChest.y + 0.5 - (client.player.getY() + client.player.getEyeHeight());
                        double dZ = returnChest.z + 0.5 - client.player.getZ();
                        client.player.setYRot((float) (Math.atan2(dZ, dX) * 180.0D / Math.PI) - 90.0F);
                        client.player.setXRot((float) -(Math.atan2(dY, Math.sqrt(dX * dX + dZ * dZ)) * 180.0D / Math.PI));
                        
                        if (System.currentTimeMillis() - stateWaitStart > 300) {
                            net.minecraft.world.phys.BlockHitResult hitResult = new net.minecraft.world.phys.BlockHitResult(
                                new net.minecraft.world.phys.Vec3(returnChest.x + 0.5, returnChest.y + 0.5, returnChest.z + 0.5),
                                net.minecraft.core.Direction.UP,
                                new net.minecraft.core.BlockPos(returnChest.x, returnChest.y, returnChest.z),
                                false
                            );
                            client.gameMode.useItemOn(client.player, net.minecraft.world.InteractionHand.MAIN_HAND, hitResult);
                            
                            state = State.DEPOSITING;
                            depositSlotIndex = -1;
                            stateWaitStart = System.currentTimeMillis();
                        }
                    } else if (System.currentTimeMillis() - stateWaitStart > 15000) {
                        log("Â§cFailed to reach chest! Moving on...");
                        beginPass(currentChestIndex + 1);
                    }
                } else {
                    stateWaitStart = System.currentTimeMillis();
                }
                break;
            case DEPOSITING:
                if (client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
                    if (chestOpenTime == 0) chestOpenTime = System.currentTimeMillis();
                    
                    net.minecraft.client.gui.screens.inventory.AbstractContainerScreen screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) client.screen;
                    net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
                    
                    if (System.currentTimeMillis() - chestOpenTime > 400) {
                        if (!hasRequiredBlock(client, chests.get(currentChestIndex).blockId)) {
                            // Empty of this block! Move to next pass!
                            client.player.closeContainer();
                            client.setScreen(null);
                            chestOpenTime = 0;
                            failedDepositClickCount = 0;
                            lastDepositInventoryCount = -1;
                            beginPass(currentChestIndex + 1);
                            return;
                        }
                        
                        boolean clicked = false;
                        net.minecraft.resources.Identifier targetRl = net.minecraft.resources.Identifier.parse(chests.get(currentChestIndex).blockId);
                        int totalChestSlots = menu.slots.size() - 36;
                        
                        int currentlyHave = getInventoryCount(client, targetRl.toString());
                        if (lastDepositInventoryCount == currentlyHave) {
                            if (chestOpenTime != 0) failedDepositClickCount++;
                        } else {
                            failedDepositClickCount = 0;
                            lastDepositInventoryCount = currentlyHave;
                        }
                        
                        boolean chestHasSpace = hasChestSpace(menu, targetRl.toString());
                        if (!chestHasSpace || failedDepositClickCount > 5) {
                            clicked = false;
                        } else {
                            // We need to click items from our inventory INTO the chest
                            for (int i = totalChestSlots; i < menu.slots.size(); i++) {
                                net.minecraft.world.inventory.Slot slot = menu.slots.get(i);
                                if (slot.hasItem() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).equals(targetRl)) {
                                    debugLog("[CLICK] QUICK_MOVE on slot " + i + " from inventory to chest");
                            client.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, client.player);
                                    clicked = true;
                                    break;
                                }
                            }
                        }
                        
                        if (!clicked) {
                            client.player.closeContainer();
                            client.setScreen(null);
                            chestOpenTime = 0;
                            failedDepositClickCount = 0;
                            lastDepositInventoryCount = -1;
                            
                            int remainingToDeposit = getInventoryCount(client, targetRl.toString());
                            if (remainingToDeposit > 0) {
                                returnChest = chests.get(currentChestIndex);
                                net.minecraft.core.BlockPos abovePos = new net.minecraft.core.BlockPos(returnChest.x, returnChest.y + 1, returnChest.z);
                                net.minecraft.world.level.block.state.BlockState aboveState = client.level.getBlockState(abovePos);
                                
                                if (aboveState.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
                                    if (returnChest.y - returnChest.originalY >= 4) {
                                        log("§cChest column full up to max height! Trashing " + remainingToDeposit + " leftover blocks...");
                                        state = State.DROPPING;
                                        return;
                                    }
                                    log("§eChest full! Depositing into chest above...");
                                    returnChest.y = returnChest.y + 1;
                                    
                                    currentChestFrontFound = false;
                                    sendChestGoto(returnChest.x, returnChest.y, returnChest.z);
                                    state = State.RETURNING_TO_CHEST;
                                    stateWaitStart = System.currentTimeMillis();
                                    return;
                                } else {
                                    log("§cChest column full! Trashing " + remainingToDeposit + " leftover blocks...");
                                    state = State.DROPPING;
                                    return;
                                }
                            } else {
                                log("§aDeposit complete!");
                                beginPass(currentChestIndex + 1);
                            }
                        } else {
                            chestOpenTime = System.currentTimeMillis() - 100; // Next tick will be 300ms wait
                        }
                    }
                } else {
                    chestOpenTime = 0;
                    if (System.currentTimeMillis() - stateWaitStart > 4000) {
                        log("§cFailed to open chest to deposit! Moving on...");
                        beginPass(currentChestIndex + 1);
                    }
                }
                break;
            case DROPPING:
                if (client.player.tickCount % 4 != 0) break;
                
                boolean dropped = false;
                net.minecraft.resources.Identifier targetDropRl = net.minecraft.resources.Identifier.parse(chests.get(currentChestIndex).blockId);
                for (int i = 0; i < client.player.inventoryMenu.slots.size(); i++) {
                    net.minecraft.world.inventory.Slot slot = client.player.inventoryMenu.slots.get(i);
                    if (slot.hasItem() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).equals(targetDropRl)) {
                        debugLog("[CLICK] THROW on slot " + i + " to drop item");
                        client.gameMode.handleInventoryMouseClick(client.player.inventoryMenu.containerId, i, 1, net.minecraft.world.inventory.ClickType.THROW, client.player);
                        dropped = true;
                        break;
                    }
                }
                
                if (!dropped) {
                    log("§aFinished dropping items! Moving to next pass...");
                    beginPass(currentChestIndex + 1);
                }
                break;
        }
    }
    
    public static int getInventoryCount(net.minecraft.client.Minecraft client, String blockIdStr) {
        int count = 0;
        net.minecraft.resources.Identifier targetRl = net.minecraft.resources.Identifier.parse(blockIdStr);
        for (int _i = 0; _i < client.player.getInventory().getContainerSize(); _i++) {
            net.minecraft.world.item.ItemStack stack = client.player.getInventory().getItem(_i);
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(targetRl)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean hasInventorySpace(net.minecraft.client.Minecraft client, String blockIdStr) {
        net.minecraft.resources.Identifier targetRl = net.minecraft.resources.Identifier.parse(blockIdStr);
        for (int _i = 0; _i < client.player.getInventory().getContainerSize(); _i++) {
            net.minecraft.world.item.ItemStack stack = client.player.getInventory().getItem(_i);
            if (stack.isEmpty()) return true;
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(targetRl) && stack.getCount() < stack.getMaxStackSize()) return true;
        }
        return false;
    }

    private static boolean hasChestSpace(net.minecraft.world.inventory.AbstractContainerMenu menu, String blockIdStr) {
        net.minecraft.resources.Identifier targetRl = net.minecraft.resources.Identifier.parse(blockIdStr);
        for (int i = 0; i < menu.slots.size() - 36; i++) {
            net.minecraft.world.item.ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) return true;
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(targetRl) && stack.getCount() < stack.getMaxStackSize()) return true;
        }
        return false;
    }

    private static boolean hasRequiredBlock(net.minecraft.client.Minecraft client, String blockIdStr) {
        return getInventoryCount(client, blockIdStr) > 0;
    }

    private static void sendBaritoneGoto(int x, int y, int z) {
        try {
            if (isCleaningUp) {
                int currentY = net.minecraft.client.Minecraft.getInstance().player.getBlockY();
                net.minecraft.client.Minecraft.getInstance().player.connection.sendChat("#goto " + x + " " + currentY + " " + z);
            } else {
                net.minecraft.client.Minecraft.getInstance().player.connection.sendChat("#goto " + x + " " + y + " " + z);
            }
        } catch (Exception e) {
        }
    }

    private static void cancelBaritone() {
        try {
            net.minecraft.client.Minecraft.getInstance().player.connection.sendChat("#stop");
        } catch (Exception e) {
        }
    }

    public static class ChestEntry {
        public String blockId;
        public int x, y, z;
        public transient int originalY = -1;
        
        public ChestEntry() {
            this.originalY = -1;
        }
        
        public ChestEntry(String blockId, int x, int y, int z) {
            this.blockId = blockId;
            this.x = x; this.y = y; this.z = z;
            this.originalY = y;
        }
    }

    private static class ConfigData {
        Integer startX, startZ, pathWidth, mapLength, mapWidth;
        Boolean showHudOverlay;
        String commandPrefix;
        List<ChestEntry> chests;
    }

    // Getters / Setters for GUI
    public static int getStartX() { return startX; }
    public static void setStartX(int x) { startX = x; saveConfig(); }
    public static int getStartZ() { return startZ; }
    public static void setStartZ(int z) { startZ = z; saveConfig(); }
    public static int getPathWidth() { return pathWidth; }
    public static void setPathWidth(int w) { pathWidth = w; saveConfig(); }
    public static int getMapLength() { return mapLength; }
    public static void setMapLength(int l) { mapLength = l; saveConfig(); }
    public static int getMapWidth() { return mapWidth; }
    public static void setMapWidth(int w) { mapWidth = w; saveConfig(); }
    public static String getCommandPrefix() { return commandPrefix; }
    public static void setCommandPrefix(String p) { commandPrefix = p; saveConfig(); }
    public static boolean isShowHudOverlay() { return showHudOverlay; }
    public static void setShowHudOverlay(boolean b) { showHudOverlay = b; saveConfig(); }
    public static int getCurrentChestIndex() { return currentChestIndex; }

    public static boolean isCleaningUp = false;
    
    public static void startCleanup() {
        if (!running) {
            running = true;
            paused = false;
        }
        isCleaningUp = true;
        isCurrentlyBreaking = false;
        currentChestIndex = -1;
        currentPathIndex = 0;
        currentPathWaypoints.clear();
        log("§eScanning map for extra carpets to clean up...");
        beginPass(-1);
    }
}

