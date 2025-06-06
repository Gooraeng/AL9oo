from __future__ import annotations
from bson import ObjectId
from component.feedback import FeedbackView, FeedbackFailedView
from datetime import datetime, timedelta
from discord import app_commands, Embed, Interaction
from discord.ext import commands, tasks
from discord.utils import format_dt, utcnow
from typing import TYPE_CHECKING
from utils.embed_color import succeed, failed, interaction_with_server
from utils.exception import key
from utils.models import FeedbackToMongo, FeedbackAllInfo, ModalResponse, NumberedObject

if TYPE_CHECKING:
    from al9oo import Al9oo

import asyncio
import discord
import inspect


default_cooldown = 60


def check_owner(interaction : Interaction):
    if interaction.user.id == 303915314062557185:
        return None
    return app_commands.Cooldown(1, default_cooldown)


def response_err_formatter(code : int) -> str:
    if code == 400 :
        return "BAD REQUEST : The request was improperly formatted, or the server couldn't understand it."
    elif code == 401 :
        return "UNAUTHORIZED : The Authorization header was missing or invalid."
    elif code == 403 :
        return "FORBIDDEN : My problem."
    elif code == 404:
        return "NOT FOUND : My problem."
    elif code == 429 :
        return "TOO MANY REQUESTS : I think someone is spamming somehow."
    elif code >= 500:
        return "DISCORD SERVER ERROR : It's not what I can handle with."
    else:
        return str(code)


class Feedback(commands.Cog): 
    
    global default_cooldown
       
    def __init__(self, app : Al9oo) :
        self.app = app
        self.fb = self.app.pool['Feedback']['temp']
        self.cd = commands.CooldownMapping.from_cooldown(2, 300, key)

        if not app.is_dev:
            self.check_feedbacks.start()

    @property
    def logger(self):
        return self.app.logger

    async def feedback_handler(self, response : ModalResponse, *, interaction : Interaction, embed : Embed):
        attempts = 1
        limitation = 3
        response_code = None
        created_at = utcnow()
        
        info = FeedbackToMongo(
            type=response.title,
            detail=response.detail,
            author_info=FeedbackAllInfo.from_interaction(interaction),
            created_at=created_at.timestamp()
        ).model_dump(exclude={'id'})
        
        while attempts <= limitation:
            try:
                await self.fb.insert_one(info)
                
                embed.title = 'Your Feedback was Submitted!'
                embed.description = inspect.cleandoc(
                    f"""
                    Developer will take a look.
                    However, please consider possiblity your opinions could not be approved.
                    We would like to ask for your understanding.
                    
                    Submitted at : {format_dt(created_at, 'F')}
                    """
                )
                embed.colour = succeed
                embed.clear_fields()
                await interaction.edit_original_response(embed=embed)
                break
            
            except discord.HTTPException as e:
                response_code = e.status
                embed.title = 'Your Feedback was rejected!'
                if response_code == 403:
                    break
                
                try_later = 2 * (attempts - 1) + 3
                embed.description = inspect.cleandoc(
                    f"""Sorry, We had Internet problem while processing your feedback.
                    We are trying to send your feedback again in {try_later} seconds."""
                )
                embed.colour = failed
                embed.add_field(name=f'CURRENT ATTEMPTS', value=f'{attempts} / {limitation}')
                await interaction.edit_original_response(embed=embed)
                await asyncio.sleep(try_later)
                attempts += 1                
                
        if not response_code:
            return

        reason = response_err_formatter(response_code)
        instruction = inspect.cleandoc(
            """
            we would like to request you press button below so you copy what you wrote.
            And please consider to post it at suggestion channel in AL9oo Server.
            """
        )
        embed.description = f"I'm sorry. I tried hard to send your feedback, but, ultimately failed.\n[Reason] {reason}\nAlternatively, {instruction}"
        other_view = FeedbackFailedView(response, app=self.app, user_id=interaction.user.id)
        await interaction.edit_original_response(view=other_view, embed=embed)     

    @app_commands.command(
        name='feedback',
        description='Is there any feedback to send to us?',
        extras={"permissions" : ["Embed Links"]}
    )
    @app_commands.guild_only()
    @app_commands.checks.dynamic_cooldown(check_owner, key=lambda i : i.user.id)
    @app_commands.checks.bot_has_permissions(embed_links=True)
    async def feedback(self, interaction : Interaction):   
        await interaction.response.defer(thinking=True, ephemeral=True)

        retry_after = interaction.created_at + timedelta(seconds=default_cooldown)
        view = FeedbackView(interaction.user.id, app=self.app, delete_time=retry_after, cooldownMapping=self.cd)
        
        embed = view.load_warning_embed(True)
        embed.description += '\n### Plus, you are allowed to submit up to 2 feedbacks in 5 minutes.'
        
        cooldown = self.cd.get_bucket(interaction).get_retry_after()
        if cooldown:
            until = interaction.created_at + timedelta(seconds=cooldown)
            until = format_dt(until, 'T')
            embed.add_field(name='WARNING', value=f'You are unable to send feedback until {until}')
        
        view.message = await interaction.edit_original_response(content=None, embed=embed, view=view)
        await view.wait()
        
        if view.is_pressed:
            self.cd.update_rate_limit(interaction)
        
        await self.feedback_handler(view.modal_responses, interaction=interaction, embed=embed)

    @tasks.loop(minutes=1)
    async def check_feedbacks(self):
        data = await self.fb.find({}).sort('created_at' , 1).to_list(length=150)
        if not data or len(data) == 0:
            return
        
        feedbacks = [FeedbackToMongo(**doc) for doc in data]
        embeds_list : list[NumberedObject] = []
        
        for j in feedbacks:
            created_at_formatted = format_dt(datetime.fromtimestamp(j.created_at), 'F')
            embed = Embed(
                title=f"[{j.type}] FEEDBACK",
                description=f'{j.detail}\n\nCreated at : {created_at_formatted}',
                color=interaction_with_server,
            )
            
            num = 1
            author_info = j.author_info

            if author_info.guild:
                embed.add_field(name=f'{num}. Guild', value=f'* name : {author_info.guild.name}\n* id : {author_info.guild.id}')
                num += 1
            if author_info.channel:
                embed.add_field(name=f"{num}. Channel", value=f'* name : {author_info.channel.name}\n* id : {author_info.channel.id}')
                num += 1

            embed.add_field(name=f"{num}. Author", value=f'* name : {author_info.author.name}\n* id : {author_info.author.id}')
            embeds_list.append(NumberedObject(_id=j.id, object=embed))
        
        done : list[ObjectId] = []
        failed : list[ObjectId] = []
            
        per_page = 10 # This must be 10
        for i in range(0, len(embeds_list), per_page):
            temp = embeds_list[i:i+per_page]
            embeds = [embed.object for embed in temp if isinstance(embed.object, Embed)]
            object_ids = [s.id for s in temp]

            try:
                await self.app.fb_hook.send(embeds=embeds)
                done += object_ids

            except Exception as e:
                failed += object_ids
                self.logger.error('피드백 전송 실패 : %s 발생 > 에러 리포트 전송 실시', e.__class__.__name__, exc_info=e)
                if self.app.err_handler:
                    self.logger.error('에러 리포트 전송 실시')
                    await self.app.err_handler.configure_error(e)
                    self.logger.error('에러 리포트 전송 완료')
                continue

            finally:
                await asyncio.sleep(2.5)

        if len(done) > 0:
            self.logger.info("피드백 %s개 전송 완료", len(done))
            result = await self.fb.delete_many({'_id' : {'$in' : done}})
            self.logger.info("전송된 피드백 %s개 삭제 완료", result.deleted_count)

        if len(failed) > 0:
            self.logger.error("피드백 %s개 전송 실패", len(failed))
    
    @check_feedbacks.before_loop
    async def ready(self):
        await self.app.wait_until_ready()


async def setup(app : Al9oo):
    await app.add_cog(Feedback(app))