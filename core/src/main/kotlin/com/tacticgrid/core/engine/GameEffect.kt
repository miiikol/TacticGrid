package com.tacticgrid.core.engine

import com.tacticgrid.core.model.GridPos
import com.tacticgrid.core.model.ItemType

/** reducer 产生的表现副作用，UI 据此播放动画/音效，不参与状态计算。 */
sealed interface GameEffect {
    data class Moved(val unitId: Int, val from: GridPos, val to: GridPos, val path: List<GridPos>) : GameEffect
    data class Attacked(val attackerId: Int, val targetId: Int, val damage: Int, val crit: Boolean, val miss: Boolean) : GameEffect
    data class Healed(val healerId: Int, val targetId: Int, val amount: Int) : GameEffect
    data class UnitDied(val unitId: Int) : GameEffect
    data class GameOver(val playerWon: Boolean) : GameEffect
    data class ItemPicked(val unitId: Int, val itemId: Int, val type: ItemType) : GameEffect
    data class ItemUsed(val userId: Int, val targetId: Int, val type: ItemType) : GameEffect
}
