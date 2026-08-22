package com.fantasyidler.repository

import com.fantasyidler.data.json.HouseCostTier
import com.fantasyidler.data.json.HouseTileDef
import com.fantasyidler.data.model.HouseData
import com.fantasyidler.data.model.HousePlacement
import com.fantasyidler.data.model.HouseRoom
import com.fantasyidler.data.model.Skills
import javax.inject.Inject
import javax.inject.Singleton

enum class HouseActionResult {
    SUCCESS,
    INSUFFICIENT_LEVEL,
    INSUFFICIENT_COINS,
    INSUFFICIENT_MATERIALS,
    INVALID_SPOT,
    MAX_ROOMS,
    MAX_ROOM_SIZE,
    MIN_ROOM_SIZE,
    ALREADY_PLACED,
    LAST_ROOM,
}

enum class HouseDirection { NORTH, SOUTH, EAST, WEST }

/**
 * Player housing: rooms bought/expanded with coins + materials gated by Construction level,
 * furnished with pieces paid for the same way. All state lives in PlayerFlags.house.
 */
@Singleton
class HouseRepository @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val boostRepo: BoostRepository,
) {
    companion object {
        /** The house grid is GRID_SIZE x GRID_SIZE cells; rooms must fit inside it. */
        // 18x18 lets all six rooms reach max size (6 x 9x9 fits with room to arrange);
        // the canvas pans and zooms, so the grid no longer has to fit the screen at 1x.
        const val GRID_SIZE = 18
        const val STARTER_ROOM_SIZE = 4
        const val NEW_ROOM_SIZE = 3
        const val MAX_ROOM_DIM = 9
        /** Construction level that unlocks the brick floor style. */
        const val BRICK_FLOOR_LEVEL = 10

        /**
         * Placement-grid subdivisions per room cell: placements snap to half cells.
         * Rooms stay on the full-cell grid; all placement coordinates are half-cell units.
         */
        const val SUB = 2

        /** Placement key prefix for earned seasonal banners: "banner:" + drawable name. */
        const val BANNER_PREFIX = "banner:"

        /** Synthetic def for seasonal banners: free wall decor, one cell, no atlas sprite. */
        val BANNER_DEF = HouseTileDef(
            x = 0, y = 0, w = 16, h = 29,
            footprintW = 1, footprintH = 1,
            wallMounted = true, category = "banner",
            levelRequired = 1, coinCost = 0L, materials = emptyMap(), xp = 0L,
        )

        fun roomsOverlap(a: HouseRoom, x: Int, y: Int, w: Int, h: Int): Boolean =
            a.x < x + w && x < a.x + a.w && a.y < y + h && y < a.y + a.h

        /** True when the rect shares at least one full cell edge with [room]. */
        fun sharesEdge(room: HouseRoom, x: Int, y: Int, w: Int, h: Int): Boolean {
            val horizontalOverlap = room.x < x + w && x < room.x + room.w
            val verticalOverlap   = room.y < y + h && y < room.y + room.h
            val touchesVertically   = (room.y + room.h == y || y + h == room.y) && horizontalOverlap
            val touchesHorizontally = (room.x + room.w == x || x + w == room.x) && verticalOverlap
            return touchesVertically || touchesHorizontally
        }

        fun roomContaining(rooms: List<HouseRoom>, x: Int, y: Int, w: Int, h: Int): HouseRoom? =
            rooms.firstOrNull { x >= it.x && y >= it.y && x + w <= it.x + it.w && y + h <= it.y + it.h }

        /**
         * Room containing a footprint given in placement (half-cell) units. A footprint may
         * ride half a cell above the room's top row so furniture can nestle against the
         * north wall the way tall sprites (beds, shelves) visually do.
         */
        fun roomContainingP(rooms: List<HouseRoom>, px: Int, py: Int, fw: Int, fh: Int): HouseRoom? =
            rooms.firstOrNull {
                px >= it.x * SUB && py >= it.y * SUB - 1 &&
                    px + fw * SUB <= (it.x + it.w) * SUB && py + fh * SUB <= (it.y + it.h) * SUB
            }

        /**
         * True when every half-cell of the footprint lies inside SOME room, letting
         * furniture straddle the seam between adjacent rooms. Each room also grants the
         * half-cell just above its top row (the lean-against-the-north-wall allowance).
         */
        fun footprintCovered(rooms: List<HouseRoom>, px: Int, py: Int, fw: Int, fh: Int): Boolean =
            (px until px + fw * SUB).all { cx ->
                (py until py + fh * SUB).all { cy ->
                    rooms.any { r ->
                        cx >= r.x * SUB && cx < (r.x + r.w) * SUB &&
                            cy >= r.y * SUB - 1 && cy < (r.y + r.h) * SUB
                    }
                }
            }

        /** [hasWallAbove] for a footprint given in placement (half-cell) units. */
        fun hasWallAboveP(rooms: List<HouseRoom>, room: HouseRoom, px: Int, fw: Int): Boolean {
            val startCol = px / SUB
            val endCol = (px + fw * SUB - 1) / SUB
            return hasWallAbove(rooms, room, startCol, endCol - startCol + 1)
        }

        /**
         * True when every column of a footprint on [room]'s top row has a visible north wall:
         * walls render only on the northernmost room of each column, so any other room lying
         * anywhere north of this one in that column removes the wall.
         */
        fun hasWallAbove(rooms: List<HouseRoom>, room: HouseRoom, x: Int, fw: Int): Boolean =
            (x until x + fw).all { cx ->
                rooms.none { other ->
                    other !== room && cx >= other.x && cx < other.x + other.w &&
                        other.y < room.y
                }
            }
    }

    /**
     * Room a placement belongs to. Edge-anchored walls render on their cell's top/left
     * grid line, so they may also hug a room's south/east side with their footprint
     * just outside it; the shifted check covers that.
     */
    fun containingRoomFor(house: HouseData, def: HouseTileDef, x: Int, y: Int): HouseRoom? {
        roomContainingP(house.rooms, x, y, def.footprintW, def.footprintH)?.let { return it }
        return when (def.anchor) {
            "top_edge" -> roomContainingP(house.rooms, x, y - def.footprintH * SUB, def.footprintW, def.footprintH)
            "left_edge" -> roomContainingP(house.rooms, x - def.footprintW * SUB, y, def.footprintW, def.footprintH)
            else -> null
        }
    }

    fun tileDef(key: String): HouseTileDef? =
        if (key.startsWith(BANNER_PREFIX)) BANNER_DEF else gameData.houseTiles.items[key]

    /** Cost of the next room purchase, or null when all rooms are owned. */
    fun nextRoomCost(roomsOwned: Int): HouseCostTier? =
        gameData.houseTiles.rooms.getOrNull(roomsOwned - 1)

    /** Per-new-cell expansion cost for the room at [roomIndex]. */
    fun expansionCost(roomIndex: Int): HouseCostTier =
        gameData.houseTiles.expansion.let { it.getOrNull(roomIndex) ?: it.last() }

    /** Creates the free starter room on first visit, and migrates coordinate scale. */
    suspend fun ensureStarterRoom() = playerRepo.withLock {
        val flags = playerRepo.getFlagsUnlocked()
        val house = flags.house
        if (house == null) {
            val offset = (GRID_SIZE - STARTER_ROOM_SIZE) / 2
            val starter = HouseRoom(offset, offset, STARTER_ROOM_SIZE, STARTER_ROOM_SIZE)
            playerRepo.updateFlagsUnlocked(flags.copy(
                house = HouseData(rooms = listOf(starter), coordScale = SUB)))
        } else if (house.coordScale < SUB) {
            // Legacy full-cell placement coordinates become half-cell units.
            val f = SUB / house.coordScale
            playerRepo.updateFlagsUnlocked(flags.copy(house = house.copy(
                placements = house.placements.map { it.copy(x = it.x * f, y = it.y * f) },
                coordScale = SUB,
            )))
        }
    }

    suspend fun buyRoom(x: Int, y: Int): HouseActionResult = playerRepo.withLock {
        val flags = playerRepo.getFlagsUnlocked()
        val house = flags.house ?: return@withLock HouseActionResult.INVALID_SPOT
        val tier = nextRoomCost(house.rooms.size) ?: return@withLock HouseActionResult.MAX_ROOMS
        val size = NEW_ROOM_SIZE

        if (x < 0 || y < 0 || x + size > GRID_SIZE || y + size > GRID_SIZE)
            return@withLock HouseActionResult.INVALID_SPOT
        if (house.rooms.any { roomsOverlap(it, x, y, size, size) })
            return@withLock HouseActionResult.INVALID_SPOT
        if (house.rooms.none { sharesEdge(it, x, y, size, size) })
            return@withLock HouseActionResult.INVALID_SPOT

        val result = payTier(tier, cells = 1)
        if (result != HouseActionResult.SUCCESS) return@withLock result

        val updated = house.copy(rooms = house.rooms + HouseRoom(x, y, size, size))
        playerRepo.updateFlagsUnlocked(playerRepo.getFlagsUnlocked().copy(house = updated))
        HouseActionResult.SUCCESS
    }

    suspend fun expandRoom(roomIndex: Int, dir: HouseDirection): HouseActionResult = playerRepo.withLock {
        val flags = playerRepo.getFlagsUnlocked()
        val house = flags.house ?: return@withLock HouseActionResult.INVALID_SPOT
        val room = house.rooms.getOrNull(roomIndex) ?: return@withLock HouseActionResult.INVALID_SPOT

        val (nx, ny, nw, nh) = when (dir) {
            HouseDirection.NORTH -> listOf(room.x, room.y - 1, room.w, room.h + 1)
            HouseDirection.SOUTH -> listOf(room.x, room.y, room.w, room.h + 1)
            HouseDirection.WEST  -> listOf(room.x - 1, room.y, room.w + 1, room.h)
            HouseDirection.EAST  -> listOf(room.x, room.y, room.w + 1, room.h)
        }
        if (nw > MAX_ROOM_DIM || nh > MAX_ROOM_DIM) return@withLock HouseActionResult.MAX_ROOM_SIZE
        if (nx < 0 || ny < 0 || nx + nw > GRID_SIZE || ny + nh > GRID_SIZE)
            return@withLock HouseActionResult.INVALID_SPOT
        if (house.rooms.filterIndexed { i, _ -> i != roomIndex }
                .any { roomsOverlap(it, nx, ny, nw, nh) })
            return@withLock HouseActionResult.INVALID_SPOT

        val newCells = if (dir == HouseDirection.NORTH || dir == HouseDirection.SOUTH) room.w else room.h
        val result = payTier(expansionCost(roomIndex), cells = newCells)
        if (result != HouseActionResult.SUCCESS) return@withLock result

        val updatedRooms = house.rooms.toMutableList()
        updatedRooms[roomIndex] = room.copy(x = nx, y = ny, w = nw, h = nh)
        playerRepo.updateFlagsUnlocked(playerRepo.getFlagsUnlocked().copy(house = house.copy(rooms = updatedRooms)))
        HouseActionResult.SUCCESS
    }

    /**
     * Shrinks a room by one row/column from the [dir] side, refunding that side's
     * per-cell expansion cost. Furnishings in the removed strip go to storage.
     */
    suspend fun shrinkRoom(roomIndex: Int, dir: HouseDirection): HouseActionResult = playerRepo.withLock {
        val flags = playerRepo.getFlagsUnlocked()
        val house = flags.house ?: return@withLock HouseActionResult.INVALID_SPOT
        val room = house.rooms.getOrNull(roomIndex) ?: return@withLock HouseActionResult.INVALID_SPOT

        val (nx, ny, nw, nh) = when (dir) {
            HouseDirection.NORTH -> listOf(room.x, room.y + 1, room.w, room.h - 1)
            HouseDirection.SOUTH -> listOf(room.x, room.y, room.w, room.h - 1)
            HouseDirection.WEST  -> listOf(room.x + 1, room.y, room.w - 1, room.h)
            HouseDirection.EAST  -> listOf(room.x, room.y, room.w - 1, room.h)
        }
        if (nw < NEW_ROOM_SIZE || nh < NEW_ROOM_SIZE) return@withLock HouseActionResult.MIN_ROOM_SIZE

        val newRoom = room.copy(x = nx, y = ny, w = nw, h = nh)
        val newStorage = house.storage.toMutableMap()
        val keptPlacements = house.placements.filter { p ->
            // Only pieces that were in this room and no longer fit its shrunken rect leave.
            if (!placementInRoom(p, room) || placementInRoom(p, newRoom)) return@filter true
            if (!p.item.startsWith(BANNER_PREFIX)) {
                newStorage[p.item] = (newStorage[p.item] ?: 0) + 1
            }
            false
        }

        // Refund the strip at the same per-cell rate the expansion charges today.
        val cells = if (dir == HouseDirection.NORTH || dir == HouseDirection.SOUTH) room.w else room.h
        val tier = expansionCost(roomIndex)
        val level = playerRepo.getSkillLevels()[Skills.CONSTRUCTION] ?: 1
        val perMille = boostRepo.builderDiscountPerMille(flags)
        playerRepo.applyMultiSkillResultsUnlocked(
            xpPerSkill = emptyMap(),
            itemsGained = TownRepository.discountedMaterials(
                tier.materials.mapValues { it.value * cells }, level, perMille),
            coinsGained = TownRepository.discountedCoins(tier.coins * cells, level, perMille),
        )

        val updatedRooms = house.rooms.toMutableList()
        updatedRooms[roomIndex] = newRoom
        val updated = storeOrphanedWallDecor(house.copy(rooms = updatedRooms, placements = keptPlacements, storage = newStorage))
        playerRepo.updateFlagsUnlocked(playerRepo.getFlagsUnlocked().copy(house = updated))
        HouseActionResult.SUCCESS
    }

    /** True when the placement's footprint (half-cell units) lies inside [room]. */
    private fun placementInRoom(p: HousePlacement, room: HouseRoom): Boolean {
        val def = tileDef(p.item) ?: return false
        fun inRect(x: Int, y: Int): Boolean =
            // Matches roomContainingP: half-cell overhang into the north wall is allowed.
            x >= room.x * SUB && y >= room.y * SUB - 1 &&
                x + def.footprintW * SUB <= (room.x + room.w) * SUB &&
                y + def.footprintH * SUB <= (room.y + room.h) * SUB
        if (inRect(p.x, p.y)) return true
        // Edge-anchored walls hugging the room's south/east side belong to it too.
        return when (def.anchor) {
            "top_edge" -> inRect(p.x, p.y - def.footprintH * SUB)
            "left_edge" -> inRect(p.x - def.footprintW * SUB, p.y)
            else -> false
        }
    }

    /**
     * Moves a room and everything in it to a new top-left cell. Free. Wall decor anywhere
     * in the house that loses its wall (a room now sits directly above it) goes to storage.
     */
    suspend fun moveRoom(roomIndex: Int, nx: Int, ny: Int): HouseActionResult = playerRepo.withLock {
        val flags = playerRepo.getFlagsUnlocked()
        val house = flags.house ?: return@withLock HouseActionResult.INVALID_SPOT
        val room = house.rooms.getOrNull(roomIndex) ?: return@withLock HouseActionResult.INVALID_SPOT

        if (nx < 0 || ny < 0 || nx + room.w > GRID_SIZE || ny + room.h > GRID_SIZE)
            return@withLock HouseActionResult.INVALID_SPOT
        val others = house.rooms.filterIndexed { i, _ -> i != roomIndex }
        if (others.any { roomsOverlap(it, nx, ny, room.w, room.h) })
            return@withLock HouseActionResult.INVALID_SPOT
        if (others.isNotEmpty() && others.none { sharesEdge(it, nx, ny, room.w, room.h) })
            return@withLock HouseActionResult.INVALID_SPOT

        val dx = (nx - room.x) * SUB
        val dy = (ny - room.y) * SUB
        val movedRooms = house.rooms.toMutableList()
        movedRooms[roomIndex] = room.copy(x = nx, y = ny)
        var moved = house.copy(
            rooms = movedRooms,
            placements = house.placements.map { p ->
                if (placementInRoom(p, room)) p.copy(x = p.x + dx, y = p.y + dy) else p
            },
        )
        moved = storeOrphanedWallDecor(moved)
        playerRepo.updateFlagsUnlocked(flags.copy(house = moved))
        HouseActionResult.SUCCESS
    }

    /**
     * Demolishes a room: its furnishings go to storage (banners come down for free), the
     * room's build cost is not refunded. The last room cannot be demolished.
     */
    suspend fun removeRoom(roomIndex: Int): HouseActionResult = playerRepo.withLock {
        val flags = playerRepo.getFlagsUnlocked()
        val house = flags.house ?: return@withLock HouseActionResult.INVALID_SPOT
        if (house.rooms.size <= 1) return@withLock HouseActionResult.LAST_ROOM
        val room = house.rooms.getOrNull(roomIndex) ?: return@withLock HouseActionResult.INVALID_SPOT

        val newStorage = house.storage.toMutableMap()
        val keptPlacements = house.placements.filter { p ->
            if (!placementInRoom(p, room)) return@filter true
            if (!p.item.startsWith(BANNER_PREFIX)) {
                newStorage[p.item] = (newStorage[p.item] ?: 0) + 1
            }
            false
        }
        val updated = storeOrphanedWallDecor(house.copy(
            rooms = house.rooms.filterIndexed { i, _ -> i != roomIndex },
            placements = keptPlacements,
            storage = newStorage,
        ))
        playerRepo.updateFlagsUnlocked(flags.copy(house = updated))
        HouseActionResult.SUCCESS
    }

    /**
     * Sends placements whose support disappeared to storage: wall decor without a wall
     * (a room moved above it) and table decorations without a table underneath.
     */
    private fun storeOrphanedWallDecor(house: HouseData): HouseData {
        val newStorage = house.storage.toMutableMap()
        val kept = house.placements.filter { p ->
            val def = tileDef(p.item) ?: return@filter true
            val valid = when {
                def.wallMounted -> {
                    val room = roomContainingP(house.rooms, p.x, p.y, def.footprintW, def.footprintH)
                    room != null && p.y == room.y * SUB &&
                        hasWallAboveP(house.rooms, room, p.x, def.footprintW)
                }
                def.tableOnly -> tableCovered(house, p.x, p.y, def.footprintW, def.footprintH)
                else -> containingRoomFor(house, def, p.x, p.y) != null ||
                    footprintCovered(house.rooms, p.x, p.y, def.footprintW, def.footprintH)
            }
            if (!valid && !p.item.startsWith(BANNER_PREFIX)) {
                newStorage[p.item] = (newStorage[p.item] ?: 0) + 1
            }
            valid
        }
        return house.copy(placements = kept, storage = newStorage)
    }

    suspend fun setFloor(roomIndex: Int, style: String): HouseActionResult = playerRepo.withLock {
        val flags = playerRepo.getFlagsUnlocked()
        val house = flags.house ?: return@withLock HouseActionResult.INVALID_SPOT
        val room = house.rooms.getOrNull(roomIndex) ?: return@withLock HouseActionResult.INVALID_SPOT
        if (style == "brick" && constructionLevel() < BRICK_FLOOR_LEVEL)
            return@withLock HouseActionResult.INSUFFICIENT_LEVEL
        val updatedRooms = house.rooms.toMutableList()
        updatedRooms[roomIndex] = room.copy(floor = style)
        playerRepo.updateFlagsUnlocked(flags.copy(house = house.copy(rooms = updatedRooms)))
        HouseActionResult.SUCCESS
    }

    /**
     * Places [key] with its footprint's top-left cell at (x, y). Uses a stored copy when one
     * exists, otherwise pays the build cost (coins + materials, Construction level gated).
     */
    suspend fun placeItem(key: String, x: Int, y: Int): HouseActionResult = playerRepo.withLock {
        val def = tileDef(key) ?: return@withLock HouseActionResult.INVALID_SPOT
        val flags = playerRepo.getFlagsUnlocked()
        val house = flags.house ?: return@withLock HouseActionResult.INVALID_SPOT

        val room = containingRoomFor(house, def, x, y)
        if (def.wallMounted) {
            if (room == null || y != room.y * SUB || !hasWallAboveP(house.rooms, room, x, def.footprintW))
                return@withLock HouseActionResult.INVALID_SPOT
        } else if (room == null && !footprintCovered(house.rooms, x, y, def.footprintW, def.footprintH)) {
            return@withLock HouseActionResult.INVALID_SPOT
        }
        if (def.tableOnly && !tableCovered(house, x, y, def.footprintW, def.footprintH))
            return@withLock HouseActionResult.INVALID_SPOT
        if (collides(house, def, x, y)) return@withLock HouseActionResult.INVALID_SPOT

        val stored = house.storage[key] ?: 0
        if (key.startsWith(BANNER_PREFIX)) {
            // Earned seasonal banners are free trophies: one placement per banner, no cost.
            val icon = key.removePrefix(BANNER_PREFIX)
            if (flags.seasonalBannersEarned.none { it.bannerIcon == icon })
                return@withLock HouseActionResult.INVALID_SPOT
            if (house.placements.any { it.item == key })
                return@withLock HouseActionResult.ALREADY_PLACED
        } else if (stored <= 0) {
            if (constructionLevel() < def.levelRequired) return@withLock HouseActionResult.INSUFFICIENT_LEVEL
            val pay = payTier(HouseCostTier(def.levelRequired, def.coinCost, def.materials, def.xp), cells = 1)
            if (pay != HouseActionResult.SUCCESS) return@withLock pay
        }

        val latest = playerRepo.getFlagsUnlocked()
        val latestHouse = latest.house ?: return@withLock HouseActionResult.INVALID_SPOT
        val newStorage = if (stored > 0 && !key.startsWith(BANNER_PREFIX)) {
            latestHouse.storage.toMutableMap().apply {
                if (stored - 1 <= 0) remove(key) else put(key, stored - 1)
            }
        } else latestHouse.storage
        playerRepo.updateFlagsUnlocked(latest.copy(house = latestHouse.copy(
            placements = latestHouse.placements + HousePlacement(key, x, y),
            storage = newStorage,
        )))
        HouseActionResult.SUCCESS
    }

    /** Moves the placement at [index] to (x, y). Free: the piece was already paid for. */
    suspend fun moveItem(index: Int, x: Int, y: Int): HouseActionResult = playerRepo.withLock {
        val flags = playerRepo.getFlagsUnlocked()
        val house = flags.house ?: return@withLock HouseActionResult.INVALID_SPOT
        val placement = house.placements.getOrNull(index) ?: return@withLock HouseActionResult.INVALID_SPOT
        val def = tileDef(placement.item) ?: return@withLock HouseActionResult.INVALID_SPOT

        val room = containingRoomFor(house, def, x, y)
        if (def.wallMounted) {
            if (room == null || y != room.y * SUB || !hasWallAboveP(house.rooms, room, x, def.footprintW))
                return@withLock HouseActionResult.INVALID_SPOT
        } else if (room == null && !footprintCovered(house.rooms, x, y, def.footprintW, def.footprintH)) {
            return@withLock HouseActionResult.INVALID_SPOT
        }
        if (def.tableOnly && !tableCovered(house, x, y, def.footprintW, def.footprintH))
            return@withLock HouseActionResult.INVALID_SPOT
        if (collides(house, def, x, y, excludeIndex = index)) return@withLock HouseActionResult.INVALID_SPOT

        val updated = house.placements.toMutableList()
        updated[index] = placement.copy(x = x, y = y)
        playerRepo.updateFlagsUnlocked(flags.copy(
            house = storeOrphanedWallDecor(house.copy(placements = updated))))
        HouseActionResult.SUCCESS
    }

    /** Swaps the placement at [index] to its rotated variant, keeping its top-left cell. */
    suspend fun rotateItem(index: Int): HouseActionResult = playerRepo.withLock {
        val flags = playerRepo.getFlagsUnlocked()
        val house = flags.house ?: return@withLock HouseActionResult.INVALID_SPOT
        val placement = house.placements.getOrNull(index) ?: return@withLock HouseActionResult.INVALID_SPOT
        val rotatedKey = tileDef(placement.item)?.rotatesTo ?: return@withLock HouseActionResult.INVALID_SPOT
        val def = tileDef(rotatedKey) ?: return@withLock HouseActionResult.INVALID_SPOT

        val room = containingRoomFor(house, def, placement.x, placement.y)
        if (def.wallMounted) {
            if (room == null || placement.y != room.y * SUB ||
                !hasWallAboveP(house.rooms, room, placement.x, def.footprintW))
                return@withLock HouseActionResult.INVALID_SPOT
        } else if (room == null &&
            !footprintCovered(house.rooms, placement.x, placement.y, def.footprintW, def.footprintH)) {
            return@withLock HouseActionResult.INVALID_SPOT
        }
        if (def.tableOnly && !tableCovered(house, placement.x, placement.y, def.footprintW, def.footprintH))
            return@withLock HouseActionResult.INVALID_SPOT
        if (collides(house, def, placement.x, placement.y, excludeIndex = index))
            return@withLock HouseActionResult.INVALID_SPOT

        val updated = house.placements.toMutableList()
        updated[index] = placement.copy(item = rotatedKey)
        playerRepo.updateFlagsUnlocked(flags.copy(
            house = storeOrphanedWallDecor(house.copy(placements = updated))))
        HouseActionResult.SUCCESS
    }

    /** Sets the outdoor ground texture. Free, cosmetic. */
    suspend fun setGround(key: String): HouseActionResult = playerRepo.withLock {
        if (key !in gameData.houseTiles.grounds) return@withLock HouseActionResult.INVALID_SPOT
        val flags = playerRepo.getFlagsUnlocked()
        val house = flags.house ?: return@withLock HouseActionResult.INVALID_SPOT
        playerRepo.updateFlagsUnlocked(flags.copy(house = house.copy(ground = key)))
        HouseActionResult.SUCCESS
    }

    /** Removes the placement at [index]; the piece goes to storage, materials are kept. */
    suspend fun removeItem(index: Int): HouseActionResult = playerRepo.withLock {
        val flags = playerRepo.getFlagsUnlocked()
        val house = flags.house ?: return@withLock HouseActionResult.INVALID_SPOT
        val placement = house.placements.getOrNull(index) ?: return@withLock HouseActionResult.INVALID_SPOT
        val newStorage = house.storage.toMutableMap()
        // Banners are free trophies: removing one never creates a storage entry.
        if (!placement.item.startsWith(BANNER_PREFIX)) {
            newStorage[placement.item] = (newStorage[placement.item] ?: 0) + 1
        }
        playerRepo.updateFlagsUnlocked(flags.copy(house = storeOrphanedWallDecor(house.copy(
            placements = house.placements.filterIndexed { i, _ -> i != index },
            storage = newStorage,
        ))))
        HouseActionResult.SUCCESS
    }

    /**
     * Collision layer: floor furniture (1), wall decor (2), and each divider orientation
     * (3, 4) collide only among themselves — dividers occupy grid lines, not cell space,
     * so furniture can stand beside them and the two orientations can meet in corners.
     */
    private fun layerOf(def: HouseTileDef): Int = when {
        def.wallMounted -> 2
        def.anchor == "left_edge" -> 3
        def.anchor == "top_edge" -> 4
        def.tableOnly -> 5
        else -> 1
    }

    /** True when every half-cell of the footprint is covered by a placed table. */
    fun tableCovered(house: HouseData, x: Int, y: Int, fw: Int, fh: Int): Boolean {
        val tables = house.placements.filter { it.item.startsWith("table_") }
        return (x until x + fw * SUB).all { cx ->
            (y until y + fh * SUB).all { cy ->
                tables.any { t ->
                    val td = tileDef(t.item) ?: return@any false
                    cx >= t.x && cx < t.x + td.footprintW * SUB &&
                        cy >= t.y && cy < t.y + td.footprintH * SUB
                }
            }
        }
    }

    /** True when the footprint would overlap another placement on the same layer. */
    fun collides(house: HouseData, def: HouseTileDef, x: Int, y: Int, excludeIndex: Int? = null): Boolean =
        house.placements.withIndex().any { (i, other) ->
            if (i == excludeIndex) return@any false
            val otherDef = tileDef(other.item) ?: return@any false
            if (layerOf(otherDef) != layerOf(def)) return@any false
            other.x < x + def.footprintW * SUB && x < other.x + otherDef.footprintW * SUB &&
                other.y < y + def.footprintH * SUB && y < other.y + otherDef.footprintH * SUB
        }

    private suspend fun constructionLevel(): Int =
        playerRepo.getSkillLevels()[Skills.CONSTRUCTION] ?: 1

    /** Checks + pays [tier] scaled by [cells], applying the builder discount, and awards XP. */
    private suspend fun payTier(tier: HouseCostTier, cells: Int): HouseActionResult {
        val level = constructionLevel()
        if (level < tier.level) return HouseActionResult.INSUFFICIENT_LEVEL

        val perMille = boostRepo.builderDiscountPerMille(playerRepo.getFlagsUnlocked())
        val coinCost = TownRepository.discountedCoins(tier.coins * cells, level, perMille)
        val materials = TownRepository.discountedMaterials(
            tier.materials.mapValues { it.value * cells }, level, perMille)

        val player = playerRepo.getOrCreatePlayer()
        if (player.coins < coinCost) return HouseActionResult.INSUFFICIENT_COINS
        val inventory = playerRepo.getInventoryUnlocked()
        for ((item, qty) in materials) {
            if ((inventory[item] ?: 0) < qty) return HouseActionResult.INSUFFICIENT_MATERIALS
        }

        playerRepo.consumeItemsUnlocked(materials)
        playerRepo.spendCoinsUnlocked(coinCost)
        if (tier.xp > 0) {
            playerRepo.applyMultiSkillResultsUnlocked(
                mapOf(Skills.CONSTRUCTION to tier.xp * cells), emptyMap())
        }
        return HouseActionResult.SUCCESS
    }
}
