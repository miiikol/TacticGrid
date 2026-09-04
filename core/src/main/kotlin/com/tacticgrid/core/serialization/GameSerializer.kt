package com.tacticgrid.core.serialization

import com.tacticgrid.core.model.GameState
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 存档序列化：基于 kotlinx.serialization，将整个 GameState 转成 JSON。 */
object GameSerializer {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun toJson(state: GameState): String = json.encodeToString(state)

    fun fromJson(text: String): GameState = json.decodeFromString(text)
}
