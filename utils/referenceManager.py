from __future__ import annotations
from aiohttp import ClientSession
from config import *
from pathlib import Path
from typing import Any, List, Optional
from .models import CarInfo, ReferenceInfo
from .exception import DownloadFailed

import aiofiles
import csv
import logging


__all__ = (
    "CsvDataBaseManager",
    "get_references",
    "get_car_list"
)


current_path = Path(__file__).resolve()
db_folder = current_path.parent.parent / 'data'

log = logging.getLogger(__name__)


dbs = [
    ("carhunt", carhunt_db),
    ("elite", elite_db),
    ("gauntlet", gauntlet_db),
    ("weekly", weekly_db)
]


class ManagerBase:
    def __init__(
        self,
        url : str,
    ):
        self._url = url

    async def _download(self) -> Any:
        raise NotImplementedError

    async def _process(self):
        raise NotImplementedError


class FileManager(ManagerBase):
    def __init__(
        self,
        url: str,
        session: Optional[ClientSession] = None,
    ):
        super().__init__(url)
        if session is None:
            self._session_was_none = True
            self._session = ClientSession()
        else:
            self._session_was_none = False
            self._session = session

    async def _save(self, newline : Optional[str] = None):
        raise NotImplementedError
        
    def _resolve_path(self):
        raise NotImplementedError


class CsvDataBaseManager(FileManager):
    def __init__(
        self,
        name: str,
        url: str,
        session: Optional[ClientSession] = None,
    ) -> None:
        super().__init__(url, session)
        self._name = name
        self._count = 0
        self._path = self._resolve_path()
        
    async def get_list(self) -> Any:
        raise NotImplementedError

    def _resolve_path(self):
        return db_folder / f'{self._name}_db.csv'

    async def _download(self) -> str:
        try:
            async with self._session.get(self._url) as response:
                response.raise_for_status()
                return await response.text() 
        
        except Exception as e:
            raise DownloadFailed(self.name) from e
        
        finally:
            if self._session_was_none:
                await self._session.close()

    async def _save(self, newline : Optional[str] = None):
        result = await self._download()
        f = await aiofiles.open(self._path, "w+", encoding='utf-8', newline=newline)
        await f.write(result)
        return f
    
    async def _process(self):
        try:
            f = await self._save('')
            await f.seek(0)
            reader = csv.reader(await f.readlines())
            await f.close()
            
            header = next(reader)
            data = list(reader)
            header_indexes = {h : header.index(h) for h in header}

            self._count = len(data)
            return header, data, header_indexes
        
        except Exception as e:
            raise e
        
    @property
    def name(self):
        return self._name

    @property
    def count(self):
        return self._count

    @count.setter
    def count(self, value : int):
        if not isinstance(value, int):
            raise TypeError("This Value Must Be Int.")
        self._count = value


class ReferenceManager(CsvDataBaseManager):
    def __init__(
        self,
        name : str,
        url : str,
        session: Optional[ClientSession] = None,
    ) -> None:
        super().__init__(name, url, session)
    
    async def get_list(self) -> tuple[str, List[ReferenceInfo]]:
        try:
            headers, data, header_indexes = await self._process()
            info_headers = {
                'CAR_NAME': 'car',
                'AREA': 'track',
                'LAP_TIME': 'record',
                'LINK': 'link',
                'CLASS': 'cls'
            }

            lists = [
                ReferenceInfo(**{info_headers[header]: data[i][header_indexes[header]] for header in headers})
                for i in range(self.count)
            ]
            
            log.info(f'Found "{len(lists)}" {self._name} reference(s).')
            return self.name, lists

        except Exception as e:
            log.warning(f"Failed AUTOMATICALLY renew DBs due to '{e}'.", exc_info=e)
            return self.name, []


class CarListManager(CsvDataBaseManager):
    def init(
        self,
        session : ClientSession,
        name : str,
        url : str
    ) -> None:
        super().__init__(name, url, session)

    async def get_list(self) -> tuple[str, List[CarInfo]]:
        try:
            headers, data, header_indexes = await self._process()

            info_headers = {
                "CLASS" : "cls",
                "CAR NAME" : "car",
            }

            lists = [
                CarInfo(**{info_headers[header] : data[i][header_indexes[header]] for header in headers})
                for i in range(self._count)
            ]
            log.info(f'Found "{len(lists)}" car list(s).')
            return self.name, lists

        except Exception as e:
            log.warning(f"Failed AUTOMATICALLY renew DBs due to '{e}'.", exc_info=e)
            return self.name, []


def _transform_to_spreadsheet_url(id : str) -> str:
    key = refer_key
    return f"https://docs.google.com/spreadsheets/d/{key}/export?format=csv&id={key}&gid={id}"


def get_references() :
    return [
        ReferenceManager(name, _transform_to_spreadsheet_url(id))
        for name, id in dbs
    ]


def get_car_list() :
    return CarListManager('car_list', car_list_db)
