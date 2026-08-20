import discord
from discord.ext import commands
import requests

BOT_TOKEN = "YOUR_DISCORD_BOT_TOKEN_HERE"

class MapArtBot(discord.Client):
    def __init__(self):
        super().__init__(intents=discord.Intents.default())
        self.tree = discord.app_commands.CommandTree(self)

    async def setup_hook(self):
        await self.tree.sync()

bot = MapArtBot()

@bot.event
async def on_ready():
    print(f'Logged in as {bot.user}')

@bot.tree.command(name="coords", description="Get the last known coordinates of a specific bot account.")
@discord.app_commands.describe(username="The Minecraft username of the bot account")
async def coords(interaction: discord.Interaction, username: str):
    await interaction.response.defer()
    url = f"https://mapart-assistant-default-rtdb.firebaseio.com/coords/{username}.json"
    try:
        response = requests.get(url)
        if response.status_code == 200:
            data = response.json()
            if data and 'x' in data:
                x = data.get('x', 'Unknown')
                y = data.get('y', 'Unknown')
                z = data.get('z', 'Unknown')
                await interaction.followup.send(f"📍 **{username}** is currently at: **X:** {x}, **Y:** {y}, **Z:** {z}")
            else:
                await interaction.followup.send(f"❌ No coordinates found for **{username}** in Firebase yet.")
        else:
            await interaction.followup.send("❌ Failed to fetch from Firebase.")
    except Exception as e:
        await interaction.followup.send(f"❌ Error: {e}")

@bot.tree.command(name="list", description="List all accounts and their last known coordinates.")
async def list_coords(interaction: discord.Interaction):
    await interaction.response.defer()
    url = "https://mapart-assistant-default-rtdb.firebaseio.com/coords.json"
    try:
        response = requests.get(url)
        if response.status_code == 200:
            data = response.json()
            if data and isinstance(data, dict):
                message_lines = ["**📍 MapArt Bots - Last Known Coordinates:**"]
                for username, coords_data in data.items():
                    if isinstance(coords_data, dict) and 'x' in coords_data:
                        x = coords_data.get('x', '?')
                        y = coords_data.get('y', '?')
                        z = coords_data.get('z', '?')
                        message_lines.append(f"• **{username}**: X: {x}, Y: {y}, Z: {z}")
                
                if len(message_lines) > 1:
                    final_msg = "\n".join(message_lines)
                    if len(final_msg) > 2000:
                        final_msg = final_msg[:1996] + "..."
                    await interaction.followup.send(final_msg)
                else:
                    await interaction.followup.send("❌ No valid coordinates found in Firebase yet.")
            else:
                await interaction.followup.send("❌ No coordinates found in Firebase yet. Are any bots running?")
        else:
            await interaction.followup.send("❌ Failed to fetch from Firebase.")
    except Exception as e:
        await interaction.followup.send(f"❌ Error: {e}")

bot.run(BOT_TOKEN)
