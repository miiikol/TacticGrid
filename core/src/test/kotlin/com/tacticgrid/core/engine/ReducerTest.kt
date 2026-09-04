package com.tacticgrid.core.engine

import com.tacticgrid.core.GameFactory
import com.tacticgrid.core.model.GameState
import com.tacticgrid.core.model.Grid
import com.tacticgrid.core.model.GridPos
import com.tacticgrid.core.model.Item
import com.tacticgrid.core.model.ItemType
import com.tacticgrid.core.model.Phase
import com.tacticgrid.core.model.Side
import com.tacticgrid.core.model.Skillbook
import com.tacticgrid.core.model.Stats
import com.tacticgrid.core.model.Unit
import com.tacticgrid.core.model.UnitClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducerTest {

    @Test
    fun `开始游戏进入玩家回合`() {
        val s0 = GameFactory.defaultGame()
        assertEquals(Phase.START, s0.phase)
        val s1 = reduce(s0, GameEvent.StartGame).state
        assertEquals(Phase.PLAYER_TURN, s1.phase)
        assertEquals(1, s1.turnNumber)
    }

    @Test
    fun `选中单位计算移动与攻击范围`() {
        val s = reduce(GameFactory.defaultGame(), GameEvent.StartGame).state
        val sel = reduce(s, GameEvent.SelectUnit(1)).state
        assertEquals(Phase.UNIT_SELECTED, sel.phase)
        assertEquals(1, sel.currentUnitId)
        assertTrue(sel.reachableTiles.isNotEmpty())
    }

    @Test
    fun `移动单位更新位置并标记已移动`() {
        val s = reduce(GameFactory.defaultGame(), GameEvent.StartGame).state
        val sel = reduce(s, GameEvent.SelectUnit(1)).state
        val target = sel.reachableTiles.first { it != sel.unit(1)!!.pos }
        val moved = reduce(sel, GameEvent.MoveUnit(1, target)).state
        assertEquals(target, moved.unit(1)!!.pos)
        assertTrue(1 in moved.movedIds)
    }

    @Test
    fun `攻击降低目标生命并记录行动`() {
        val attacker = Unit(1, "战士", UnitClass.WARRIOR, Side.PLAYER, GridPos(0, 0), Stats(30, 12, 8, 100, 0, 4, 1), 30)
        val defender = Unit(2, "敌", UnitClass.WARRIOR, Side.ENEMY, GridPos(0, 1), Stats(30, 10, 8, 90, 0, 4, 1), 30)
        val result = reduce(combatState(attacker, defender), GameEvent.Attack(1, 2))
        assertTrue(result.state.unit(2)!!.currentHp < 30)
        assertTrue(1 in result.state.actedIds)
        assertTrue(result.effects.any { it is GameEffect.Attacked })
    }

    @Test
    fun `全歼敌军判胜`() {
        val attacker = Unit(1, "战士", UnitClass.WARRIOR, Side.PLAYER, GridPos(0, 0), Stats(30, 50, 8, 100, 0, 4, 1), 30)
        val defender = Unit(2, "敌", UnitClass.WARRIOR, Side.ENEMY, GridPos(0, 1), Stats(10, 0, 0, 0, 0, 1, 1), 10)
        val result = reduce(combatState(attacker, defender), GameEvent.Attack(1, 2))
        assertEquals(Phase.GAME_OVER, result.state.phase)
        assertEquals(Side.PLAYER, result.state.winner)
        assertTrue(result.effects.any { it is GameEffect.GameOver && it.playerWon })
    }

    @Test
    fun `主角阵亡判负`() {
        val hero = Unit(1, "主角", UnitClass.WARRIOR, Side.PLAYER, GridPos(0, 0), Stats(10, 5, 0, 100, 0, 1, 1), 1, isHero = true)
        val enemy = Unit(2, "敌", UnitClass.WARRIOR, Side.ENEMY, GridPos(0, 1), Stats(30, 20, 0, 100, 0, 4, 1), 30)
        val s = combatState(hero, enemy).copy(
            phase = Phase.ENEMY_TURN,
            currentUnitId = null,
        )
        val result = reduce(s, GameEvent.Attack(2, 1))
        assertEquals(Phase.GAME_OVER, result.state.phase)
        assertEquals(Side.ENEMY, result.state.winner)
    }

    @Test
    fun `治疗者恢复友军生命`() {
        val healer = Unit(1, "治疗", UnitClass.HEALER, Side.PLAYER, GridPos(0, 0), Stats(20, 8, 5, 100, 0, 3, 2), 20)
        val ally = Unit(2, "友", UnitClass.WARRIOR, Side.PLAYER, GridPos(0, 1), Stats(30, 10, 8, 90, 0, 4, 1), 10)
        val result = reduce(combatState(healer, ally), GameEvent.Attack(1, 2))
        assertTrue(result.state.unit(2)!!.currentHp > 10)
        assertTrue(result.effects.any { it is GameEffect.Healed })
    }

    @Test
    fun `结束敌方回合递增回合并回到玩家回合`() {
        val s = reduce(GameFactory.defaultGame(), GameEvent.StartGame).state
        val enemy = reduce(s, GameEvent.EndTurn).state
        assertEquals(Phase.ENEMY_TURN, enemy.phase)
        val next = reduce(enemy, GameEvent.EndTurn).state
        assertEquals(Phase.PLAYER_TURN, next.phase)
        assertEquals(2, next.turnNumber)
    }

    @Test
    fun `回合超限判负`() {
        val cur = reduce(GameFactory.defaultGame(), GameEvent.StartGame).state
            .copy(phase = Phase.ENEMY_TURN, turnNumber = 10, maxTurns = 10)
        val next = reduce(cur, GameEvent.EndTurn).state
        assertEquals(Phase.GAME_OVER, next.phase)
        assertEquals(Side.ENEMY, next.winner)
    }

    @Test
    fun `移动拾取道具进入背包`() {
        val hero = Unit(1, "战士", UnitClass.WARRIOR, Side.PLAYER, GridPos(0, 0), Stats(30, 12, 8, 100, 0, 4, 1), 30)
        val s = GameState(
            phase = Phase.PLAYER_TURN,
            grid = Grid.open(4, 4),
            units = listOf(hero),
            currentUnitId = null,
            reachableTiles = emptySet(),
            attackableTiles = emptySet(),
            turnNumber = 1,
            maxTurns = 10,
            winner = null,
            message = "",
            rngSeed = 1,
            rngCounter = 0,
            movedIds = emptySet(),
            actedIds = emptySet(),
            items = listOf(Item(100, ItemType.MP_POTION_SMALL, GridPos(1, 0))),
        )
        val sel = reduce(s, GameEvent.SelectUnit(1)).state
        val result = reduce(sel, GameEvent.MoveUnit(1, GridPos(1, 0)))

        assertEquals(12, result.state.unit(1)!!.stats.atk) // 拾取不立即生效
        assertTrue(ItemType.MP_POTION_SMALL in result.state.unit(1)!!.inventory) // 进背包
        assertTrue(result.state.items.isEmpty())
        assertTrue(result.effects.any { it is GameEffect.ItemPicked })
    }

    @Test
    fun `使用血瓶治疗友军`() {
        val user = Unit(1, "战士", UnitClass.WARRIOR, Side.PLAYER, GridPos(0, 0), Stats(30, 12, 8, 100, 0, 4, 1), 30, inventory = listOf(ItemType.HP_POTION_SMALL))
        val ally = Unit(2, "友", UnitClass.WARRIOR, Side.PLAYER, GridPos(0, 1), Stats(30, 10, 8, 90, 0, 4, 1), 10)
        val result = reduce(combatState(user, ally), GameEvent.UseItem(1, ItemType.HP_POTION_SMALL, 2))

        assertEquals(25, result.state.unit(2)!!.currentHp) // 10 + 15
        assertTrue(result.state.unit(1)!!.inventory.isEmpty()) // 道具被消耗
        assertTrue(result.effects.any { it is GameEffect.ItemUsed })
    }

    @Test
    fun `法师消耗魔力施放伤害技能`() {
        val mage = Unit(
            1, "魔法师", UnitClass.MAGE, Side.PLAYER, GridPos(0, 0),
            Stats(20, 15, 3, 100, 0, 3, 3, 25), 20,
            currentMp = 10, skills = Skillbook.skillsFor(UnitClass.MAGE),
        )
        val golem = Unit(
            2, "石像魔像", UnitClass.TANK, Side.ENEMY, GridPos(0, 3),
            Stats(30, 5, 12, 80, 0, 1, 1, 12), 30,
        )
        val sel = reduce(combatState(mage, golem), GameEvent.SelectSkill("fireball")).state
        assertEquals("fireball", sel.usingSkillId)
        assertTrue(GridPos(0, 3) in sel.skillTargets)

        val cast = reduce(sel, GameEvent.CastSkill(1, "fireball", 2)).state
        assertEquals(5, cast.unit(1)!!.currentMp)          // 10 - 5 消耗魔力
        assertTrue(cast.unit(2)!!.currentHp < 30)          // 受到伤害
        assertTrue(1 in cast.actedIds)                     // 记为一次行动
        assertTrue(cast.usingSkillId == null)
    }

    @Test
    fun `魔力不足无法施放技能`() {
        val mage = Unit(
            1, "魔法师", UnitClass.MAGE, Side.PLAYER, GridPos(0, 0),
            Stats(20, 15, 3, 100, 0, 3, 3, 25), 20,
            currentMp = 3, skills = Skillbook.skillsFor(UnitClass.MAGE),
        )
        val golem = Unit(
            2, "石像魔像", UnitClass.TANK, Side.ENEMY, GridPos(0, 3),
            Stats(30, 5, 12, 80, 0, 1, 1, 12), 30,
        )
        val result = reduce(combatState(mage, golem), GameEvent.SelectSkill("fireball"))
        assertEquals(null, result.state.usingSkillId)
        assertTrue(result.state.message.contains("魔力不足"))
    }

    @Test
    fun `牧师消耗魔力施放治疗技能`() {
        val healer = Unit(
            1, "牧师", UnitClass.HEALER, Side.PLAYER, GridPos(0, 0),
            Stats(22, 7, 5, 100, 0, 3, 2, 20), 22,
            currentMp = 10, skills = Skillbook.skillsFor(UnitClass.HEALER),
        )
        val ally = Unit(
            2, "受创友军", UnitClass.WARRIOR, Side.PLAYER, GridPos(0, 1),
            Stats(30, 10, 8, 90, 0, 4, 1, 15), 10,
        )
        val sel = reduce(combatState(healer, ally), GameEvent.SelectSkill("holy")).state
        assertTrue(GridPos(0, 1) in sel.skillTargets)

        val cast = reduce(sel, GameEvent.CastSkill(1, "holy", 2)).state
        assertEquals(4, cast.unit(1)!!.currentMp)  // 10 - 6
        assertEquals(28, cast.unit(2)!!.currentHp) // 10 + 18 圣愈术
        assertTrue(1 in cast.actedIds)
    }

    private fun combatState(attacker: Unit, target: Unit): GameState = GameState(
        phase = Phase.UNIT_SELECTED,
        grid = Grid.open(4, 4),
        units = listOf(attacker, target),
        currentUnitId = attacker.id,
        reachableTiles = emptySet(),
        attackableTiles = setOf(target.pos),
        turnNumber = 1,
        maxTurns = 10,
        winner = null,
        message = "",
        rngSeed = 1,
        rngCounter = 0,
        movedIds = emptySet(),
        actedIds = emptySet(),
    )
}
