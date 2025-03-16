from __future__ import annotations

import inspect
from collections import deque, OrderedDict

import discord.utils
from discord import (
    ButtonStyle,
    ComponentType,
    Embed,
    Interaction,
    InteractionMessage,
    Member,
    SelectOption,
    ui,
    User,
    utils,
)
from discord.ext import menus
from typing import Any, List, Optional, Union, TypeVar, Sequence
from typing_extensions import Self
from .models import ReferenceInfo
from .stringformat import one_reference_string

import itertools



__all__ = (
    'NumberPageModal',
    'T_Pagination',
    'ReferenceSelectPaginator',
)   

T = TypeVar('T')



class T_Pagination(ui.View):
    """Generate Embed page.
    You can use it with normal or ephemeral message.\n
    Unlike F_Pagination, however, There is no "Exit" Button.
    

    Args:
        ui (_type_): _description_
    """
    def __init__(
        self,
        embeds : List[Embed] = None,
        *,
        _author : Union[Member, User] = None
    ):
        super().__init__(timeout=120)
        self._author = _author
        self._embeds = embeds
        self._current_page = 1
        self.message : Optional[InteractionMessage] = None
        
        if embeds:
            self._queue = deque(embeds)
            self._len = len(embeds)
            if self._len == 1:
                self.clear_items()
            else:
                self.first_page.disabled = True
                self.previous.disabled = True
            
            self._initial = embeds[0]
            self._queue[0].set_footer(text=f'Page : {self._current_page} / {self._len}')
            
    async def update_button(self, interaction : Interaction, *, embed : Embed):
        for i in self._queue:
            i.set_footer(text=f"Page : {self._current_page} / {self._len} ")   
        
        if self._current_page == self._len:
            self.next.disabled = True
            self.last_page.disabled = True
        else:
            self.next.disabled = False
            self.last_page.disabled = False
        
        if self._current_page == 1:
            self.first_page.disabled = True
            self.previous.disabled = True
        else:
            self.first_page.disabled = False
            self.previous.disabled = False

        await interaction.response.edit_message(embed=embed, view=self)

    @ui.button(label="|<", style=ButtonStyle.danger, custom_id="Tfirst")
    async def first_page(self, interaction : Interaction, _):
        self._queue.rotate(1)
        embed = self._queue[0]
        self._current_page = 1

        await self.update_button(interaction, embed=embed)

    @ui.button(label="<", style=ButtonStyle.primary, custom_id="Tprevious")
    async def previous(self, interaction : Interaction, _):
        self._queue.rotate(1)
        embed = self._queue[0]
        self._current_page -= 1
        
        await self.update_button(interaction, embed=embed)

    @ui.button(label=">", style=ButtonStyle.primary, custom_id="Tnext")
    async def next(self, interaction : Interaction, _):
        self._queue.rotate(-1)
        embed = self._queue[0]
        self._current_page += 1
        
        await self.update_button(interaction, embed=embed)

    @ui.button(label=">|", style=ButtonStyle.danger, custom_id="Tlast")
    async def last_page(self, interaction : Interaction, _):
        self._queue.rotate(-1)
        embed = self._queue[0]
        self._current_page = self._len
        
        await self.update_button(interaction, embed=embed)

    async def on_timeout(self) -> None:
        if self.message:
            await self.message.edit(view=None)
        
    async def interaction_check(self, interaction: Interaction) -> bool:
        if interaction.user == self._author:
            return True

        embed = Embed(
            title='Oh!',
            description="I am sure you don't have permission to control other people things!",
            color=0xfe7866
        )
        await interaction.response.send_message(embed=embed, ephemeral=True, delete_after=5)
        return False

    @property
    def initial(self) -> Embed:
        return self._initial


class NumberPageModal(ui.Modal, title='Go to page'):
    page = ui.TextInput(label='Page', placeholder='Enter a number', min_length=1)
    
    def __init__(self, max_pages : Optional[int]) -> None:
        super().__init__()
        self.max_pages = max_pages
        if max_pages is not None:
            as_string = str(max_pages)
            self.page.placeholder = f'Enter a number between 1 and {as_string}'
            self.page.max_length = len(as_string)
        
    async def on_submit(self, interaction: Interaction) -> None:
        self.interaction = interaction
        self.stop()    


class ReferenceSelect(ui.Select['ReferenceSelectPaginator']):
    def __init__(
        self,
        references: List[ReferenceInfo],
        row : int = 0
    ) -> None:
        self.default_message = "Click this to view Reference(s)"
        self._references = {reference.car : reference for reference in references} 
        super().__init__(
            placeholder=self.default_message,
            min_values=1,
            max_values=1,
            row=row,
        )

    def adjust_references(self, entries : list[ReferenceInfo]):
        options = [
            SelectOption(
                label=f'[{reference.cls}] {reference.car}',
                description=reference.record,
                value=reference.car
            ) for reference in entries
        ] 
        self.options = options
          
    def selected_reference_format(self):
        value = self.values[0]
        found = self._references[value]
        
        return one_reference_string(found)
    
    async def callback(self, interaction: Interaction) -> None:
        content = self.selected_reference_format()
        await interaction.response.edit_message(content=content, embed=None, view=self.view)
    
    async def interaction_check(self, interaction: Interaction) -> bool:
        return await self.view.interaction_check(interaction)

        
class ClassButton(ui.Button['ReferenceSelectPaginator']):
    def __init__(
        self,
        *,
        row : int,
        label : str,
    ):
        super().__init__(
            style=ButtonStyle.gray,
            label=label,
            row=row,
        )
    
    async def callback(self, interaction: Interaction) -> Any:
        assert self.view is not None
        
        for b in self.view.buttons:
            b.disabled = False
            b.style = ButtonStyle.gray
            
        self.disabled = True
        self.style = ButtonStyle.blurple
        
        self.view.fill_paginator_items()
        await self.view.show_page(interaction, 0)
    
    async def interaction_check(self, interaction: Interaction) -> bool:
        self.view.paging_class = self.label
        return await self.view.interaction_check(interaction)


# Reference from https://github.com/Rapptz/RoboDanny/blob/rewrite/cogs/utils/paginator.py
# by Rapptz
class BasePaginator(ui.View):
    row : int = 2

    def __init__(
        self,
        source : menus.ListPageSource,
        *,
        author : Union[Member, User],
        check_embeds : bool = True,
        compact : bool = False
    ):
        super().__init__(timeout=300)
        self.source = source
        self.current_page: int = 0
        self.check_embeds = check_embeds
        self.message: Optional[InteractionMessage] = None
        self.compact = compact
        self.author = author

    def fill_paginator_items(self) -> None:
        if self.source.is_paginating():
            max_pages = self.source.get_max_pages()
            use_last_and_first = max_pages is not None and max_pages >= 2

            if use_last_and_first:
                self.add_item(self.go_to_first_page)
            self.add_item(self.go_to_previous_page)
            if not self.compact:
                self.add_item(self.go_to_current_page)
            self.add_item(self.go_to_next_page)
            if use_last_and_first:
                self.add_item(self.go_to_last_page)
            if not self.compact:
                self.add_item(self.numbered_page)
        self.add_item(self.stop_pages)

    def _update_labels(self, page_number: int) -> None:
        self.go_to_first_page.disabled = (page_number == 0)

        if self.compact:
            max_pages = self.source.get_max_pages()
            self.go_to_last_page.disabled = max_pages is None or (page_number + 1) >= max_pages
            self.go_to_next_page.disabled = max_pages is not None and (page_number + 1) >= max_pages
            self.go_to_previous_page.disabled = page_number == 0
            return

        self.go_to_current_page.label = str(page_number + 1)
        self.go_to_previous_page.label = str(page_number)
        self.go_to_next_page.label = str(page_number + 2)
        self.go_to_next_page.disabled = False
        self.go_to_previous_page.disabled = False
        self.go_to_first_page.disabled = False

        max_pages = self.source.get_max_pages()
        if max_pages is not None:
            self.go_to_last_page.disabled = (page_number + 1) >= max_pages
            if (page_number + 1) >= max_pages:
                self.go_to_next_page.disabled = True
                self.go_to_next_page.label = '…'
            if page_number == 0:
                self.go_to_previous_page.disabled = True
                self.go_to_previous_page.label = '…'

    async def _get_kwargs_from_page(self, page: int) -> dict[str, Any]:
        value = await utils.maybe_coroutine(self.source.format_page, self, page)
        if isinstance(value, dict):
            return value
        elif isinstance(value, str):
            return {'content': value, 'embed': None}
        elif isinstance(value, Embed):
            return {'embed': value}
        else:
            return {}

    async def adjust_page(self, page_number: int):
        page = await self.source.get_page(page_number)
        self.current_page = page_number
        kwargs = await self._get_kwargs_from_page(page)
        self._update_labels(page_number)
        return page, kwargs

    async def show_page(self, interaction: Interaction, page_number: int) -> None:
        _, kwargs = await self.adjust_page(page_number)
        if kwargs:
            if interaction.response.is_done():
                if self.message:
                    await self.message.edit(**kwargs, view=self)
            else:
                await interaction.response.edit_message(**kwargs, view=self)

    async def show_checked_page(self, interaction: Interaction, page_number: int) -> None:
        max_pages = self.source.get_max_pages()
        try:
            if max_pages is None:
                # If it doesn't give maximum pages, it cannot be checked
                await self.show_page(interaction, page_number)
            elif max_pages > page_number >= 0:
                await self.show_page(interaction, page_number)
        except IndexError:
            # An error happened that can be handled, so ignore it.
            pass

    def _prepare_item(self, per_page : int = 5, start_row : int = 0):
        pass

    async def start(self, interaction: Interaction, *, content: Optional[str] = None) -> None:
        if self.check_embeds and not interaction.channel.permissions_for(
                interaction.guild.me).embed_links:  # type: ignore
            await interaction.followup.send('I do not have embed links permission in this channel.', ephemeral=True)
            return

        self._prepare_item()

        await self.source._prepare_once()
        page = await self.source.get_page(0)
        kwargs = await self._get_kwargs_from_page(page)
        if content:
            kwargs.setdefault('content', content)

        self._update_labels(0)
        if interaction.response.is_done():
            self.message = await interaction.edit_original_response(**kwargs, view=self)
        else:
            message = await interaction.response.send_message(**kwargs, view=self)
            if isinstance(message, InteractionMessage):
                self.message = message
    
    @ui.button(label='≪', style=ButtonStyle.grey, )
    async def go_to_first_page(self, interaction: Interaction, button: ui.Button):
        """go to the first page"""
        await self.show_page(interaction, 0)

    @ui.button(label='Back', style=ButtonStyle.blurple, )
    async def go_to_previous_page(self, interaction: Interaction, button: ui.Button):
        """go to the previous page"""
        await self.show_checked_page(interaction, self.current_page - 1)

    @ui.button(label='Current', style=ButtonStyle.grey, disabled=True, )
    async def go_to_current_page(self, interaction: Interaction, button: ui.Button):
        pass

    @ui.button(label='Next', style=ButtonStyle.blurple)
    async def go_to_next_page(self, interaction: Interaction, button: ui.Button):
        """go to the next page"""
        await self.show_checked_page(interaction, self.current_page + 1)

    @ui.button(label='≫', style=ButtonStyle.grey)
    async def go_to_last_page(self, interaction: Interaction, button: ui.Button):
        """go to the last page"""
        # The call here is safe because it's guarded by skip_if
        await self.show_page(interaction, self.source.get_max_pages() - 1)  # type: ignore

    @ui.button(label='Skip to page...', style=ButtonStyle.grey, )
    async def numbered_page(self, interaction: Interaction, button: ui.Button):
        """lets you type a page number to go to"""
        if self.message is None:
            return

        modal = NumberPageModal(self.source.get_max_pages())
        await interaction.response.send_modal(modal)
        timed_out = await modal.wait()

        if timed_out:
            await interaction.followup.send('Took too long', ephemeral=True)
            return
        elif self.is_finished():
            await modal.interaction.response.send_message('Took too long', ephemeral=True)
            return

        value = str(modal.page.value)
        if not value.isdigit():
            await modal.interaction.response.send_message(f'Expected a number not {value!r}', ephemeral=True)
            return

        value = int(value)
        await self.show_checked_page(modal.interaction, value - 1)
        if not modal.interaction.response.is_done():
            error = modal.page.placeholder.replace('Enter', 'Expected')  # type: ignore # Can't be None
            await modal.interaction.response.send_message(error, ephemeral=True)

    @ui.button(label='Quit', style=ButtonStyle.red, row=row + 1)
    async def stop_pages(self, interaction: Interaction, button: ui.Button):
        """stops the pagination session."""
        await interaction.response.defer()
        await interaction.delete_original_response()
        self.stop()

    async def on_timeout(self) -> None:
        msg = self.message
        if msg:
            if not (msg.embeds or msg.content):
                return

            try:
                await msg.edit(view=None)
            except discord.HTTPException:
                pass


class ReferenceSelectPaginator(BasePaginator):
    def __init__(
        self,
        source : menus.ListPageSource,
        *,
        author : Union[Member, User],
        check_embeds : bool = True,
        compact : bool = False
    ):
        super().__init__(source, author=author, check_embeds=check_embeds, compact=compact)
        self.paging_class = None
        self.buttons = None

        self.other_buttons = [self.numbered_page, self.stop_pages]
        self.paging_buttons = [i for i in self.children if i.type == ComponentType.button and i not in self.other_buttons]
        self.clear_items()

    @classmethod
    def from_list(
        cls,
        iterable : Sequence[ReferenceInfo],
        *,
        author : Union[User, Member],
    ) -> Self:
        return cls(
            source=ReferenceSelectPageSource(iterable),
            author=author
        )

    def _sort(
        self, per_page : int = 25
    ):
        sources = {
            cls : ReferenceSelectPageSource(list(group), per_page=per_page)
            for cls, group in itertools.groupby(self.source.entries, key=lambda i: i.cls)
        }
        priority_order = ['S', 'A', 'B', 'C', 'D']
        
        sorted_sources = OrderedDict()
        for key in priority_order:
            if key in sources:
                sorted_sources[key] = sources[key]
        self.sources = sorted_sources

    async def _get_kwargs_from_page(self, page: int) -> dict[str, Any]:
        value = await utils.maybe_coroutine(self.source.format_page, self, page)
        if isinstance(value, dict):
            return value
        elif isinstance(value, str):
            return {'embed': value, 'content': None}
        elif isinstance(value, Embed):
            return {'embed': value, 'content' : None}
        elif value is None:
            return {'embed': None}
        else:
            return {}

    async def show_page(self, interaction: Interaction, page_number: int):
        page, kwargs = await self.adjust_page(page_number)
        self._select.adjust_references(page)

        if kwargs:
            if interaction.response.is_done():
                if self.message:
                    await self.message.edit(**kwargs, view=self)
            else:
                await interaction.response.edit_message(**kwargs, view=self)

    def _prepare_item(self, per_page : int = 25, start_row : int = 0):
        self._select = select = ReferenceSelect(self.source.entries)

        self._sort(per_page)
        self.config_class_buttons(1)

        select.adjust_references(self.source.entries)
        self.add_item(select)

    def config_class_buttons(self, initial_row : int = 0):
        row = initial_row
        buttons : List[ui.Button] = []
        
        for idx, label in enumerate(self.sources.keys()):
            row = len(buttons) // 5 + initial_row
            button = ClassButton(row=row, label=label)
            
            if idx == 0:
                self.paging_class = label
                button.style = ButtonStyle.blurple
                button.disabled = True  
            buttons.append(button)
        
        self.row = row + 1
        
        if len(buttons) > 1:
            for i in buttons:
                self.add_item(i)
            self.buttons = buttons

        if self.paging_class is not None:
            self.source = self.sources[self.paging_class]
            self.fill_paginator_items()
        else:
            err = inspect.cleandoc(
                """Failed To Bring Search Result. This error may be come from Reference System Failure.
                So please try again later.
                """
            )
            raise RuntimeError(err)
            
    def fill_paginator_items(self) -> None:
        if not self.compact:
            for i in self.paging_buttons:
                i.row = self.row
                
            for i in self.other_buttons:
                i.row = self.row + 1
            
        for i in (self.paging_buttons + self.other_buttons):
            self.remove_item(i)
        
        super().fill_paginator_items()

    async def interaction_check(self, interaction: Interaction) -> bool:
        if interaction.user == self.author:
            self.source = self.sources[self.paging_class]
            return True

        embed = Embed(
            title='Oh!',
            description="I am sure you don't have permission to handle others'!",
            color=0xfe7866
        )
        await interaction.response.send_message(embed=embed, ephemeral=True, delete_after=10)
        return False


class ReferenceSelectPageSource(menus.ListPageSource):
    def __init__(
        self,
        entry : Sequence[ReferenceInfo],
        *,
        per_page : int = 25,
    ):
        if not isinstance(entry, list):
            entry = list(entry)

        super().__init__(entry, per_page=per_page)

    async def format_page(self, menu : ReferenceSelectPaginator, entries : list[ReferenceInfo]):
        return