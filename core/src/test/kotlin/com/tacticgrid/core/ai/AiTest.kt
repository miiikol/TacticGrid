package com.tacticgrid.core.ai

import com.tacticgrid.core.GameFactory
import com.tacticgrid.core.engine.GameEvent
import com.tacticgrid.core.engine.reduce
import com.tacticgrid.core.model.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTest {

    private fun enemyTurnState() = reduce(
        reduce(GameFactory.defaultGame(), GameEvent.StartGame).state,
        GameEvent.EndTurn,
    ).state

    @Test
    fun `贪心 AI 生成合法事件序列`() {
        val state = enemyTurnState()
        val events = GreedyAI.decide(state)
        assertTrue(events.isNotEmpty())
        // 应用后仍处于敌方回合（尚未结束回合），无异常
        assertEquals(Phase.ENEMY_TURN, applyEvents(state, events).phase)
    }

    @Test
    fun `minimax AI 生成合法事件序列`() {
        val state = enemyTurnState()
        val events = MinimaxAI.decide(state)
        assertTrue(events.isNotEmpty())
        assertEquals(Phase.ENEMY_TURN, applyEvents(state, events).phase)
    }

    @Test
    fun `危险图包含敌方攻击范围`() {
        val state = enemyTurnState()
        val danger = dangerMap(state)
        assertTrue(danger.isNotEmpty())
    }
}
