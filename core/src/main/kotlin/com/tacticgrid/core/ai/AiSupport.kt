package com.tacticgrid.core.ai

import com.tacticgrid.core.engine.GameEvent
import com.tacticgrid.core.engine.reduce
import com.tacticgrid.core.geometry.inAttackRange
import com.tacticgrid.core.model.GameState
import com.tacticgrid.core.model.GridPos
import com.tacticgrid.core.model.Side
import com.tacticgrid.core.model.Unit
import com.tacticgrid.core.model.UnitClass

/** 危险图：敌方单位当前可攻击到的所有格子集合，供 AI 规避与玩家提示。 */
fun dangerMap(state: GameState): Set<GridPos> {
    val result = mutableSetOf<GridPos>()
    for (enemy in state.aliveUnits(Side.ENEMY)) {
        if (enemy.unitClass == UnitClass.HEALER) continue // 治疗者不构成威胁
        for (dy in -enemy.stats.rng..enemy.stats.rng) {
            for (dx in -enemy.stats.rng..enemy.stats.rng) {
                val p = GridPos(enemy.pos.x + dx, enemy.pos.y + dy)
                if (state.grid.contains(p) && inAttackRange(enemy.pos, p, enemy.stats.rng, enemy.isMelee)) {
                    result.add(p)
                }
            }
        }
    }
    return result
}

/** 当前单位可作用的合法目标（治疗者→受伤友军，其余→敌方）。 */
fun attackableTargets(state: GameState, actor: Unit): List<Unit> =
    state.units
        .filter { it.isAlive && it.id != actor.id }
        .filter {
            if (actor.unitClass == UnitClass.HEALER) it.side == actor.side && it.currentHp < it.stats.maxHp
            else it.side != actor.side
        }
        .filter { inAttackRange(actor.pos, it.pos, actor.stats.rng, actor.isMelee) }

/** 单位价值权重，用于 AI 选择集火目标。 */
fun classValue(cls: UnitClass): Int = when (cls) {
    UnitClass.HEALER -> 3
    UnitClass.MAGE, UnitClass.SPEARMAN -> 2
    UnitClass.WARRIOR, UnitClass.TANK -> 1
}

/** 依次应用一组事件，返回最终状态。 */
fun applyEvents(state: GameState, events: List<GameEvent>): GameState =
    events.fold(state) { acc, e -> reduce(acc, e).state }
