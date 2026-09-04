package com.tacticgrid.core.model

import kotlinx.serialization.Serializable

/**
 * 五类职业。双方阵营共用同一套职业，仅立绘不同：
 * 法师 / 战士 / 坦克 / 牧师 / 枪兵。
 */
@Serializable
enum class UnitClass { MAGE, WARRIOR, TANK, HEALER, SPEARMAN }

/** 单位基础数值。 */
@Serializable
data class Stats(
    val maxHp: Int,
    val atk: Int,
    val def: Int,
    val hit: Int,  // 基础命中率 0..100
    val crit: Int, // 暴击率 0..100
    val mov: Int,  // 移动力
    val rng: Int,  // 攻击/治疗距离：1 为近战，>=2 为远程
    val maxMp: Int = 0, // 最大魔力（蓝条），技能消耗 MP，蓝瓶用于回复
)

/** 战场单位。 */
@Serializable
data class Unit(
    val id: Int,
    val name: String,
    val unitClass: UnitClass,
    val side: Side,
    val pos: GridPos,
    val stats: Stats,
    val currentHp: Int,
    val isHero: Boolean = false,
    val inventory: List<ItemType> = emptyList(), // 背包：拾取后待使用的道具
    val currentMp: Int = stats.maxMp,            // 当前魔力（默认满蓝）
    val skills: List<Skill> = emptyList(),       // 职业技能（见 Skillbook）
) {
    val isAlive: Boolean get() = currentHp > 0

    /** 近战判定：攻击距离为 1 时按相邻四格计算。 */
    val isMelee: Boolean get() = stats.rng <= 1
}
