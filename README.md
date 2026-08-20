# 🗺️ MapArt Assistant

A fully automated, client-side Fabric mod designed to build massive MapArts in Minecraft with zero manual effort. It integrates seamlessly with **Litematica** to read schematics and uses **Baritone** to handle pathfinding and block placement.

---

## ✨ Features
* **Fully Automated Building**: Reads your Litematica schematic and places blocks automatically.
* **Smart Chest Assignment**: Tell the bot which chests contain which blocks, and it will fetch them automatically when it runs out!
* **Auto-Cleanup**: Drops unnecessary blocks when transitioning between different parts of the build.
* **Baritone Integration**: Advanced pathfinding ensures the bot never gets stuck and can navigate complex terrain.

---

## 📜 Commands

The mod uses a # prefix by default (configurable in the mod settings).

| Command | Description |
|---|---|
| #start | Starts the MapArt building process from the beginning. |
| #stop | Completely stops the bot, halts Baritone, and resets its state. |
| #pause | Pauses the bot in its current place (useful if you need to intervene). |
| #resume | Resumes the bot from where it was paused. |
| #clean | Scans the area and cleans up any accidentally misplaced carpets or blocks. |
| #setchest <block_id> | Assigns the chest you are currently looking at to the specified block (e.g., #setchest minecraft:white_carpet). |
| #clearchests | Clears all of your saved chest assignments. |

---

## 📦 How the Chest Assignment System Works

For large MapArts, you can't fit all the required blocks in your inventory. The **Chest Assignment System** solves this by letting the bot restock itself!

![Chest Layout](assets/chest_layout.png)

**How to use it:**
1. Place a chest and fill it with the block you need (e.g., White Carpet). *Tip: You can stack them in giant columns like the image above!*
2. Look directly at the bottom chest.
3. Type #setchest minecraft:white_carpet. 
4. Repeat this for all the different blocks your MapArt requires.

**What the bot does:**
When the bot is building and runs out of White Carpet, it will instantly stop, pathfind back to the exact chest you assigned, grab as much White Carpet as it can hold, and then walk right back to where it left off to continue building. If a chest gets full or empty, it dynamically adjusts and looks for the next chest in the column!

---

## 🔍 How the Bot Searches & Builds

![MapArt Progress](assets/mapart_progress.png)

1. **Schematic Scanning**: The bot hooks directly into Litematica to scan the loaded schematic within the boundaries you set in the mod options.
2. **Color by Color**: As seen above, it sweeps across the canvas placing blocks color by color to minimize switching inventory slots.
3. **Inventory Matching**: It actively compares the blocks required by the schematic against the blocks currently in its inventory. 
4. **Pathfinding execution**: Once it determines the next block it needs to place, it calculates the most efficient route and commands Baritone to walk there and execute the placement.

---

## ⚙️ Requirements
* Fabric Mod Loader
* Fabric API
* Litematica
* Baritone
