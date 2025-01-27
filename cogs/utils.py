from __future__ import annotations
from discord import app_commands, Embed, Interaction, InteractionMessage, Permissions, ui
from discord.ext import commands
from discord.utils import format_dt, oauth_url
from typing import List, Optional, TYPE_CHECKING, Union
from component.view import InviteLinkView
from utils import CommandDetails
from utils.embed_color import al9oo_point, etc
from utils.models import CommandUsageModel

if TYPE_CHECKING:
    from al9oo import Al9oo

import asyncio
import time


exclude_cmds = (
    # these are only for bot admin.
    'admin',
    'patch',
    'profile',
    # this cmds don't need to be viewed.
    'help',
)


def check_interaction(interaction : Interaction):
    if interaction.user.id == 303915314062557185:
        return None
    return app_commands.Cooldown(1, 30.0)  


class CommandsTutorialSelect(ui.Select['TutorialView']):
    def __init__(self, cmds : List[CommandUsageModel]):
        super().__init__(
            placeholder="Choose Command!",
            min_values=1,
            max_values=1,
        )
        
        for cmd in cmds:
            self.add_option(
                label=cmd.name[1:],
                description=cmd.description
            )
        self.cmds = cmds

    async def callback(self, interaction : Interaction):
        cmd_name = self.values[0]
        target = None
        for cmd in self.cmds:
            if cmd_name == cmd.name[1:]:
                target = cmd
                break
        
        assert target is not None
        num = 1

        details = target.details
        if details.guild_only:
            guild = "\n### This command doesn't work at DM."
            colour = 0xA064FF
        else:
            guild = ''
            colour = 0x62D980
            
        embed = Embed(
            title=target.name,
            description=f'{target.description}{guild}',
            colour=colour
        )
        
        if params := details.parameters:
            if details.sequence:
                sequence = f"\n**(2) Search Sequence**\n* {details.sequence}"
            else:
                sequence = ""
            embed.add_field(
                name=f"{num}. Parameter",
                value=f"**(1) List**\n{params}{sequence}",
                inline=False
            )
            num += 1
        
        embed.add_field(
            name=f"{num}. How to use",
            value=f"{details.howto}",
            inline=False
        )
        num += 1
        
        if details.permissions:
            embed.add_field(
                name=f"{num}. I require Permission(s)",
                value=f"{details.permissions}",
            )
            num += 1
    
        if details.default_permission:
            embed.add_field(
                name=f"{num}. You need Permission(s)",
                value=f"{details.default_permission}"
            )
        
        embed.set_footer(text="Try again please if any command doesn't run properly.")
        
        try:
            await interaction.response.edit_message(content=None, embed=embed)
        
        except:
            await interaction.response.defer(thinking=True, ephemeral=True)
            await asyncio.sleep(5)
            await interaction.followup.send(content=None, embed=embed, ephemeral=True)


class TutorialView(ui.View):
    def __init__(
        self,
        cmds : List[CommandUsageModel]
    ):
        super().__init__(timeout=180)
        self.message : Optional[InteractionMessage] = None
        self.add_item(CommandsTutorialSelect(cmds))

    async def on_timeout(self) -> None:
        self.clear_items()
        self.stop()
        if self.message:
            try:
                await self.message.delete()
            except:
                pass
            

class Utils(commands.Cog):
    def __init__(self, app : Al9oo) -> None:
        self.app = app

    @property
    def logger(self):
        return self.app.logger

    async def configure_help(self, interaction : Interaction) :
        def command_solver(cmd : Union[app_commands.Command, app_commands.Group]):
            try:
                if (binded_cmd := cmd.binding.app_command.default_permissions) is not None:
                    if isinstance(cmd, app_commands.Group):
                        command_solver(cmd)
                    elif isinstance(cmd, app_commands.Command):
                        channel_perm = interaction.channel.permissions_for(interaction.user)
                        return (binded_cmd.value & channel_perm.value) == binded_cmd.value
                return True

            except:
                return True

        async def add_description(cmd : app_commands.Command):
            if cmd.parameters:
                temp = []
                for parameter in cmd.parameters:
                    if parameter.required:
                        is_req = ' (Essential) '
                    else:
                        is_req = ' '
                    temp.append(f"*{is_req}`{parameter.display_name}` - {parameter.description}")
                parameters = '\n'.join(s for s in temp)
                del temp
            else:
                parameters = None
                
            permission = cmd.extras.get("permissions")
            if permission:
                permission = "* " + "\n* ".join(permission)

            sequence = cmd.extras.get("sequence")

            howto = cmd.extras.get("howto")
            if howto is not None:
                howto = "\n".join(howto)
            else:
                howto = "* You would know how to do it!"
            
            if cmd.default_permissions and cmd.default_permissions & interaction.channel.permissions_for(interaction.user) == cmd.default_permissions:
                default_permission = cmd.default_permissions.__qualname__.replace('_', ' ').replace('guild', 'server').title()
            else:
                default_permission = None

            detail = {
                "default_permission" : default_permission,
                "guild_only" : cmd.guild_only,
                "parameters": parameters,
                "permissions": permission,
                "sequence": sequence,
                "howto": howto,
            }

            command_usage = {
                "name" : f"/{cmd.qualified_name}",
                "description" : cmd.description,
                "details" : CommandDetails(**detail)
            }

            result.append(CommandUsageModel(**command_usage))
            
        result : List[CommandUsageModel] = []
        
        async with (asyncio.TaskGroup() as tg):
            for excluded, cog in self.app.cogs.items():
                if excluded.lower() in exclude_cmds:
                    continue

                for cmd in cog.walk_app_commands():
                    if cmd.name.lower() in exclude_cmds:
                        continue

                    if command_solver(cmd):
                        tg.create_task(add_description(cmd))

        return sorted(result, key=lambda c: c.name)

    @app_commands.command(name="help", description="You can know how to enjoy AL9oo!")
    @app_commands.guild_only()
    async def help(self, interaction : Interaction):
        try:
            await interaction.response.defer(thinking=True, ephemeral=True)

            helps = await self.configure_help(interaction)
            view = TutorialView(helps)
            embed = Embed(
                title="Please Choose command",
                description="This helps you use commands well!",
                colour=etc
            )

            view.message = await interaction.edit_original_response(content=None, view=view, embed=embed)
            await view.wait()
            
        except Exception as e:
            await interaction.edit_original_response(content=e)
            self.logger.error(e, exc_info=e)

    @app_commands.command(description='Send you redeem link!')
    @app_commands.checks.dynamic_cooldown(check_interaction, key=lambda i : i.user.id)
    async def redeem(self, interaction : Interaction):       
        await interaction.response.send_message('https://www.gameloft.com/redeem/asphalt-legends-unite')
    
    @app_commands.command(description='Run this If you interested in me!')
    @app_commands.checks.dynamic_cooldown(check_interaction, key=lambda i : i.user.id)
    async def invite(self, interaction : Interaction):
        await interaction.response.defer(thinking=True)

        perm = Permissions.none()
        perm.read_message_history = True
        perm.read_messages = True
        perm.send_messages = True
        perm.send_messages_in_threads = True
        perm.manage_messages = True
        perm.manage_webhooks = True
        perm.attach_files = True
        perm.embed_links = True
        
        reasons = {
            "Message" : {
                "Embed links" : "Basically Lots of messages I sent are consist of Embeds!",
                "Send messages / Send messages in threads" : "I can't just respond while being muted..",
                "Manage messages / Read message history" : "To Manage my messages!",
                "Read Messages" : "This might be better for flexibility.",
                "Attach Files" : "To send thumbnail of ALU patch notes"
            },
            "Follow" : {
                "Manage Webhooks" : "I bring Announcement messages with webhook from AL9oo Support Server. I handle webhooks only that I made!"
            }
        }
        view = InviteLinkView(
            label='Click Me!',
            url=oauth_url(self.app.bot_app_info.id, permissions=perm)
        )
        embed = Embed(
            title='Permission Usage',
            description='',
            colour=al9oo_point
        )
        for i, (k, v) in enumerate(reasons.items(), start=1):
            embed.description += f"### {i}. {k}\n"
            sub_value = (
                f"**({j})** ***{sub_k}***\n * {sub_v}"
                for j, (sub_k, sub_v) in enumerate(v.items(), start=1)
            )
            embed.description += '\n'.join(sub_value) + '\n'
        await interaction.followup.send(view=view, embed=embed)
        
    @app_commands.command(description='Get support server link!')
    @app_commands.checks.dynamic_cooldown(check_interaction, key=lambda i : i.user.id)
    async def support(self, interaction : Interaction):
        embed = Embed(
            title="AL9oo Support Server", 
            color=al9oo_point,
            url='https://discord.gg/8dpAFYXk8s'
        )
        view = InviteLinkView(url="AL9oo Support Server")
        await interaction.response.send_message(embed=embed, view=view)

    @app_commands.command(description="Are you interested in my source?")
    @app_commands.checks.dynamic_cooldown(check_interaction, key=lambda i : i.user.id)
    async def source(self, interaction : Interaction):
        url = "https://github.com/Gooraeng/AL9oo_"
        view = InviteLinkView(label='MY SOURCE', url=url)
        await interaction.response.send_message(url, view=view)

    @app_commands.command(description="am 'I'?")
    @app_commands.checks.dynamic_cooldown(check_interaction, key=lambda i: i.user.id)
    async def who(self, interaction : Interaction):                
        try:
            await interaction.response.defer(thinking=True)

            uptime_float = (interaction.created_at - self.app.uptime).total_seconds()
            uptime = int(uptime_float)
            days = uptime // 86400
            hours = (uptime % 86400) // 3600
            minutes = (uptime % 3600) // 60
            seconds = uptime % 60

            uptime_msg = f"{days}D {hours}h {minutes}m {seconds}s"
            birthday = format_dt(self.app.user.created_at, 'F')
            info = self.app.bot_app_info

            embed = Embed(
                title=interaction.client.user.global_name,
                description=info.description, color=al9oo_point
            )
            embed.add_field(name="1. Birthday", value=birthday, inline=False)
            embed.add_field(name='2. Uptime', value=uptime_msg, inline=True)
            embed.add_field(name="3. Representive Color", value="#59E298, #8BB8E1", inline=True)
            view = InviteLinkView(label="Go to Server", url='https://discord.gg/8dpAFYXk8s')
            await interaction.followup.send(embed=embed, view=view)
        
        except:     
            await asyncio.sleep(5)
            await interaction.edit_original_response(content="Oops! There was / were error(s)! Please try again later.")

    @app_commands.command(name='ping', description='Pong! Checks Ping with Server.')
    @app_commands.checks.dynamic_cooldown(check_interaction, key=lambda i : i.user.id)
    async def ping(self, interaction : Interaction):
        start = time.perf_counter()
       
        await interaction.response.defer(thinking=True, ephemeral=True)

        description = []
        if interaction.guild is not None:
            shard_id = interaction.guild.shard_id
            shard = self.app.get_shard(shard_id)
            latency = shard.latency
            description.append(f"* Ping You are in : {int(latency*1000)}ms")

        description.append(f"* Average Ping : {int(interaction.client.latency*1000)}ms")

        end = time.perf_counter()
        duration = int((end - start) * 1000)
        description.append(f"* Actual Ping : {duration}ms")

        embed = Embed(
            title='Pong!',
            description="\n".join(description),
            colour=al9oo_point
        )
        embed.set_footer(text="This would not be accurate.")
        await interaction.followup.send(embed=embed, ephemeral=True)


async def setup(app : Al9oo):
    await app.add_cog(Utils(app))
    