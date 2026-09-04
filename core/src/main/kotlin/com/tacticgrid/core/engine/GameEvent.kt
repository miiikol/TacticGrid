package com.tacticgrid.core.engine

import com.tacticgrid.core.model.GridPos
import com.tacticgrid.core.model.ItemType

/** 用户/AI 发起的游戏事件，是 reducer 的唯一输入。 */
sealed interface GameEvent {
    object StartGame : GameEvent
    data class SelectUnit(val id: Int) : GameEvent
    data class MoveUnit(val unitId: Int, val target: GridPos) : GameEvent
    data class Attack(val attackerId: Int, val targetId: Int) : GameEvent
    object CancelSelection : GameEvent
    object EndTurn : GameEvent
    data class SelectItem(val itemType: ItemType) : GameEvent     // 从背包选择道具
    object CancelItem : GameEvent                                  // 取消使用道具
    data class UseItem(val userId: Int, val itemType: ItemType, val targetId: Int) : GameEvent // 对目标使用道具
    data class SelectSkill(val skillId: String) : GameEvent        // 准备施放技能（进入选目标）
    object CancelSkill : GameEvent                                 // 取消施放技能
    data class CastSkill(val userId: Int, val skillId: String, val targetId: Int) : GameEvent // 对目标施放技能
}
