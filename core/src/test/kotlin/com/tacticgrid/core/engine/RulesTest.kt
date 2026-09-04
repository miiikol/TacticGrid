package com.tacticgrid.core.engine

import com.tacticgrid.core.model.GridPos
import com.tacticgrid.core.model.ItemType
import com.tacticgrid.core.model.Side
import com.tacticgrid.core.model.Skill
import com.tacticgrid.core.model.SkillKind
import com.tacticgrid.core.model.Stats
import com.tacticgrid.core.model.Unit
import com.tacticgrid.core.model.UnitClass
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    // 战士克枪兵（1.2 倍）
    private val attacker = Unit(
        1, "战士", UnitClass.WARRIOR, Side.PLAYER, GridPos(0, 0),
        Stats(30, 12, 8, 100, 0, 4, 1), 30,
    )
    private val defender = Unit(
        2, "枪兵", UnitClass.SPEARMAN, Side.ENEMY, GridPos(0, 1),
        Stats(26, 11, 6, 90, 5, 4, 2), 26,
    )

    @Test
    fun `命中 100 且暴击 0 时伤害为克制后基础值`() {
        val r = computeCombat(attacker, defender, Random(0))
        assertFalse(r.miss)
        assertFalse(r.crit)
        // base = 12 - 6 = 6 → 6 * 1.2 = 7
        assertEquals(7, r.damage)
    }

    @Test
    fun `命中 0 时必未命中`() {
        val a = attacker.copy(stats = attacker.stats.copy(hit = 0))
        val r = computeCombat(a, defender, Random(0))
        assertTrue(r.miss)
        assertEquals(0, r.damage)
    }

    @Test
    fun `暴击 100 时必暴击且伤害翻倍`() {
        val a = attacker.copy(stats = attacker.stats.copy(crit = 100))
        val r = computeCombat(a, defender, Random(0))
        assertTrue(r.crit)
        assertEquals((6 * 1.2 * 2).toInt(), r.damage) // 14
    }

    @Test
    fun `克制倍率正确`() {
        assertEquals(1.2, advantage(UnitClass.WARRIOR, UnitClass.SPEARMAN), 1e-9)
        assertEquals(1.2, advantage(UnitClass.MAGE, UnitClass.TANK), 1e-9)
        assertEquals(1.2, advantage(UnitClass.SPEARMAN, UnitClass.MAGE), 1e-9)
        assertEquals(1.0, advantage(UnitClass.WARRIOR, UnitClass.WARRIOR), 1e-9)
        assertEquals(1.0, advantage(UnitClass.MAGE, UnitClass.HEALER), 1e-9)
    }

    @Test
    fun `相同种子结果可复现`() {
        val a = attacker.copy(stats = attacker.stats.copy(hit = 80, crit = 20))
        val r1 = computeCombat(a, defender, Random(1234))
        val r2 = computeCombat(a, defender, Random(1234))
        assertEquals(r1, r2)
    }

    @Test
    fun `道具效果正确`() {
        // 满血单位：血瓶封顶、毒瓶扣血
        assertEquals(30, applyItem(attacker, ItemType.HP_POTION_LARGE).currentHp)
        assertEquals(30, applyItem(attacker, ItemType.HP_POTION_SMALL).currentHp) // 30+15 封顶
        assertEquals(10, applyItem(attacker, ItemType.POISON_LARGE).currentHp)   // 30-20
        assertEquals(20, applyItem(attacker, ItemType.POISON_SMALL).currentHp)   // 30-10

        // 低血单位：毒瓶作为投掷武器可致死
        val low = attacker.copy(currentHp = 5)
        assertEquals(0, applyItem(low, ItemType.POISON_LARGE).currentHp)
    }

    @Test
    fun `蓝瓶恢复魔力而非攻击力`() {
        // 施法者魔力为 0，蓝瓶应回复蓝条而不改变攻击力
        val caster = attacker.copy(
            stats = attacker.stats.copy(maxMp = 20),
            currentMp = 0,
        )
        assertEquals(20, applyItem(caster, ItemType.MP_POTION_LARGE).currentMp)  // 大蓝瓶回满
        assertEquals(8, applyItem(caster, ItemType.MP_POTION_SMALL).currentMp)   // 小蓝瓶 +8
        assertEquals(12, applyItem(caster, ItemType.MP_POTION_SMALL).stats.atk)  // 攻击不变

        // 满蓝单位使用蓝瓶不会溢出
        val full = attacker.copy(stats = attacker.stats.copy(maxMp = 10), currentMp = 10)
        assertEquals(10, applyItem(full, ItemType.MP_POTION_SMALL).currentMp)
    }

    @Test
    fun `技能伤害叠加技能威力并受命中影响`() {
        val cleave = Skill("cleave", "重斩", "", SkillKind.DAMAGE, power = 7, mpCost = 4, range = 1)
        // effAtk = 12 + 7 = 19；base = 19 - 6 = 13；战士克枪兵 1.2 → 15.6 → 15
        val r = computeSkill(attacker, defender, cleave, Random(0))
        assertFalse(r.miss)
        assertEquals(15, r.damage)

        // 命中 0 时技能必 miss
        val blind = attacker.copy(stats = attacker.stats.copy(hit = 0))
        assertTrue(computeSkill(blind, defender, cleave, Random(0)).miss)
    }

    @Test
    fun `技能治疗量固定不受攻击力影响`() {
        val holy = Skill("holy", "圣愈术", "", SkillKind.HEAL, power = 18, mpCost = 6, range = 2)
        assertEquals(18, skillHeal(holy))
    }
}
