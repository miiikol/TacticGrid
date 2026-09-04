package com.tacticgrid.core.ai

import com.tacticgrid.core.engine.GameEvent
import com.tacticgrid.core.engine.reduce
import com.tacticgrid.core.model.GameState
import com.tacticgrid.core.model.GridPos
import com.tacticgrid.core.model.Side
import com.tacticgrid.core.model.Unit
import com.tacticgrid.core.pathfinding.Pathfinding

/**
 * 第一版敌方 AI（贪心）：能打到人则集火最弱目标，否则朝最近敌人移动。
 * 返回一整个敌方回合的事件序列，由调用方按序应用。
 */
object GreedyAI {

    fun decide(state: GameState): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        var current = state
        val danger = dangerMap(state)
        for (enemy in current.aliveUnits(Side.ENEMY).sortedBy { it.id }) {
            val unit = current.unit(enemy.id) ?: continue
            if (!unit.isAlive) continue
            val action = decideUnit(current, unit, danger)
            if (action != null) {
                events += action
                current = reduce(current, action).state
            }
        }
        return events
    }

    private fun decideUnit(state: GameState, unit: Unit, danger: Set<GridPos>): GameEvent? {
        val targets = attackableTargets(state, unit)
        if (targets.isNotEmpty()) {
            // 集火"最弱"目标：血量低、价值高者优先
            val target = targets.minWith(compareBy({ it.currentHp }, { -classValue(it.unitClass) }))
            return GameEvent.Attack(unit.id, target.id)
        }
        return chooseMove(state, unit, danger)?.let { GameEvent.MoveUnit(unit.id, it) }
    }

    private fun chooseMove(state: GameState, unit: Unit, danger: Set<GridPos>): GridPos? {
        val reachable = Pathfinding.reachable(state.grid, unit.pos, unit.stats.mov, state.occupiedTiles(unit.id))
        if (reachable.isEmpty()) return null
        val enemies = state.aliveUnits(Side.PLAYER)
        if (enemies.isEmpty()) return null
        // 朝最近敌人靠拢；距离相同时优先避开危险格
        return reachable.keys.minWithOrNull(
            compareBy<GridPos>({ distToNearest(it, enemies) }, { if (it in danger) 1 else 0 })
        )
    }

    private fun distToNearest(pos: GridPos, enemies: List<Unit>): Int =
        enemies.minOf { pos.manhattanTo(it.pos) }
}
