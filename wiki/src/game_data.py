from __future__ import annotations

import json
import logging
import re
from enum import Enum
from pathlib import Path
from typing import Callable
from xml.etree import ElementTree

from wiki.src import RESOURCES, ASSETS
from wiki.src.wiki_logs import LOGGER, SimpleWarnType


# ---------------------------------------------------------------------------
# String Management
# ---------------------------------------------------------------------------

class PluralAmount(Enum):
    ZERO = "zero"
    ONE = "one"
    TWO = "two"
    FEW = "few"
    MANY = "many"
    OTHER = "other"


_QUANTITY_SELECTORS = {
    "default": lambda amount:
    PluralAmount.ZERO if amount == 0 else
    PluralAmount.ONE if amount == 1 else
    PluralAmount.OTHER
}
"""
The dictionary of quantity selectors responsible for choosing the appropriate plural quantity depending on the amount presented.

This is used to determine the appropriate plural form for plurals for the game strings. E.g:
<c>
<plurals name="plural_level_ups">
    <item quantity="one">%1$d level up</item>
    <item quantity="other">%1$d level ups</item>
</plurals>
</c>

Dictionary Format:
{
    <locale | "default">: Callable[[int (Amount)], PluralAmount]
}

If locales get added to the wiki, then the appropriate pluralisation rules for these locales should also be used

"""


def _format_android_string(string: str, *args) -> str:
    def replace(match: re.Match[str]) -> str:
        index = int(match.group(1)) - 1
        if index < 0 or index >= len(args):
            raise IndexError(
                f"Format placeholder %{match.group(1)}$ needs argument "
                f"{index + 1}, but only {len(args)} given"
            )
        return str(args[index])

    # Android positional format args, e.g. %1$s, %2$d (type letter is ignored)
    android_format_match = re.compile(r"%(\d+)\$[a-zA-Z]")
    return android_format_match.sub(replace, string).replace("%%", "%")


class String:
    def __init__(self, string: str):
        self.string = string

    def format(self, *args) -> str:
        return _format_android_string(self.string, *args)


class Plural:
    def __init__(self, quantities: dict[PluralAmount, str], quantity_selector: Callable[[int], PluralAmount]):
        self._quantity_selector = quantity_selector
        self._versions = quantities

    def format(self, amount: int, *args) -> str:
        # Fall back to OTHER when the selected quantity is not defined, matching Android,
        # which only uses quantities like "zero" in locales whose grammar requires them.
        quantity = self._quantity_selector(amount)
        version = self._versions.get(quantity) or self._versions[PluralAmount.OTHER]
        return _format_android_string(version, *args)


class GameStrings:
    """The class responsible for loading and accessing game strings for the wiki. Does not perform any validity checks

    Automatically loads the game strings from the given resources"""
    def __init__(self, res_path: Path, locale: str = "default"):
        # Load strings and plurals values
        self._strings = _load_strings(res_path)
        self._plurals = _load_plurals(res_path)
        self.locale = locale

    def has_string(self, string: str) -> bool:
        return string in self._strings["default"]

    def has_plural(self, plural: str) -> bool:
        return plural in self._plurals["default"]

    def get_string(self, string: str, *args, locale: str = "default") -> str:
        # Attempt to resolve locale in order to most relevant to least relevant locales
        order_of_resolution = [locale, self.locale, "default"]
        for next_locale in order_of_resolution:
            if string in self._strings[next_locale]:
                return self._strings[next_locale][string].format(*args)
        raise KeyError(string, "String not found in any locale")

    def get_plural(self, plural: str, amount: int, *args, locale: str = "default") -> str:
        # Attempt to resolve locale in order to most relevant to least relevant locales
        order_of_resolution = [locale, self.locale, "default"]
        for next_locale in order_of_resolution:
            if plural in self._plurals[next_locale]:
                return self._plurals[next_locale][plural].format(amount, *args)
        raise KeyError(plural, "Plural not found in any locale")

    def __getitem__(self, item):
        return self.get_string(item)

    def __contains__(self, item):
        return self.has_string(item) or self.has_plural(item)


def _unescape_android_string(value: str) -> str:
    """Decode Android resource escape sequences in a string value."""
    parts: list[str] = []
    i = 0
    while i < len(value):
        if value[i] == "\\" and i + 1 < len(value):
            nxt = value[i + 1]
            if nxt == "n":
                parts.append("\n")
            elif nxt == "t":
                parts.append("\t")
            elif nxt in {"'", '"', "\\", "@", "?"}:
                parts.append(nxt)
            else:
                parts.append(nxt)
            i += 2
        else:
            parts.append(value[i])
            i += 1
    text = "".join(parts)
    # Quoted Android strings use surrounding "..." to preserve whitespace
    if len(text) >= 2 and text[0] == '"' and text[-1] == '"':
        text = text[1:-1]
    return text


def _load_strings(res_path: Path) -> dict[str, dict[str, String]]:
    strings: dict[str, dict[str, String]] = {}
    for directory in res_path.iterdir():
        # Skip non-locale directories
        if not directory.is_dir() or not directory.name.startswith("values"):
            continue
        # Set locale name
        locale = "default" if directory.name == "values" else directory.name.removeprefix("values-")
        strings.setdefault(locale, {})
        for file in directory.iterdir():
            if not file.is_file() or not file.name.startswith("strings"):
                continue
            root = ElementTree.parse(file).getroot()
            # Add strings
            for node in root.findall("string"):
                name = node.get("name")
                if name is None:
                    logging.warning(f"No name found for string with text `{node.text}`")
                    continue
                strings[locale][name] = String(_unescape_android_string("".join(node.itertext())))

    return strings


def _load_plurals(res_path: Path) -> dict[str, dict[str, Plural]]:
    plurals: dict[str, dict[str, Plural]] = {}
    for directory in res_path.iterdir():
        # Skip non-locale directories
        if not directory.is_dir() or not directory.name.startswith("values"):
            continue
        # Set locale name
        locale = "default" if directory.name == "values" else directory.name.removeprefix("values-")
        quantity_selector = _QUANTITY_SELECTORS[locale if locale in _QUANTITY_SELECTORS else "default"]
        plurals.setdefault(locale, {})
        # Loop through all strings files
        for file in directory.iterdir():
            if not file.is_file() or not file.name.startswith("strings"):
                continue
            root = ElementTree.parse(file).getroot()
            # Add plurals
            for node in root.findall("plurals"):
                name = node.get("name")
                if name is None:
                    logging.warning(f"No name found for plural with text `{node.text}`")
                    continue
                quantities = {}
                for item in node.findall("item"):
                    quantity = item.get("quantity")
                    if quantity is None:
                        logging.warning(f"No quantity found for plural item `{name}`")
                        continue
                    if quantity not in PluralAmount:
                        logging.warning(f"Invalid quantity `{quantity}` in plural item `{name}`")
                        continue
                    quantities[PluralAmount(item.get("quantity"))] = _unescape_android_string("".join(item.itertext()))
                plurals[locale][name] = Plural(quantities, quantity_selector)

    return plurals


STRINGS = GameStrings(RESOURCES)

# ---------------------------------------------------------------------------
# String Loaders
# ---------------------------------------------------------------------------


def title(key: str) -> str:
    """Converts a key into a suitable title text.

    Note: Generally this function should only be used as a backup. Consider using STRINGS.get_string() or the appropriate
    string loader functions (e.g. item_name(), item_desc()) instead to use the in-game strings"""
    return key.replace("_", " ").title()


def item_name(item: str) -> str:
    # Find item title
    item_title = None
    prefixes = ["item", "crop", "spell"]
    for pre in prefixes:
        if STRINGS.has_string(f"{pre}_{item}_name"):
            item_title = STRINGS.get_string(f"{pre}_{item}_name")
            break
    # Default to title() if item is not found
    if item_title is None:
        LOGGER.simple_warn(SimpleWarnType.ITEM_NAME, item)
        return title(item)
    return item_title


def house_item_name(item: str) -> str:
    return _standard_string_resolution(item, "house_item_{}", SimpleWarnType.HOUSE_ITEM_NAME)


def item_desc(item: str) -> str:
    # Find item description
    item_description = None
    prefixes = ["item", "crop", "spell"]
    for pre in prefixes:
        if STRINGS.has_string(f"{pre}_{item}_desc"):
            item_description = STRINGS.get_string(f"{pre}_{item}_desc")
            break
    # Default to title() if item is not found
    if item_description is None:
        LOGGER.simple_warn(SimpleWarnType.ITEM_DESC, item)
        return "—"
    return item_description


def _standard_string_resolution(value: str, key_string: str, warn_type: SimpleWarnType, default_value: str | None = None) -> str:
    value_title = STRINGS.get_string(key_string.format(value)) if key_string.format(value) in STRINGS else None
    if value_title is None:
        LOGGER.simple_warn(warn_type, value)
        return default_value if default_value else title(value)
    return value_title


def skill_name(skill: str) -> str:
    return _standard_string_resolution(skill, "skill_{}_name", SimpleWarnType.SKILL_NAME)


def enemy_name(enemy: str) -> str:
    return _standard_string_resolution(enemy, "enemy_{}_name", SimpleWarnType.ENEMY_NAME)


def guild_name(guild: str) -> str:
    return _standard_string_resolution(guild, "guild_name_{}", SimpleWarnType.GUILD_NAME)


def agility_course_name(course: str) -> str:
    return _standard_string_resolution(course, "agility_{}_name", SimpleWarnType.AGILITY_COURSE_NAME)


def tree_name(tree: str) -> str:
    return _standard_string_resolution(tree, "tree_{}_name", SimpleWarnType.TREE_NAME)


def trade_route_name(trade_route: str) -> str:
    return _standard_string_resolution(trade_route, "trade_route_{}_name", SimpleWarnType.TRADE_ROUTE_NAME)


def trade_route_desc(trade_route: str) -> str:
    return _standard_string_resolution(trade_route, "trade_route_{}_desc", SimpleWarnType.TRADE_ROUTE_DESC, "")


def thieving_npc_name(npc: str) -> str:
    return _standard_string_resolution(npc, "thieving_npc_{}_name", SimpleWarnType.THIEVING_NPC_NAME)


def quest_name(quest: str) -> str:
    return _standard_string_resolution(quest, "quest_{}_name", SimpleWarnType.QUEST_NAME)


def quest_desc(quest: str) -> str:
    return _standard_string_resolution(quest, "quest_{}_objective", SimpleWarnType.QUEST_DESC, "No objective provided")


def town_building_name(building: str) -> str:
    return _standard_string_resolution(building, "town_building_{}_name", SimpleWarnType.TOWN_BUILDING_NAME)


def title_name(skill: str) -> str:
    return _standard_string_resolution(skill, "title_{}_name", SimpleWarnType.TITLE_NAME)


def pet_name(pet: str) -> str:
    return _standard_string_resolution(pet, "pet_{}_name", SimpleWarnType.PET_NAME)


def pet_desc(pet: str) -> str:
    return _standard_string_resolution(pet, "pet_{}_desc", SimpleWarnType.PET_DESC, "")


def merc_name(merc: str) -> str:
    return _standard_string_resolution(merc, "merc_{}_name", SimpleWarnType.MERC_NAME)


def race_name(race: str) -> str:
    return _standard_string_resolution(race, "character_race_{}", SimpleWarnType.RACE_NAME)


def carnival_prize_name(prize: str) -> str:
    return _standard_string_resolution(prize, "carnival_prize_{}_name", SimpleWarnType.CARNIVAL_PRIZE_NAME)


def carnival_prize_desc(prize: str) -> str:
    return _standard_string_resolution(prize, "carnival_prize_{}_desc", SimpleWarnType.CARNIVAL_PRIZE_DESC, "")


def boss_name(boss: str) -> str:
    return _standard_string_resolution(boss, "boss_{}_name", SimpleWarnType.BOSS_NAME)


def boss_desc(boss: str) -> str:
    return _standard_string_resolution(boss, "boss_{}_desc", SimpleWarnType.BOSS_DESC, "")


def dungeon_name(dungeon: str) -> str:
    return _standard_string_resolution(dungeon, "dungeon_{}_name", SimpleWarnType.DUNGEON_NAME)


def dungeon_desc(dungeon: str) -> str:
    return _standard_string_resolution(dungeon, "dungeon_{}_desc", SimpleWarnType.DUNGEON_DESC, "")


def expedition_name(expedition: str) -> str:
    return _standard_string_resolution(expedition, "skilling_dungeon_{}_name", SimpleWarnType.EXPEDITION_NAME)


def expedition_desc(expedition: str) -> str:
    return _standard_string_resolution(expedition, "skilling_dungeon_{}_desc", SimpleWarnType.EXPEDITION_DESC, "")


def seasonal_event_name(event: str) -> str:
    return _standard_string_resolution(event, "seasonal_event_{}_name", SimpleWarnType.SEASONAL_EVENT_NAME)


def seasonal_event_banner(event: str) -> str:
    return _standard_string_resolution(event, "seasonal_event_{}_banner", SimpleWarnType.SEASONAL_EVENT_BANNER)


def seasonal_bounty_name(bounty: str) -> str:
    return _standard_string_resolution(bounty, "seasonal_bounty_{}_name", SimpleWarnType.SEASONAL_BOUNTY_NAME)


def seasonal_bounty_hint(bounty: str) -> str:
    return _standard_string_resolution(bounty, "seasonal_bounty_{}_hint", SimpleWarnType.SEASONAL_BOUNTY_HINT)


def seasonal_minigame_name(minigame: str) -> str:
    return _standard_string_resolution(minigame, "seasonal_minigame_{}_name", SimpleWarnType.SEASONAL_MINIGAME_NAME)


def seasonal_reward_desc(event: str, tokens: int) -> str:
    key = f"seasonal_reward_{event}_{tokens}_desc"
    if key in STRINGS:
        return STRINGS.get_string(key)
    LOGGER.simple_warn(SimpleWarnType.SEASONAL_REWARD_DESC, f"{event}_{tokens}")
    return ""


def seasonal_market_name(offer: str) -> str:
    return _standard_string_resolution(offer, "seasonal_market_{}_name", SimpleWarnType.SEASONAL_MARKET_NAME)


def prestige_effect_desc(effect: str, value: float, unlock: str | None = None) -> str:
    key = f"prestige_effect_{effect}"
    if key not in STRINGS:
        LOGGER.simple_warn(SimpleWarnType.PRESTIGE_EFFECT_DESC, effect)
        return ""
    if effect == "unlock_recipe":
        return STRINGS.get_string(key, item_name(unlock or NotImplemented))
    formatted = int(value) if value == int(value) else value
    return STRINGS.get_string(key, formatted)


# ---------------------------------------------------------------------------
# Miscellaneous
# ---------------------------------------------------------------------------

def load(rel_path: str | Path, prefix_assets: bool = True) -> dict | list:
    """Loads the specified JSON file - defaults to the game data in the assets directory"""
    path = (ASSETS / rel_path) if prefix_assets else Path(rel_path)
    return json.loads(path.read_text(encoding="utf-8"))
