package com.tacticgrid.core.engine

import com.tacticgrid.core.model.ItemType
import com.tacticgrid.core.model.Skill
import com.tacticgrid.core.model.Unit
import com.tacticgrid.core.model.UnitClass
import kotlin.random.Random

/**
 * 职业克制倍率（环形）：法师→坦克→战士→枪兵→法师；牧师不参与克制。
 */
private val ADVANTAGE: Map<UnitClass, Map<UnitClass, Double>> = mapOf(
    UnitClass.MAGE to mapOf(UnitClass.TANK to 1.2),
    UnitClass.TANK to mapOf(UnitClass.WARRIOR to 1.2),
    UnitClass.WARRIOR to mapOf(UnitClass.SPEARMAN to 1.2),
    UnitClass.SPEARMAN to mapOf(UnitClass.MAGE to 1.2),
)

/** 攻击方对目标职业的克制倍率，无克制关系返回 1.0。 */
fun advantage(attacker: UnitClass, defender: UnitClass): Double =
    ADVANTAGE[attacker]?.get(defender) ?: 1.0

/** 一次攻击的结算结果。 */
data class CombatResult(
    val miss: Boolean,
    val crit: Boolean,
    val damage: Int,
)

/**
 * 伤害公式（纯函数）：(ATK - DEF) × 克制 × 暴击，保底 1。
 * 命中与暴击由传入的 [Random] 决定，调用方传入确定种子即可复现。
 */
fun computeCombat(attacker: Unit, defender: Unit, random: Random): CombatResult {
    if (random.nextInt(100) >= attacker.stats.hit) {
        return CombatResult(miss = true, crit = false, damage = 0)
    }

    val base = (attacker.stats.atk - defender.stats.def).coerceAtLeast(1)
    val crit = random.nextInt(100) < attacker.stats.crit
    val raw = base * advantage(attacker.unitClass, defender.unitClass) * (if (crit) 2.0 else 1.0)
    return CombatResult(miss = false, crit = crit, damage = raw.toInt().coerceAtLeast(1))
}

/** 治疗量（纯函数）：治疗者的 atk 视为治疗强度。 */
fun computeHeal(healer: Unit): Int = healer.stats.atk.coerceAtLeast(1)

/**
 * 技能伤害（纯函数）：与普攻相同的命中/暴击规则，
 * 但将 [Skill.power] 叠加到攻击力上（魔法/武技蓄力），克制倍率依旧生效。
 */
fun computeSkill(attacker: Unit, defender: Unit, skill: Skill, random: Random): CombatResult {
    if (random.nextInt(100) >= attacker.stats.hit) {
        return CombatResult(miss = true, crit = false, damage = 0)
    }
    val effAtk = attacker.stats.atk + skill.power
    val base = (effAtk - defender.stats.def).coerceAtLeast(1)
    val crit = random.nextInt(100) < attacker.stats.crit
    val raw = base * advantage(attacker.unitClass, defender.unitClass) * (if (crit) 2.0 else 1.0)
    return CombatResult(miss = false, crit = crit, damage = raw.toInt().coerceAtLeast(1))
}

/** 技能治疗量（纯函数）：治疗技能按固定数值回复，不受攻击力影响。 */
fun skillHeal(skill: Skill): Int = skill.power.coerceAtLeast(1)

/**
 * 道具效果（纯函数）：作用于目标单位。
 * 血瓶回血、蓝瓶回复魔力（技能系统的续航）、毒瓶扣血（毒瓶作为投掷武器可致死）。
 */
fun applyItem(target: Unit, type: ItemType): Unit = when (type) {
    ItemType.HP_POTION_LARGE -> target.copy(currentHp = target.stats.maxHp)
    ItemType.HP_POTION_SMALL -> target.copy(currentHp = (target.currentHp + 15).coerceAtMost(target.stats.maxHp))
    ItemType.MP_POTION_LARGE -> target.copy(currentMp = target.stats.maxMp)
    ItemType.MP_POTION_SMALL -> target.copy(currentMp = (target.currentMp + 8).coerceAtMost(target.stats.maxMp))
    ItemType.POISON_LARGE -> target.copy(currentHp = (target.currentHp - 20).coerceAtLeast(0))
    ItemType.POISON_SMALL -> target.copy(currentHp = (target.currentHp - 10).coerceAtLeast(0))
}
