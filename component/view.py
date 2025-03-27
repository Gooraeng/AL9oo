from __future__ import annotations
from discord import ui
from typing import Any, Optional, TYPE_CHECKING
from utils.embed_color import failed

if TYPE_CHECKING:
    from al9oo import Al9oo

import asyncio
import discord
import inspect


__all__ = (
    'BaseView',
    'InviteLinkView',
    'DeleteMessage'
)


class BaseView(ui.View):
    """Base View Class inherits from ``discord.discord.ui.View``
    When there's error in here, automatically reported to AL9oo Management Team.
    
    ## Parameters
    * bot : ``class`` AL9oo
    * timeout : ``class`` Optional[float] = 180
    
    ## Methods
    * on_error : An unknown error occurs, then reports to management team.
    """
    def __init__(self, *, app : Al9oo, timeout: Optional[float] = 180):
        super().__init__(timeout=timeout)
        self.app = app
    
    async def on_error(self, interaction: discord.Interaction, error: Exception, item: ui.Item[Any]) -> None:
        embed = discord.Embed(
            title='Unknown Error Occurred',
            description=inspect.cleandoc(
                """
                We are sorry, this error was automatically reported to AL9oo Management Team.
                But, We don't investigate about Discord's fault. We appreciate to your patience.
                """
            ),
            color=failed
        )
        await self.app.err_handler.send_error(interaction, embed=embed, error=error, do_report=True)


class InviteLinkView(ui.View):
    def __init__(self, label : str, url : Optional[str] = None):
        if not url:
            # AL9oo Support Server Invitation Link
            url = "https://discord.gg/8dpAFYXk8s"
        super().__init__(timeout=None)
        self.add_item(discord.ui.Button(label=label, url=url))


class DeleteMessage(ui.Button):
    def __init__(
        self,
        label: str = "Delete Message",
        style: Optional[discord.ButtonStyle] = discord.ButtonStyle.blurple,
        custom_id : Optional[str] = None,
    ):
        super().__init__(
            label=label,
            style=style,
            custom_id=custom_id,
            emoji=discord.PartialEmoji(name="❌")
        )

    async def retry_msg_delete(self, interaction: discord.Interaction):
        await asyncio.sleep(5)
        try:
            if interaction.response.is_done():
                await interaction.delete_original_response()
            else:
                await interaction.message.delete()
        except (discord.Forbidden, discord.NotFound, discord.HTTPException):
            return

    async def callback(self, interaction: discord.Interaction):
        try:
            await interaction.response.defer()
        except discord.HTTPException:
            await asyncio.sleep(3)

        try:
            await interaction.delete_original_response()
        except (discord.Forbidden, discord.NotFound):
            return
        except discord.HTTPException:
            await asyncio.create_task(self.retry_msg_delete(interaction))