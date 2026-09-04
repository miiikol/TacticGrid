package com.tacticgrid.core.serialization

import com.tacticgrid.core.GameFactory
import com.tacticgrid.core.engine.GameEvent
import com.tacticgrid.core.engine.reduce
import org.junit.Assert.assertEquals
import org.junit.Test

class SerializationTest {

    @Test
    fun `存档往返一致`() {
        val state = reduce(GameFactory.defaultGame(), GameEvent.StartGame).state
        val json = GameSerializer.toJson(state)
        val restored = GameSerializer.fromJson(json)
        assertEquals(state, restored)
    }
}
