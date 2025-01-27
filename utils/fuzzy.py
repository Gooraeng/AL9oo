from __future__ import annotations
from dataclasses import asdict, dataclass
from typing import (
    Any,
    Sequence,
    TypeVar,
    Callable,
    Generator,
    Literal,
    Iterable,
    NamedTuple,
    TypedDict,
    Optional,
    overload,
)
from rapidfuzz import fuzz, process
from .models import ReferenceInfo

import re


T = TypeVar('T')


class DetailByField(NamedTuple):
    field : str
    user_search : str
    suggestion : str
    exact : bool


@dataclass
class SearchResult(TypedDict):
    references : list[ReferenceInfo]
    detail : list[DetailByField]


def _get_field_values(choices: Sequence[T], field: str) -> Generator[T, None, None]:
    """Extract unique field values from choices"""
    temp = set(
        item[field] if isinstance(item, dict) else getattr(item, field)
        for item in choices
    )
    return (item for item in temp)
    
    
def ratio(scores : Sequence[T], bounties : Sequence[T]) -> T:
    assert len(scores) == len(bounties) and sum(bounties) == 1
    
    length = len(scores)
    return sum([scores[i] * bounties[i] for i in range(length)])
    

def _oc_scorer(query : str, choice : str, **kwargs) -> float:
    query.lower()
    choice.lower()
    
    is_car = kwargs.pop('car', None)
    
    sort_score = fuzz.token_sort_ratio(query, choice, **kwargs)
    ratio_score = fuzz.token_set_ratio(query, choice, **kwargs)
    simple_score = fuzz.partial_token_ratio(query, choice, **kwargs)
    
    base_score = ratio((ratio_score, sort_score, simple_score), (0.22, 0.15, 0.63))
    
    query_tokens = set(query.split())
    choice_tokens = set(choice.split())
    
    # query에 독립된 'oc' 토큰이 없는데
    if is_car:
        if 'oc' in query_tokens or '(oc)' in query_tokens:
        
        # choice에도 독립된 'oc' 토큰이 있으면 점수 소폭 상승
            if 'oc' in choice_tokens or '(oc)' in choice_tokens:
                base_score += 2.2
        
        # query에 독립된 'oc' 토큰이 없는데
        elif 'oc' in choice_tokens or '(oc)' in choice_tokens:
            # choice에 독립된 'oc' 토큰이 있으면 점수 소폭 하락
            base_score -= 1.0

    if base_score >= 100.0:
        return 100.0
    return base_score


def _string_mapper(
    text : str,
    collection : Iterable[T],
    *,
    key: Optional[Callable[[T], str]] = None,
):
    text = str(text)
    pat = '.*?'.join(map(re.escape, text))
    regex = re.compile(pat, flags=re.IGNORECASE)
    
    for item in collection:
        to_search = key(item) if key else str(item)
        r = regex.search(to_search)
        if r:
            yield len(r.group()), r.start(), item


@overload
def extract_group(
    query : str,
    field : str,
    choices : Sequence[T],
    *,
    scorer : Optional[Callable[[str, str], float]] = ...,
    score_cutoff : float = ...,
    raw : Literal[True]
) -> list[tuple[T, float, int]]:
    ...


@overload
def extract_group(
    query : str,
    field : str,
    choices : Sequence[T],
    *,
    scorer : Optional[Callable[[str, str], float]] = ...,
    score_cutoff : float = ...,
    raw : Literal[False]
) -> list[T]:
    ...


@overload
def extract_group(
    query : str,
    field : str,
    choices : Sequence[T],
    *,
    scorer : Optional[Callable[[str, str], float]] = ...,
    score_cutoff : float = ...,
    raw : bool = ...
) -> list[T]:
    ...


def extract_group(
    query : str,
    field : str,
    choices : Sequence[T],
    *,
    scorer : Optional[Callable[[str, str], float]] = None,
    score_cutoff : float = 60.0,
    raw : bool = False
) -> list[tuple[T, float, int]] | list[T]:
    
    basic_cutoff = score_cutoff / 2
    
    # Get unique field values to search through
    field_values = _get_field_values(choices, field)
    
    if scorer is None:
        scorer = _oc_scorer
    
    if field == 'car':
        kw = {'car' : True}
    else:
        kw = None
    
    # Find best matches using rapidfuzz    
    # (searched, score, index)
    matches = process.extract(
        query,
        field_values,
        scorer=scorer,
        score_cutoff=basic_cutoff,
        processor=lambda s: s.lower(),
        limit=None,
        scorer_kwargs=kw
    )
    
    if raw:
        return matches
    return [m[0] for m in matches]
    

@overload
def find(
    query: str,
    field: str,
    choices: Sequence[T],
    *,
    scorer: Optional[Callable[[str, str], float]] = ...,
    score_cutoff: float = ...,
    raw : Literal[True],
) -> list[tuple[T, float]]:
    ...


@overload
def find(
    query: str,
    field: str,
    choices: Sequence[T],
    *,
    scorer: Optional[Callable[[str, str], float]] = ...,
    score_cutoff: float = ...,
    raw : Literal[False],
) -> list[T]:
    ...


@overload
def find(
    query: str,
    field: str,
    choices: Sequence[T],
    *,
    scorer: Optional[Callable[[str, str], float]] = ...,
    score_cutoff: float = ...,
    raw : bool = ...,
) -> list[T]:
    ...


def find(
    query: str,
    field: str,
    choices: Sequence[T],
    *,
    scorer: Optional[Callable[[str, str], float]] = None,
    score_cutoff: float = 0.0,
    raw : bool = False,
) -> list[tuple[T, float]] | list[T]:
    """
    Find best matching reference based on field value.
    
    Args:
        query: Search query
        field: Field name to search in
        choices: Sequence of reference objects
        scorer: Scoring function to use
        score_cutoff: Minimum score to consider a match
        raw : returns (best match, score) if true or returns best match
        
    Returns:
        Tuple of (best match, score) or list of best matches
        or list of matches
    """
    
    matches = extract_group(query, field, choices, scorer=scorer, score_cutoff=score_cutoff, raw=True)
    
    if not matches:
        raise RuntimeError(f'Can not found `{field}- {query}`')
        
    found, score, _ = matches[0]
    if score < score_cutoff:
        suggestion = '\n'.join([f'`{match[0]}`' for match in matches[:3]])
        raise RuntimeError(f'Can not found `[{field}] {query}`.\n\nDid you mean...\n{suggestion}')
    
    # Find the first choice that matches the best field value
    if raw:
        return [
            (choice, score) for choice in choices 
            if (choice[field] if isinstance(choice, dict) else getattr(choice, field)) == found
        ]
    
    return [
        choice for choice in choices 
        if (choice[field] if isinstance(choice, dict) else getattr(choice, field)) == found
    ]


@overload
def find_one(
    query: str,
    field: str,
    choices: Sequence[T],
    *,
    scorer: Optional[Callable[[str, str], float]] = ...,
    score_cutoff: float = ...,
    raw: Literal[True],
) -> tuple[T, float]:
    ...


@overload
def find_one(
    query: str,
    field: str,
    choices: Sequence[T],
    *,
    scorer: Optional[Callable[[str, str], float]] = ...,
    score_cutoff: float = ...,
    raw: Literal[False],
) -> T:
    ...


@overload
def find_one(
    query: str,
    field: str,
    choices: Sequence[T],
    *,
    scorer: Optional[Callable[[str, str], float]] = ...,
    score_cutoff: float = ...,
    raw: bool = ...,
) -> T:
    ...
    

def find_one(
    query: str,
    field: str,
    choices: Sequence[T],
    *,
    scorer: Optional[Callable[[str, str], float]] = None,
    score_cutoff: float = 80.0,
    raw: bool = False,
) -> tuple[T, float] | T:
    """
    Find best matching one based on field value.

    Args:
        query: Search query
        field: Field name to search in
        choices: Sequence of objects
        scorer: Scoring function to use
        score_cutoff: Minimum score to consider a match
        raw : returns (best match, score) if true or returns best match

    Returns:
        Tuple of (best match, score) or best match
    """
    result = max(
        find(query, field, choices, scorer=scorer, score_cutoff=score_cutoff, raw=True),
        key=lambda t: t[1]
    )
    
    if raw:
        return result
    return result[0]


def search_references(
    fields: dict[str, Any],
    choices: list[ReferenceInfo],
    *,
    scorer: Optional[Callable[[str, str], float]] = None,
    score_cutoff: float = 60.0,
) -> SearchResult:
    """
    Search through references using multiple fields.
    
    Args:
        fields: inherited from dataclass of field names and values to search
        choices: Sequence of reference objects
        scorer: Scoring function to use
        score_cutoff: Minimum score to consider a match
        
    Returns:
        Tuple of (best match or None, best score, matched field)
    """
    
    # Validate field names
    valid_fields = {"track", "car", "cls", "lap_time", "link"}
    input_fields_keys = fields.keys()
    if invalid_fields := set(input_fields_keys) - valid_fields:
        raise ValueError(f"Invalid fields: {invalid_fields}. Valid fields are: {valid_fields}")
    
    # Special validation for cls field
    if "cls" in fields:
        valid_classes = ("S", "A", "B", "C", "D")
        cls = fields['cls']
        if cls and cls.upper() not in valid_classes:
            valid_classes = '\n'.join(valid_classes)
            raise ValueError(f"Invalid car class: {cls}. Valid classes : {valid_classes}")
        del cls

    search_detail : list[DetailByField] = []
    
    for field, user_search in fields.items():
        
        temp : list[tuple[ReferenceInfo, float]] = find(
            query=user_search,
            field=field,
            choices=choices,
            scorer=scorer,
            score_cutoff=score_cutoff,
            raw=True,
        )

        reference, score = temp[0]
        suggestion = asdict(reference)[field]
        exact = True if score >= 100.0 else False

        detail = DetailByField(
            field=field,
            user_search=user_search,
            suggestion=suggestion,
            exact=exact
        )
        search_detail.append(detail)
        
        choices : list[ReferenceInfo] = [choice for choice, score in temp]
        
    return SearchResult(
        references=choices,
        detail=search_detail
    )
    