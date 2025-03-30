from __future__ import annotations
from aiohttp import ClientSession
from cogs import error
from config import *
from collections import defaultdict
from datetime import datetime
from discord import (
    ForumChannel,
    Status,
)
from discord.ext import commands
from discord.utils import utcnow
from motor.motor_asyncio import AsyncIOMotorClient
from typing import Any, Optional
from utils.models import CommandExecutableGuildChannel, WebhookMessagableChannel

import asyncio
import discord
import logging
import pathlib


__all__ = (
    "Al9oo",
)

initial_extensions = (
    'cogs.admin',
    'cogs.error',
    'cogs.event',
    'cogs.feedback',
    'cogs.reference',
    'cogs.utils',
)
current_path = pathlib.Path(__file__).resolve()


    
class Al9oo(commands.AutoShardedBot):
    user : discord.ClientUser
    pool : AsyncIOMotorClient
    
    def __init__(self, is_dev : bool = False, /):
        self.is_dev = is_dev
        
        intents = discord.Intents.none()
        intents.guilds = True
        super().__init__(
            command_prefix=None,
            pm_help=None,
            heartbeat_timeout=60.0,
            chunk_guild_at_startup=False,
            intents=intents,
            status=Status.online
        )
        
        self.resumes : defaultdict[int, list[datetime]] = defaultdict(list)
        self.identifies: defaultdict[int, list[datetime]] = defaultdict(list)
        self._auto_info : dict[str, Any] = {}
        self._failed_auto_info : list[str] = []
        self._feedback_channel : Optional[CommandExecutableGuildChannel] = None
        self._suggestion_channel : Optional[ForumChannel] = None
        self.is_closing = False
        self.logger = logging.getLogger(__name__)

    def load_mongo_drivers(self):
        self.pnote = self.pool["patchnote"]
        # AL9oo 패치노트 로그
        self._pnlog = self.pnote["log"]
        # AL9oo DB renew 시간 갱신
        self._db_renewed = self.pnote["db_renewed"]
        self.fixing = self.pnote["fixing"]

    async def set_feedback_channel(self):
        try:
            self._feedback_channel = self.get_channel(int(feedback_log_channel)) or await self.fetch_channel(int(feedback_log_channel))
        except:
            pass
        
    async def setup_hook(self) -> None:        
        self.load_mongo_drivers()
        self.session = ClientSession()
        self.bot_app_info = await self.application_info()     
        self.owner_id = self.bot_app_info.owner.id
        
        await self.set_feedback_channel()
        self.config = Config(self)
        
        for extension in initial_extensions:
            try:
                await self.load_extension(extension)
                self.logger.info('%s 로딩 완료', extension)
                
            except Exception as e:
                self.logger.error('%s 로딩 실패\n', extension, exc_info=e)

    # Bot events
    async def on_ready(self):
        try:
            if self.is_ready():
                if not hasattr(self, 'uptime'):
                    self.uptime = utcnow()
                self.logger.info("[Ready] Tag :  %s // (ID : %s)", self.user, self.user.id)
        
        except Exception as e:
            self.logger.error(e)
            self.logger.error("Force exit")
            await self.close()

    async def on_error(self, event_method: str, /, *args: Any, **kwargs: Any) -> None:
        return await super().on_error(event_method, *args, **kwargs)
    
    async def start(self):
        if not self.is_dev:
            token = discord_api_token
        else:
            token = discord_api_token_test
        await super().start(token)
    
    async def close(self):
        if self.is_closing:
            self.logger.info("Shutting down alreay in progress. Exiting.")
            return
        
        self.is_closing = True
        self.logger.info("Shutting down...")
        
        await super().close()
        
        if bot_tasks := [task for task in asyncio.all_tasks() if task is not asyncio.current_task()]:
            self.logger.debug(f'Canceling {len(bot_tasks)} outstanding tasks.')
            
            for task in bot_tasks:
                task.cancel()
            
            await asyncio.gather(*bot_tasks, return_exceptions=True)
            self.logger.debug('All tasks cancelled.')
        
        try:
            self.logger.info("Closing MongoDB connection")
            self.pool.close()
            await self.session.close()
        except Exception as e:
            self.logger.critical(f"Error during database disconnection: {e}")
        
        self.logger.info('Shutdown Complete.')
    
    @discord.utils.cached_property
    def fb_hook(self):
        hook = discord.Webhook.from_url(fwh, session=self.session)
        return hook
    
    @discord.utils.cached_property
    def el_hook(self):
        hook = discord.Webhook.from_url(elwh, client=self)
        return hook
    
    @property
    def owner(self) -> discord.User:
        return self.bot_app_info.owner
    
    @property
    def feedback_channel(self) -> WebhookMessagableChannel:
        return self._feedback_channel
    
    @property
    def err_handler(self) -> Optional[error.AppCommandErrorHandler]:
        return self.get_cog('AppCommandErrorHandler')