from __future__ import annotations
from al9oo import Al9oo
from config import refer_db, verify_env_vars
from logging.handlers import RotatingFileHandler
from motor.motor_asyncio import AsyncIOMotorClient
from utils.exception import LoadingFailedMongoDrive

import click
import discord
import contextlib
import pathlib
import logging


current_file = pathlib.Path(__file__).resolve()


def check_data_folder():
    """Create data folder when it doesn't exist."""
    data_folder = current_file.parent / 'data'
    if not data_folder.exists():
        data_folder.mkdir()


async def create_pool():
    attempt = 1
    client = AsyncIOMotorClient(refer_db)
    
    while attempt <= 5:
        try:
            response = await client.admin.command('ping')
            if response.get("ok") == 1:
                return client
        except Exception:
            attempt += 1
    raise LoadingFailedMongoDrive


async def main(is_dev : bool = False):
    log = logging.getLogger(__name__)
    
    try:
        log.info("LOADING ENVIRONMENT VARIABLES")
        verify_env_vars()
    except ValueError as e:
        log.exception('Every Environment Variable is not set. Exiting.', exc_info=e)
        raise KeyboardInterrupt
    
    try:
        log.info('Configuring MongoDB Driver')
        pool = await create_pool()
    except LoadingFailedMongoDrive:
        log.exception('Could not set up Mongo. Exiting.')
        return
    
    bot = Al9oo(is_dev) 
    bot.pool = pool

    try:
        
        await bot.start()  
        
    except KeyboardInterrupt:
        log.info("KeyboardInterput Detected, shutting down.")
    
    finally:
        log.info("Closing Resources")
        await bot.close()
    
    log.info('AL9oo shutdown complete.')


class RemoveNoise(logging.Filter):
    def __init__(self):
        super().__init__(name='discord.state')

    def filter(self, record: logging.LogRecord) -> bool:
        if record.levelname == 'WARNING' and 'referencing an unknown' in record.msg:
            return False
        return True


class CustomRotatingFileHandler(RotatingFileHandler):
    def __init__(
        self,
        filename: str | pathlib.Path[str],
        mode: str = "a",
        max_bytes: int = 0,
        backup_count: int = 0,
        encoding: str | None = None,
        delay: bool = False,
        errors: str | None = None
    ) -> None:
        super().__init__(filename, mode, max_bytes, backup_count, encoding, delay, errors)
        self.___log = logging.getLogger(__name__)
    
    def doRollover(self) -> None:
        self.___log.warning('로그 파일 경신')
        super().doRollover()  
        

@contextlib.contextmanager
def setup_logging():
    log = logging.getLogger()

    try:
        discord.utils.setup_logging()
        # __enter__
        max_bytes = 9 * 1024 * 1024  # 25 MiB
        logging.getLogger('discord').setLevel(logging.INFO)
        logging.getLogger('discord.http').setLevel(logging.WARNING)
        logging.getLogger('discord.state').addFilter(RemoveNoise())

        log.setLevel(logging.INFO)

        log_data = pathlib.Path(__file__).parent / 'data/al9oo.log'
        handler = CustomRotatingFileHandler(filename=log_data, encoding='utf-8', mode='w', max_bytes=max_bytes, backup_count=5)
        dt_fmt = '%Y-%m-%d %H:%M:%S'
        fmt = logging.Formatter('[{asctime}] [{levelname:<7}] {name:<23}: {message}', dt_fmt, style='{')
        handler.setFormatter(fmt)
        log.addHandler(handler)

        yield

    finally:
        # __exit__
        handlers = log.handlers[:]
        for hdlr in handlers:
            hdlr.close()
            log.removeHandler(hdlr)


@click.command()
@click.option('-normal', is_flag=True, default=False, help='', required=False)
def algoo(normal):
    click.echo('Configuring..')
    check_data_folder()
    with setup_logging():
        if not normal:
            debug = False
            click.echo('Starting with Normal mode.')
        else:
            debug = True
            click.echo('Starting with dev mode.')

        import asyncio

        # if platform.system() == 'Windows':
        #     import winloop
        #     to_run = winloop
        #     click.echo('Windows Detected. Running with Winloop.')

        # elif platform.system() == 'Linux':
        #     import asyncio
        #     to_run = asyncio
        #     click.echo('Linux Detected. Running with Asyncio.')

        try:
            asyncio.run(main(debug), debug=debug)
        
        except Exception:
            raise


if __name__ == "__main__":
    try:
        algoo()
    except SystemExit:
        pass