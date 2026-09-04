package com.tacticgrid.core.model

import kotlinx.serialization.Serializable

/** 方格地图坐标。x 为列、y 为行，原点在左上角。 */
@Serializable
data class GridPos(val x: Int, val y: Int) {
    operator fun plus(o: GridPos) = GridPos(x + o.x, y + o.y)
    operator fun minus(o: GridPos) = GridPos(x - o.x, y - o.y)

    /** 曼哈顿距离，用于近战范围与寻路启发函数。 */
    fun manhattanTo(o: GridPos): Int = kotlin.math.abs(x - o.x) + kotlin.math.abs(y - o.y)
}
