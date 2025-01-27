from __future__ import annotations
from discord.ext import tasks
from dotenv import load_dotenv
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from al9oo import Al9oo
    
import discord
import os
import pathlib


load_dotenv()


discord_api_token = os.environ.get('DISCORD_API_TOKEN')
discord_api_token_test = os.environ.get('DISCORD_API_TOKEN_TEST')

refer_db = os.environ.get('REFER_DB')
carhunt_db = os.environ.get('CARHUNT_DB')
clash_db = os.environ.get('CLASH_DB')
elite_db = os.environ.get('ELITE_DB')
weekly_db = os.environ.get('WEEKLY_DB')
car_list_db = os.environ.get('CAR_LIST_DB')

lwh = os.environ.get('LOG_WH')
fwh = os.environ.get('FEEDBACK_WH')
elwh = os.environ.get('ERROR_LOG_WH')

feedback_log_channel = os.environ.get('FEEDBACK_LOG_CHANNEL')
suggestion_channel = os.environ.get('SUGGESTION_CHANNEL')

al9oo_main_announcement = 1160568027377578034
al9oo_urgent_alert = 1161584379571744830
arn_channel = 1188494656170901546

vaild_formats = [
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp"
]

log_data = pathlib.Path(__file__).parent / 'data/al9oo.log'

MB = 1024 * 1024
MAX_GLOBAL_FILE_SIZE = 9.5 * MB


class Config:
    def __init__(self, app : Al9oo) -> None:
        self.app = app
        self.check_logger.start()
        self.logger = app.logger

    @tasks.loop(minutes=1)
    async def check_logger(self):
        size_bytes = os.path.getsize(log_data)
        
        if 9 * MB < size_bytes < MAX_GLOBAL_FILE_SIZE:
            now = discord.utils.utcnow().strftime('%d-%m-%Y %H-%M-%S')
            filename = f'log_{now}.log'
            file = discord.File(log_data, filename)
            
            try:
                await self.wh.send(file=file)
                os.remove(log_data)
                self.logger.info(f'로그 파일 : {filename} 전송 완료 및 기존 로그 삭제 완료')
                
            except discord.HTTPException as e:
                self.logger.exception("FAILED SENDING LOG [%s : (CODE : %s)]", e.__class__.__name__, e.code or e.status)
                return            
            
    @discord.utils.cached_property
    def wh(self):
        hook = discord.Webhook.from_url(lwh, client=self.app)
        return hook
    
    @check_logger.before_loop
    async def _ready(self):
        await self.app.wait_until_ready()

 
def verify_env_vars():
    required_envs = {
        'DISCORD_API_TOKEN': discord_api_token,
        'REFER_DB': refer_db,
        'CARHUNT_DB': carhunt_db,
        'CLASH_DB': clash_db,
        'ELITE_DB': elite_db,
        'WEEKLY_DB': weekly_db,
        'CAR_LIST_DB': car_list_db,
        'LOG_WH': lwh,
        'FEEDBACK_WH': fwh,
        'ERROR_LOG_WH': elwh,
        'FEEDBACK_LOG_CHANNEL': feedback_log_channel,
        'SUGGESTION_CHANNEL': suggestion_channel,
    
    }
    
    MISSING_ENVS = []
    for key, value in required_envs.items():
        if not value:
            MISSING_ENVS.append(key)
            
    if MISSING_ENVS:
        missing_keys = "\n".join(MISSING_ENVS)
        raise ValueError(f"Missing Environment Variable(s)\n{missing_keys}")