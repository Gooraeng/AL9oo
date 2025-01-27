from __future__ import annotations
from discord import app_commands, Interaction
from typing import Iterable, TypeVar


T = TypeVar('T')


def ratio(scores : Iterable[T], bounties : Iterable[T]) -> T:
    assert len(scores) == len(bounties) and sum(bounties) == 1
    
    length = len(scores)
    return sum([scores[i] * bounties[i] for i in range(length)])


def is_me():
    def pred(interaction : Interaction):
        return interaction.user.id == 303915314062557185
    return app_commands.check(pred)