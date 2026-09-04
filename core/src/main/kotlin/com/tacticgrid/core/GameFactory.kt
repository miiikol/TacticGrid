package com.tacticgrid.core

import com.tacticgrid.core.map.TiledMapLoader
import com.tacticgrid.core.model.GameState
import com.tacticgrid.core.model.Grid
import com.tacticgrid.core.model.GridPos
import com.tacticgrid.core.model.Item
import com.tacticgrid.core.model.ItemType
import com.tacticgrid.core.model.Phase
import com.tacticgrid.core.model.Side
import com.tacticgrid.core.model.Skillbook
import com.tacticgrid.core.model.Stats
import com.tacticgrid.core.model.Unit
import com.tacticgrid.core.model.UnitClass

/** 关卡与初始状态工厂。 */
object GameFactory {

    const val SEED = 42L
    const val MAX_TURNS = 20

    /** 职业基础魔力上限（蓝条）：配合技能与蓝瓶使用。 */
    private val classMp: Map<UnitClass, Int> = mapOf(
        UnitClass.MAGE to 25,
        UnitClass.WARRIOR to 15,
        UnitClass.TANK to 12,
        UnitClass.HEALER to 20,
        UnitClass.SPEARMAN to 15,
    )

    private fun mpOf(cls: UnitClass): Int = classMp[cls] ?: 0

    /**
     * 默认关卡：从 core resources 加载 Tiled 地图（用户绘制），
     * 缺失时退回全开放地图。单位与道具位置按地图空地自动生成。
     */
    fun defaultGame(): GameState {
        val grid = TiledMapLoader.fromResource() ?: Grid.open(32, 20, 0)

        // 玩家（城堡方）放底部连续空地，敌人（野蛮人）放顶部连续空地
        val playerSpawns = spawnRow(grid, 5, fromBottom = true)
        val enemySpawns = spawnRow(grid, 5, fromBottom = false)

        val units = listOf(
            // 城堡方（玩家）
            unit(1, "魔法师", UnitClass.MAGE, Side.PLAYER, playerSpawns[2], stats(20, 15, 3, 90, 10, 3, 3, mpOf(UnitClass.MAGE)), hero = true),
            unit(2, "刀盾兵", UnitClass.WARRIOR, Side.PLAYER, playerSpawns[0], stats(30, 12, 8, 90, 10, 4, 1, mpOf(UnitClass.WARRIOR))),
            unit(3, "重装战士", UnitClass.TANK, Side.PLAYER, playerSpawns[1], stats(42, 9, 12, 85, 5, 3, 1, mpOf(UnitClass.TANK))),
            unit(4, "牧师", UnitClass.HEALER, Side.PLAYER, playerSpawns[3], stats(22, 7, 5, 95, 0, 3, 2, mpOf(UnitClass.HEALER))),
            unit(5, "长枪手", UnitClass.SPEARMAN, Side.PLAYER, playerSpawns[4], stats(26, 11, 6, 88, 8, 4, 2, mpOf(UnitClass.SPEARMAN))),
            // 野蛮人方（敌人）
            unit(6, "邪恶法师", UnitClass.MAGE, Side.ENEMY, enemySpawns[2], stats(20, 15, 3, 90, 10, 3, 3, mpOf(UnitClass.MAGE))),
            unit(7, "维京刀斧手", UnitClass.WARRIOR, Side.ENEMY, enemySpawns[0], stats(30, 12, 8, 90, 10, 4, 1, mpOf(UnitClass.WARRIOR))),
            unit(8, "独眼巨人", UnitClass.TANK, Side.ENEMY, enemySpawns[1], stats(42, 9, 12, 85, 5, 3, 1, mpOf(UnitClass.TANK))),
            unit(9, "巫婆", UnitClass.HEALER, Side.ENEMY, enemySpawns[3], stats(22, 7, 5, 95, 0, 3, 2, mpOf(UnitClass.HEALER))),
            unit(10, "鬼怪", UnitClass.SPEARMAN, Side.ENEMY, enemySpawns[4], stats(26, 11, 6, 88, 8, 4, 2, mpOf(UnitClass.SPEARMAN))),
        )

        // 每局随机刷新道具：避开单位出生点，随机类型
        val items = randomItems(grid, 6, (playerSpawns + enemySpawns).toSet())

        return GameState(
            phase = Phase.START,
            grid = grid,
            units = units,
            currentUnitId = null,
            reachableTiles = emptySet(),
            attackableTiles = emptySet(),
            turnNumber = 0,
            maxTurns = MAX_TURNS,
            winner = null,
            message = "点击「开始」",
            rngSeed = SEED,
            rngCounter = 0,
            movedIds = emptySet(),
            actedIds = emptySet(),
            items = items,
        )
    }

    /** 在指定 y 范围内，找一行「连续 [count] 个空地」，找不到则退回任意空地。 */
    private fun spawnRow(grid: Grid, count: Int, yRange: IntProgression): List<GridPos> {
        for (y in yRange) {
            val run = mutableListOf<GridPos>()
            for (x in 0 until grid.width) {
                val p = GridPos(x, y)
                if (!grid.isBlocked(p)) run += p else run.clear()
                if (run.size == count) return run
            }
        }
        // 兜底：任意空地
        return (0 until grid.height)
            .flatMap { y -> (0 until grid.width).map { x -> GridPos(x, y) } }
            .filter { !grid.isBlocked(it) }
            .take(count)
    }

    /** 从底部（true）或顶部（false）找连续 [count] 个空地。 */
    private fun spawnRow(grid: Grid, count: Int, fromBottom: Boolean): List<GridPos> {
        val range = if (fromBottom) (grid.height - 1) downTo 0 else 0 until grid.height
        return spawnRow(grid, count, range)
    }

    /** 在地图空地上随机刷新 [count] 个道具，避开 [exclude] 位置。 */
    private fun randomItems(grid: Grid, count: Int, exclude: Set<GridPos>): List<Item> {
        val open = (0 until grid.height)
            .flatMap { y -> (0 until grid.width).map { x -> GridPos(x, y) } }
            .filter { !grid.isBlocked(it) && it !in exclude }
        if (open.isEmpty()) return emptyList()
        val rng = kotlin.random.Random
        val spots = open.shuffled(rng).take(count)
        val types = ItemType.entries
        return spots.mapIndexed { i, pos -> Item(i + 1, types[rng.nextInt(types.size)], pos) }
    }

    private fun stats(maxHp: Int, atk: Int, def: Int, hit: Int, crit: Int, mov: Int, rng: Int, maxMp: Int) =
        Stats(maxHp, atk, def, hit, crit, mov, rng, maxMp)

    private fun unit(id: Int, name: String, cls: UnitClass, side: Side, pos: GridPos, stats: Stats, hero: Boolean = false) =
        Unit(
            id, name, cls, side, pos, stats, stats.maxHp, hero,
            currentMp = stats.maxMp,
            skills = Skillbook.skillsFor(cls),
        )
}
