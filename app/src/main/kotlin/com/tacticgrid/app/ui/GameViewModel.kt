package com.tacticgrid.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tacticgrid.core.GameFactory
import com.tacticgrid.core.ai.GreedyAI
import com.tacticgrid.core.ai.MinimaxAI
import com.tacticgrid.core.engine.GameEffect
import com.tacticgrid.core.engine.GameEvent
import com.tacticgrid.core.engine.reduce
import com.tacticgrid.core.model.GameState
import com.tacticgrid.core.model.Phase
import com.tacticgrid.core.serialization.GameSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** AI 难度档位。 */
enum class AiLevel { GREEDY, MINIMAX }

/**
 * UI 层唯一的逻辑入口：持有状态流，把用户操作转成 GameEvent 交给 core 的 reducer。
 * 手动依赖注入，无 Hilt；通过 AndroidViewModel 获取 filesDir 做本地存档。
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(GameFactory.defaultGame())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _aiLevel = MutableStateFlow(AiLevel.GREEDY)
    val aiLevel: StateFlow<AiLevel> = _aiLevel.asStateFlow()

    // 敌方回合是否正在驱动 AI（读档/重开/结束回合等在忙时禁用，避免状态竞争）
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // 表现副作用流，UI 据此播放动画；不影响游戏状态
    private val _effects = MutableSharedFlow<GameEffect>(extraBufferCapacity = 64)
    val effects = _effects.asSharedFlow()

    private val saveFile = File(getApplication<Application>().filesDir, "save.json")

    init {
        dispatch(GameEvent.StartGame)
    }

    fun dispatch(event: GameEvent) {
        // 玩家主动结束回合 → 进入敌方回合并逐帧驱动 AI 动作
        if (event is GameEvent.EndTurn && _state.value.phase in setOf(Phase.PLAYER_TURN, Phase.UNIT_SELECTED)) {
            viewModelScope.launch { runEnemyTurn() }
            return
        }
        applyEvent(event)
    }

    fun restart() {
        _state.value = GameFactory.defaultGame()
        applyEvent(GameEvent.StartGame)
    }

    fun setAiLevel(level: AiLevel) {
        _aiLevel.value = level
    }

    fun save() {
        runCatching {
            saveFile.writeText(GameSerializer.toJson(_state.value))
        }.onSuccess {
            _state.value = _state.value.copy(message = "已保存当前进度")
        }.onFailure {
            _state.value = _state.value.copy(message = "保存失败")
        }
    }

    fun load() {
        if (_busy.value) {
            _state.value = _state.value.copy(message = "敌方回合中，无法读档")
            return
        }
        if (!saveFile.exists()) {
            _state.value = _state.value.copy(message = "还没有存档")
            return
        }
        runCatching {
            GameSerializer.fromJson(saveFile.readText())
        }.onSuccess { loaded ->
            _state.value = loaded.copy(message = "读档完成")
        }.onFailure {
            _state.value = _state.value.copy(message = "存档已损坏，无法读取")
        }
    }

    private fun applyEvent(event: GameEvent) {
        val result = reduce(_state.value, event)
        _state.value = result.state
        result.effects.forEach { _effects.tryEmit(it) }
    }

    private suspend fun runEnemyTurn() {
        _busy.value = true
        try {
            applyEvent(GameEvent.EndTurn) // → ENEMY_TURN
            // AI 决策放在后台线程，避免极小极大阻塞 UI
            val events = withContext(Dispatchers.Default) {
                when (_aiLevel.value) {
                    AiLevel.GREEDY -> GreedyAI.decide(_state.value)
                    AiLevel.MINIMAX -> MinimaxAI.decide(_state.value)
                }
            }
            for (e in events) {
                applyEvent(e)
                delay(500) // 留出移动/攻击动画时间
            }
            applyEvent(GameEvent.EndTurn) // → PLAYER_TURN
        } finally {
            _busy.value = false
        }
    }
}
