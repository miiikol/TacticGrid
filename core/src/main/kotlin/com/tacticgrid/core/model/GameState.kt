package com.tacticgrid.core.model

import kotlinx.serialization.Serializable

/** 阵营。 */
@Serializable
enum class Side { PLAYER, ENEMY }

/** 回合阶段状态机。 */
@Serializable
enum class Phase { START, PLAYER_TURN, UNIT_SELECTED, ENEMY_TURN, GAME_OVER }

/**
 * 单一不可变游戏状态，UDF 单向数据流的核心。
 * UI 只读此状态，任何变更都必须通过 [com.tacticgrid.core.engine.reduce] 产生新状态。
 */
@Serializable
data class GameState(
    val phase: Phase,
    val grid: Grid,
    val units: List<Unit>,
    val currentUnitId: Int?,
    val reachableTiles: Set<GridPos>,
    val attackableTiles: Set<GridPos>,
    val turnNumber: Int,
    val maxTurns: Int,
    val winner: Side?,
    val message: String,
    // 随机数种子与计数器，保证命中/暴击可复现、可单测
    val rngSeed: Long,
    val rngCounter: Long,
    // 本回合已移动/已行动的单位 id，用于约束"移动+行动各一次"
    val movedIds: Set<Int>,
    val actedIds: Set<Int>,
    // 地图上尚未被拾取的可交互道具
    val items: List<Item> = emptyList(),
    // 正在使用的道具（选择目标阶段）
    val usingItem: ItemType? = null,
    // 当前道具可作用的目标位置
    val itemTargets: Set<GridPos> = emptySet(),
    // 正在准备施放的技能 id（选择目标阶段，见 Unit.skills / Skillbook）
    val usingSkillId: String? = null,
    // 当前技能可作用的目标位置
    val skillTargets: Set<GridPos> = emptySet(),
) {
    fun unit(id: Int): Unit? = units.firstOrNull { it.id == id }

    fun unitAt(pos: GridPos): Unit? = units.firstOrNull { it.pos == pos && it.isAlive }

    fun currentUnit(): Unit? = currentUnitId?.let(::unit)

    fun aliveUnits(side: Side): List<Unit> = units.filter { it.side == side && it.isAlive }

    /** 存活单位占据的格子（移动不可穿越），可排除某个单位。 */
    fun occupiedTiles(excludeId: Int? = null): Set<GridPos> =
        units.filter { it.id != excludeId && it.isAlive }.map { it.pos }.toSet()
}
