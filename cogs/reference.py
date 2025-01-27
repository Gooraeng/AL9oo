from __future__ import annotations
from datetime import time, timezone
from discord import app_commands, Interaction, Embed
from discord.ext import commands, tasks
from discord.utils import format_dt, utcnow, _human_join
from typing import Any, List, Literal, TYPE_CHECKING
from utils.embed_color import failed
from utils.fuzzy import DetailByField, search_references, extract_group
from utils.models import ReferenceInfo
from utils.paginator import ReferenceSelectPaginator
from utils.referenceManager import *
from utils.stringformat import one_reference_string

if TYPE_CHECKING:
    from al9oo import Al9oo

import asyncio


class Reference(commands.Cog):
    renew_time = [
        time(hour=h, minute=m, second=5, tzinfo=timezone.utc) 
        for m in [5, 20, 35, 50] for h in range(24)
    ]
    
    def __init__(self, app : Al9oo) -> None:
        self.app = app
        if not self.app.is_dev:
            self.auto_renew_references.start()
            self.get_car_list.start()
    
    async def cog_load(self) -> None:
        await self.renew_references()
        await self.renew_car_list()

    @property
    def logger(self):
        return self.app.logger

    @property
    def session(self):
        return self.app.session
    
    @tasks.loop(time=renew_time)
    async def auto_renew_references(self) -> None:
        await self.renew_references()
    
    @tasks.loop(time=time(hour=0, minute=0, second=0, tzinfo=timezone.utc))
    async def get_car_list(self):
        await self.renew_car_list()

    @auto_renew_references.before_loop
    @get_car_list.before_loop
    async def _ready(self):
        await self.app.wait_until_ready()
        
    async def renew_car_list(self):
        lists = get_car_list()
        name, cars = await asyncio.create_task(lists.get_list())
        if not cars or len(cars) == 0:
            self.logger.warning(f"Failed Renewing Car List.")
            return
        setattr(self, name, cars)
        self.logger.info("Car List Renewed Successfully.")

    async def renew_references(self):
        """Renews References."""
        renew_info: dict[str, Any] = {}

        async with asyncio.TaskGroup() as tg:
            tmp = [
                tg.create_task(reference.get_list())
                for reference in get_references()
            ]
            
        for result in tmp:
            name, reference = await result
            count = f'{name}.count'
            applied = f"{name}.applied"

            if count in renew_info and applied in renew_info:
                continue

            if not reference or len(reference) == 0:
                continue

            renew_info[count] = len(reference)
            renew_info[applied] = format_dt(utcnow(), style="R")

            setattr(self, f'{name}_reference', reference)
            self.logger.info(f"{name} reference renewed.")

        await self.app._db_renewed.find_one_and_update({}, {"$set" : renew_info})
        
    async def search_failed_handler(self, interaction : Interaction, error : RuntimeError):
        embed = Embed(colour=failed)
        embed.description = error.__str__()
        
        if interaction.response.is_done():
            await interaction.followup.send(embed=embed)
        else:
            await interaction.response.send_message(embed=embed)

    def not_exact_details(self, details : list[DetailByField]):
        content = ''
        if details:
            temp = _human_join(
                [
                    f"`{detail.suggestion}` instead of `{detail.user_search}` from **{detail.field.upper()}**"
                    for detail in details if not detail.exact
                ],
                delimiter=',\n',
                final='and'
            )
            if temp:
                content += 'Found ' + temp
        return content
    
    async def send_reference(self, interaction : Interaction, references : list[ReferenceInfo], **kwargs):
        try:
            fields = {k: v for k, v in kwargs.items() if v is not None}
            result = search_references(fields, references, score_cutoff=60)
            
            references = result['references']
            details = result['detail']
            content = self.not_exact_details(details)
            
            if len(references) > 1:    
                view = ReferenceSelectPaginator.from_list(
                    references,
                    author=interaction.user
                )
                await view.start(interaction, content=content)
                
            elif len(references) == 1:
                await self.send_one(interaction, reference=references[0], content=content)
                
            else:
                raise RuntimeError('Sorry, There was something wrong.')
            
        except (RuntimeError, ValueError) as e:
            await self.search_failed_handler(interaction, e)
    
    async def send_one(
        self,
        interaction : Interaction,
        *,
        reference : ReferenceInfo,
        content : str = "",
    ):        
        content += "\n" + one_reference_string(reference)
        
        if interaction.response.is_done():
            await interaction.followup.send(content)
        else:
            await interaction.response.send_message(content)
        
    async def ch_autocompletion(
        self,
        interaction: Interaction,
        current: str
    ) -> List[app_commands.Choice[str]]:

        if not current:
            result: list[str] = [a.car for a in self.carhunt_reference]
        else:
            result: list[str] = extract_group(current, 'car', self.carhunt_reference)

        return [
           app_commands.Choice(name=choice, value=choice) for choice in result
           if current.lower().replace(" ", "") in choice.lower().replace(" ", "")
       ][:25]

    @app_commands.command(
        description='You can watch Car hunt Riot videos!',
        extras={
            "permissions" : ["Embed Links"]
        }
    )
    @app_commands.describe(car='What\'s the name of Car?')
    @app_commands.guild_only()
    @app_commands.checks.bot_has_permissions(embed_links=True)
    @app_commands.autocomplete(car=ch_autocompletion)
    async def carhunt(self, interaction : Interaction, car : str):
        await interaction.response.defer(thinking=True)
        await self.send_reference(interaction, self.carhunt_reference, car=car) # reference params

    async def area_autocompletion(
        self,
        interaciton: Interaction,
        current: str,
    ) -> List[app_commands.Choice[str]]:

        if not current:
            result: list[str] = list(set([a.track for a in self.clash_reference]))
        else:
            result: list[str] = extract_group(current, 'track', self.clash_reference)

        return [
           app_commands.Choice(name=choice, value=choice)
           for choice in result
           if current.lower().replace(' ', '') in choice.lower().replace(' ', '')
       ][:25]

    @app_commands.command(
        description='Check Club Clash references!',
        extras={
            "permissions" : ["Embed Links"],
        }
    )
    @app_commands.describe(track='Search track.')
    @app_commands.guild_only()
    @app_commands.checks.bot_has_permissions(embed_links=True)
    @app_commands.autocomplete(track=area_autocompletion)
    async def clash(
        self,
        interaction: Interaction,
        track : str,
    ):  
        await interaction.response.defer(thinking=True)
        await self.send_reference(interaction, self.clash_reference, track=track)
    
    @app_commands.command(
        description='Let you know elite cup reference!',
        extras={
            "permissions" : ["Embed Links"],
        }
    )
    @app_commands.describe(cls='What class of Car?')
    @app_commands.rename(cls='class')
    @app_commands.guild_only()
    @app_commands.checks.bot_has_permissions(embed_links=True)
    async def elite(
        self,
        interaction: Interaction,
        cls : Literal["S", "A", "B", "C"], 
    ):  
        await interaction.response.defer(thinking=True)
        await self.send_reference(interaction, self.elite_reference, cls=cls)

    async def track_autocompletion(
        self,
        interaction: Interaction,
        current: str,
    ) -> List[app_commands.Choice[str]]:

        if not current:
            matches: list[str] = list(set([a.track for a in self.weekly_reference]))
        else:
            matches: list[str] = extract_group(current, 'track', self.weekly_reference)

        return [
           app_commands.Choice(name=choice, value=choice)
           for choice in matches
           if current.lower().replace(' ', '') in choice.lower().replace(' ', '')
       ][:25]

    @app_commands.command(
        description='Let you know Weekly Competition references!',
        extras= {
            "permissions" : ["Embed Links"],
        }
    )
    @app_commands.describe(track='Search Track.')
    @app_commands.guild_only()
    @app_commands.checks.bot_has_permissions(embed_links=True)
    @app_commands.autocomplete(track=track_autocompletion)
    async def weekly(
        self,
        interaction: Interaction,
        track : str,
    ):      
        await interaction.response.defer(thinking=True)
        await self.send_reference(interaction, self.weekly_reference, track=track)


async def setup(app : Al9oo):
    await app.add_cog(Reference(app))