package com.tacticgrid.core.model

import kotlinx.serialization.Serializable

/** 技能类别：伤害技能 / 治疗技能。 */
@Serializable
enum class SkillKind { DAMAGE, HEAL }

/**
 * 技能定义。
 *
 * - [power]：伤害技能的伤害加成（叠加在攻击力上），或治疗技能的固定回复量
 * - [mpCost]：消耗的魔力
 * - [range]：作用距离，1 为近战（相邻四格），>=2 按欧氏距离
 */
@Serializable
data class Skill(
    val id: String,
    val name: String,
    val desc: String,
    val kind: SkillKind,
    val power: Int,
    val mpCost: Int,
    val range: Int,
)

/**
 * 职业默认技能表。双方阵营共用同一套职业，因此技能完全相同，
 * 通过立绘（阵营）与数值区分强弱。
 */
object Skillbook {

    private val table: Map<UnitClass, List<Skill>> = mapOf(
        UnitClass.MAGE to listOf(
            Skill("fireball", "火球术", "向远处敌人投掷火球，造成额外魔法伤害", SkillKind.DAMAGE, power = 8, mpCost = 5, range = 3),
        ),
        UnitClass.WARRIOR to listOf(
            Skill("cleave", "重斩", "蓄力劈砍近身之敌，造成额外伤害", SkillKind.DAMAGE, power = 7, mpCost = 4, range = 1),
        ),
        UnitClass.TANK to listOf(
            Skill("bash", "盾击", "盾牌猛击，稳定压制贴身敌人", SkillKind.DAMAGE, power = 6, mpCost = 4, range = 1),
        ),
        UnitClass.HEALER to listOf(
            Skill("holy", "圣愈术", "以圣光为受伤友军回复大量生命", SkillKind.HEAL, power = 18, mpCost = 6, range = 2),
        ),
        UnitClass.SPEARMAN to listOf(
            Skill("pierce", "突刺", "长枪穿刺，攻击距离更远的目标", SkillKind.DAMAGE, power = 6, mpCost = 4, range = 3),
        ),
    )

    /** 返回某职业的技能列表（游戏开始时随单位生成）。 */
    fun skillsFor(cls: UnitClass): List<Skill> = table[cls] ?: emptyList()
}
