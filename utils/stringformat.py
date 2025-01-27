from __future__ import annotations
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from .models import ReferenceInfo

import inspect


def one_reference_string(reference: ReferenceInfo) -> str:
    return inspect.cleandoc(
        f"""
        ```
        Car    : {reference.car}
        Track  : {reference.track}
        Record : {reference.record}
        ```{reference.link}
        """
    )