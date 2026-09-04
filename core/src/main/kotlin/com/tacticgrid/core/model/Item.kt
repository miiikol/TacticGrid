package com.tacticgrid.core.model

import kotlinx.serialization.Serializable

/**
 * 可交互道具：大/小 × 血瓶/蓝瓶/毒瓶 共 6 种。
 * 单位移动落点拾取，效果见 [com.tacticgrid.core.engine.applyItem]。
 */
@Serializable
enum class ItemType {
    HP_POTION_LARGE,   // 大血瓶：回满
    HP_POTION_SMALL,   // 小血瓶：回少量
    MP_POTION_LARGE,   // 大蓝瓶：魔力回满（配合技能才有意义）
    MP_POTION_SMALL,   // 小蓝瓶：回复少量魔力
    POISON_LARGE,      // 大毒瓶：扣血多（不致死）
    POISON_SMALL,      // 小毒瓶：扣血少（不致死）
}

/** 地图上的可拾取道具。 */
@Serializable
data class Item(val id: Int, val type: ItemType, val pos: GridPos)
