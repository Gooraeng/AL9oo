from __future__ import annotations
from datetime import time
from discord.ext import commands, tasks
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from al9oo import Al9oo

import discord


class EventHandler(commands.Cog):
    guild_log = [time(hour=h, minute=0, second=0) for h in range(24)]

    def __init__(self, app : Al9oo):
        self.app = app
        self.current_joined_guild.start()

    @property
    def logger(self):
        return self.app.logger

    @tasks.loop(count=1)
    async def startup_current_joined_guild(self):
        await self.current_status()

    @tasks.loop(time=guild_log)
    async def current_joined_guild(self):
        await self.current_status()

    async def current_status(self):
        count = len(self.app.guilds)
        activity = discord.CustomActivity(name=f"Joining {count} servers")

        await self.app.change_presence(
            status=discord.Status.online,
            activity=activity
        )
        self.logger.info(f"Currently Joining {count} guild(s).")

    @current_joined_guild.before_loop
    @startup_current_joined_guild.before_loop
    async def _ready(self):
        await self.app.wait_until_ready()

    @commands.Cog.listener()
    async def on_shard_ready(self, shard_id: int):
        self.logger.info("[Shard Ready] Shard ID : %s", shard_id)

    @commands.Cog.listener()
    async def on_shard_resumed(self, shard_id: int):
        self.logger.info("[Shard Resumed] Shard ID : %s", shard_id)

    @commands.Cog.listener()
    async def on_guild_join(self, guild: discord.Guild):
        self.logger.info("[Guild Join] Guild ID : %s", guild.id)

    @commands.Cog.listener()
    async def on_guild_remove(self, guild: discord.Guild):
        self.logger.info("[Guild Remove] Guild ID : %s", guild.id)


async def setup(app : Al9oo):
    await app.add_cog(EventHandler(app))