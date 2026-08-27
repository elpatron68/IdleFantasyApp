import logging
from enum import Enum


class SimpleWarnType(Enum):
    # Page errors
    GUILD_QUEST_TYPE = "guild_quest_type"
    BUILDING_BONUS = "building_bonus"
    MISSING_BUILDING_DESCRIPTION = "missing_building_description"
    MISSING_PRIZE_TYPE = "missing_prize_type"
    # Game string loaders
    SKILL_NAME = "skill_name"
    ITEM_NAME = "item_name"
    ITEM_DESC = "item_desc"
    HOUSE_ITEM_NAME = "house_item_name"
    ENEMY_NAME = "enemy_name"
    GUILD_NAME = "guild_name"
    AGILITY_COURSE_NAME = "agility_course_name"
    TRADE_ROUTE_NAME = "trade_route_name"
    TRADE_ROUTE_DESC = "trade_route_desc"
    THIEVING_NPC_NAME = "thieving_npc_name"
    QUEST_NAME = "quest_name"
    QUEST_DESC = "quest_desc"
    TOWN_BUILDING_NAME = "town_building_name"
    TITLE_NAME = "title_name"
    PET_NAME = "pet_name"
    PET_DESC = "pet_desc"
    BOSS_NAME = "boss_name"
    BOSS_DESC = "boss_desc"
    DUNGEON_NAME = "dungeon_name"
    DUNGEON_DESC = "dungeon_desc"
    EXPEDITION_NAME = "expedition_name"
    EXPEDITION_DESC = "expedition_desc"
    SEASONAL_EVENT_NAME = "seasonal_event_name"
    SEASONAL_EVENT_BANNER = "seasonal_event_banner"
    SEASONAL_BOUNTY_NAME = "seasonal_bounty_name"
    SEASONAL_BOUNTY_HINT = "seasonal_bounty_hint"
    SEASONAL_MINIGAME_NAME = "seasonal_minigame_name"
    SEASONAL_REWARD_DESC = "seasonal_reward_desc"
    SEASONAL_MARKET_NAME = "seasonal_market_name"
    PRESTIGE_EFFECT_DESC = "prestige_effect_desc"


class WikiLogger:
    def __init__(self):
        def default_warning(warning_type: str, expected_file: str = "strings.xml", missing_element: str = "name"):
            return set(), f"The {warning_type} `{{}}` did not have a defined {missing_element} in {expected_file}"

        self._warned_values = {
            # Page errors
            SimpleWarnType.GUILD_QUEST_TYPE: (set(), "The guild quest `{}` was not appropriately managed when generating the guilds page"),
            SimpleWarnType.BUILDING_BONUS: (set(), "The bonus `{}` was not specially formatted on the buildings page"),
            SimpleWarnType.MISSING_BUILDING_DESCRIPTION: (set(), "The building `{}` is missing a description in the buildings page"),
            SimpleWarnType.MISSING_PRIZE_TYPE: (set(), "The prize type `{}` was not specially formatted on the carnival page"),
            # Game string loaders
            SimpleWarnType.SKILL_NAME: default_warning("skill", "strings.xml"),
            SimpleWarnType.ITEM_NAME: default_warning("item", "strings_items.xml"),
            SimpleWarnType.ITEM_DESC: default_warning("item", "strings_items.xml", "description"),
            SimpleWarnType.ENEMY_NAME: default_warning("enemy", "strings_enemies.xml"),
            SimpleWarnType.GUILD_NAME: default_warning("guild"),
            SimpleWarnType.AGILITY_COURSE_NAME: default_warning("agility course"),
            SimpleWarnType.TRADE_ROUTE_NAME: default_warning("trade route"),
            SimpleWarnType.TRADE_ROUTE_DESC: default_warning("trade route", missing_element="description"),
            SimpleWarnType.THIEVING_NPC_NAME: default_warning("thieving NPC"),
            SimpleWarnType.QUEST_NAME: default_warning("quest", "the game strings"),
            SimpleWarnType.QUEST_DESC: default_warning("quest", "the game strings", "description"),
            SimpleWarnType.TOWN_BUILDING_NAME: default_warning("town building"),
            SimpleWarnType.TITLE_NAME: default_warning("title"),
            SimpleWarnType.PET_NAME: default_warning("pet"),
            SimpleWarnType.PET_DESC: default_warning("pet", missing_element="description"),
            SimpleWarnType.BOSS_NAME: default_warning("boss", "strings_enemies.xml"),
            SimpleWarnType.BOSS_DESC: default_warning("boss", "strings_enemies.xml", "description"),
            SimpleWarnType.DUNGEON_NAME: default_warning("dungeon", "strings_enemies.xml"),
            SimpleWarnType.DUNGEON_DESC: default_warning("dungeon", "strings_enemies.xml", "description"),
            SimpleWarnType.EXPEDITION_NAME: default_warning("expedition"),
            SimpleWarnType.EXPEDITION_DESC: default_warning("expedition", missing_element="description"),
            SimpleWarnType.SEASONAL_EVENT_NAME: default_warning("seasonal event"),
            SimpleWarnType.SEASONAL_EVENT_BANNER: default_warning("seasonal event", missing_element="banner"),
            SimpleWarnType.SEASONAL_BOUNTY_NAME: default_warning("seasonal bounty"),
            SimpleWarnType.SEASONAL_BOUNTY_HINT: default_warning("seasonal bounty", missing_element="hint"),
            SimpleWarnType.SEASONAL_MINIGAME_NAME: default_warning("seasonal minigame"),
            SimpleWarnType.SEASONAL_REWARD_DESC: default_warning("seasonal reward", missing_element="description"),
            SimpleWarnType.SEASONAL_MARKET_NAME: default_warning("seasonal market offer"),
            SimpleWarnType.PRESTIGE_EFFECT_DESC: default_warning("prestige effect", missing_element="description"),
            SimpleWarnType.HOUSE_ITEM_NAME: default_warning("house item"),
        }
        for value in SimpleWarnType:
            if value not in self._warned_values:
                logging.critical(f"SimpleLogType `{value}` does not have a suitable error message")

    def simple_warn(self, log_type: SimpleWarnType, value: str):
        if value not in self._warned_values[log_type][0]:
            logging.warning(self._warned_values[log_type][1].format(value))
        self._warned_values[log_type][0].add(value)


LOGGER = WikiLogger()
