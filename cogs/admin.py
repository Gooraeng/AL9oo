from __future__ import annotations
from discord import (
    app_commands, 
    Embed,
    Interaction, 
    Object,
)
from discord.ext import commands
from typing import Any, Dict, Literal, Optional, TYPE_CHECKING
from utils.embed_color import al9oo_point
from utils.paginator import T_Pagination

if TYPE_CHECKING:
    from al9oo import Al9oo

import asyncio
import psutil  


def buffed_cached() -> Dict[str, Any]:
    meminfo = {}
    try:
        with open('/proc/meminfo', 'r') as f:
            for line in f:
                parts = line.split()
                if parts[0] in ('Buffers:', 'Cached:', 'SwapCached:'):
                    meminfo[parts[0].rstrip(':')] = int(parts[1])
    except:
        pass
    return meminfo


def system_status() -> Embed:
    cpu = psutil.cpu_percent(interval= 1)
    mem = psutil.virtual_memory()
    total_mem = mem.total / (1024**3)
    free_mem = mem.free / (1024**3)
    avail_mem = mem.available / (1024**3)
    
    bc_info = buffed_cached()
    buffer = bc_info.get('Buffers', 0)
    cache = bc_info.get('Cached', 0)
    # bc = (buffer + cache) / (1024**3)
    
    rows = [
        ['* CPU USAGE', f'{cpu:.0f}%'],
        ['* TOTAL', f'{total_mem:.2f} GB'],
        ['* FREE', f'{free_mem:.2f} GB'],
        ['* AVAILABLE', f'{avail_mem:.2f} GB']
    ]

    description = '```ansi\n{:<14} | {:<8}\n============================'.format('CATEGORY', 'VALUE')

    for row in rows:
        description += f'\n{row[0]:<14} | [0;34m{row[1]:<15}[0m'
    description = f"{description}```"
    
    embed = Embed(title='System Info', description=description, color=al9oo_point)
    return embed


class Admin(commands.Cog):
    def __init__(self, app : Al9oo) -> None:
        self.app = app
        self.pnlog = self.app._pnlog 

    @app_commands.command(name='sysinfo', description='...')
    @app_commands.guild_only()
    async def sysinfo(self, interaction : Interaction):
        await interaction.response.defer(thinking=True, ephemeral=True)
        try:
            embed = system_status()
            await interaction.followup.send(embed=embed, ephemeral=True)
        
        except Exception as e:
            await asyncio.sleep(5)
            await interaction.followup.send(f"Error : {e}", ephemeral= True)
                
    @app_commands.command(name='sync-command', description='...')
    @app_commands.describe(where='select')
    @app_commands.guild_only()
    async def sync_commands(self, interaction : Interaction, where : Optional[Literal["~"]] = None):
        await interaction.response.defer(thinking=True, ephemeral=True)
        if where is None:
            synced = await self.app.tree.sync()
            await interaction.followup.send(f"{len(synced)} command synced globally")
            return
    
        synced = await self.app.tree.sync(guild=interaction.guild)  
        await interaction.followup.send(f"{len(synced)} command synced here")


async def setup(app : Al9oo):
    await app.add_cog(Admin(app), guild=Object(id=1205958300873527466))