package com.tacticgrid.core.model

import kotlinx.serialization.Serializable

/** 翻转标志编码（bit0=水平 H，bit1=垂直 V，bit2=对角 D）。 */
object Flip {
    const val NONE = 0
    const val HORIZONTAL = 1
    const val VERTICAL = 2
    const val DIAGONAL = 4
}

/**
 * 方格地图。
 *
 * - [tiles] 每格渲染瓦片索引（对应 tilemap.png 的 0..131，来自 Tiled 导出）
 * - [blocked] 每格是否阻挡（true=不可通行）
 * - [flips] 每格翻转编码（bit0=水平 / bit1=垂直 / bit2=对角），用于还原 Tiled 里的旋转
 */
@Serializable
data class Grid(
    val width: Int,
    val height: Int,
    val tiles: List<Int>,
    val blocked: List<Boolean>,
    val flips: List<Int> = emptyList(),
) {
    init {
        require(tiles.size == width * height) { "tiles 数量与宽高不符" }
        require(blocked.size == width * height) { "blocked 数量与宽高不符" }
    }

    fun contains(pos: GridPos): Boolean = pos.x in 0 until width && pos.y in 0 until height

    fun tileId(pos: GridPos): Int = tiles[pos.y * width + pos.x]

    fun isBlocked(pos: GridPos): Boolean = blocked[pos.y * width + pos.x]

    fun flipAt(pos: GridPos): Int =
        if (flips.size == width * height) flips[pos.y * width + pos.x] else Flip.NONE

    companion object {
        /** 全开放地图（无障碍），渲染统一用 [tileId] 瓦片，便于测试与快速开局。 */
        fun open(width: Int, height: Int, tileId: Int = 0): Grid =
            Grid(width, height, List(width * height) { tileId }, List(width * height) { false })
    }
}
