package com.tacticgrid.core.ai

import com.tacticgrid.core.engine.GameEvent
import com.tacticgrid.core.model.GameState
import com.tacticgrid.core.model.Phase
import com.tacticgrid.core.model.Side
import com.tacticgrid.core.model.Unit
import com.tacticgrid.core.pathfinding.Pathfinding

/**
 * 第二版敌方 AI：评估函数 + 极小极大（α-β 剪枝，深度 2）。
 * 以敌方视角对每个敌方单位做局部搜索——它会考虑"我行动后，玩家最优反击"，
 * 从而比贪心更懂集火、占位与保护治疗。
 */
object MinimaxAI {

    private const val MAX_DEPTH = 2

    fun decide(state: GameState): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        var current = state
        for (enemy in current.aliveUnits(Side.ENEMY).sortedBy { it.id }) {
            val unit = current.unit(enemy.id) ?: continue
            if (!unit.isAlive) continue
            val action = bestAction(current, unit) ?: continue
            events += action
            current = applyEvents(current, action)
        }
        return events
    }

    /** 为单个敌方单位选择收益最高的动作序列（移动+攻击）。 */
    private fun bestAction(state: GameState, unit: Unit): List<GameEvent>? {
        var best: List<GameEvent>? = null
        var alpha = Int.MIN_VALUE
        for (candidate in candidates(state, unit)) {
            val simulated = applyEvents(state, candidate)
            val score = minimize(simulated, MAX_DEPTH - 1, alpha, Int.MAX_VALUE)
            if (score > alpha) {
                alpha = score
                best = candidate
            }
        }
        return best
    }

    // 玩家回合节点：最小化敌方收益
    private fun minimize(state: GameState, depth: Int, alpha: Int, beta: Int): Int {
        if (depth <= 0 || state.phase == Phase.GAME_OVER) return evaluate(state)
        var best = Int.MAX_VALUE
        for (unit in state.aliveUnits(Side.PLAYER)) {
            for (candidate in candidates(state, unit)) {
                best = minOf(best, maximize(applyEvents(state, candidate), depth - 1, alpha, beta))
                if (best <= alpha) return best
            }
        }
        return best
    }

    // 敌方回合节点：最大化敌方收益
    private fun maximize(state: GameState, depth: Int, alpha: Int, beta: Int): Int {
        if (depth <= 0 || state.phase == Phase.GAME_OVER) return evaluate(state)
        var best = Int.MIN_VALUE
        for (unit in state.aliveUnits(Side.ENEMY)) {
            for (candidate in candidates(state, unit)) {
                best = maxOf(best, minimize(applyEvents(state, candidate), depth - 1, alpha, beta))
                if (best >= beta) return best
            }
        }
        return best
    }

    /** 局面评估：从敌方视角计算双方价值差（价值 = 职业权重 + 剩余血量比例）。 */
    private fun evaluate(state: GameState): Int {
        if (state.winner == Side.PLAYER) return Int.MIN_VALUE / 4
        if (state.winner == Side.ENEMY) return Int.MAX_VALUE / 4
        fun score(side: Side): Int = state.aliveUnits(side).sumOf {
            classValue(it.unitClass) * 10 + (it.currentHp * 10) / it.stats.maxHp
        }
        return score(Side.ENEMY) - score(Side.PLAYER)
    }

    /** 生成单个单位的候选动作序列：[移动?] + [攻击?]。 */
    private fun candidates(state: GameState, unit: Unit): List<List<GameEvent>> {
        val result = mutableListOf<List<GameEvent>>()

        // 原地攻击/治疗
        attackableTargets(state, unit).forEach { result += listOf(GameEvent.Attack(unit.id, it.id)) }

        // 移动（可选）+ 攻击/治疗
        val reachable = Pathfinding.reachable(state.grid, unit.pos, unit.stats.mov, state.occupiedTiles(unit.id)).keys
        for (tile in reachable) {
            if (tile == unit.pos) continue
            val moved = unit.copy(pos = tile)
            val movedUnits = state.units.map { if (it.id == unit.id) moved else it }
            val after = attackableTargets(state.copy(units = movedUnits), moved)
            if (after.isEmpty()) {
                result += listOf(GameEvent.MoveUnit(unit.id, tile))
            } else {
                after.forEach { t -> result += listOf(GameEvent.MoveUnit(unit.id, tile), GameEvent.Attack(unit.id, t.id)) }
            }
        }
        return result
    }
}
