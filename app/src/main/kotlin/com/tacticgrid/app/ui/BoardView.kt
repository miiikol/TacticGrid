package com.tacticgrid.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import com.tacticgrid.core.model.Flip
import com.tacticgrid.core.model.GameState
import com.tacticgrid.core.model.GridPos
import com.tacticgrid.core.model.SkillKind

/**
 * 棋盘绘制：按 Grid.tiles 的渲染索引画瓦片（来自 Tiled 导出）+ 移动/攻击/道具目标高亮 + 网格线。
 * 单位与道具本体作为独立 Composable 叠加在上层，以便独立做动画。
 */
@Composable
fun Board(
    state: GameState,
    cellSize: Float,
    art: GameArt? = null,
    modifier: Modifier = Modifier,
    onTap: (GridPos) -> Unit,
) {
    val width = state.grid.width
    val height = state.grid.height

    Canvas(
        modifier = modifier.pointerInput(state) {
            detectTapGestures { offset ->
                val pos = GridPos((offset.x / cellSize).toInt(), (offset.y / cellSize).toInt())
                if (state.grid.contains(pos)) onTap(pos)
            }
        }
    ) {
        // 地图瓦片
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pos = GridPos(x, y)
                val img = art?.tile(state.grid.tileId(pos))
                if (img != null) {
                    drawTile(img, state.grid.flipAt(pos), x, y, cellSize)
                } else {
                    // 未加载到瓦片时回退为地牢底色
                    drawRect(
                        color = Color(0xFF3A3A3A),
                        topLeft = Offset(x * cellSize, y * cellSize),
                        size = Size(cellSize, cellSize),
                    )
                }
            }
        }
        // 移动范围（蓝）、攻击范围（红）、道具目标（黄）、技能目标（伤害橙/治疗绿）
        state.reachableTiles.forEach { p ->
            drawRect(Color(0x553399FF), Offset(p.x * cellSize, p.y * cellSize), Size(cellSize, cellSize))
        }
        state.attackableTiles.forEach { p ->
            drawRect(Color(0x55FF4444), Offset(p.x * cellSize, p.y * cellSize), Size(cellSize, cellSize))
        }
        state.itemTargets.forEach { p ->
            drawRect(Color(0x66FFC107), Offset(p.x * cellSize, p.y * cellSize), Size(cellSize, cellSize))
        }
        if (state.usingSkillId != null) {
            val skill = state.currentUnit()?.skills?.firstOrNull { it.id == state.usingSkillId }
            val color = if (skill?.kind == SkillKind.HEAL) Color(0x5543A047) else Color(0x66FF9800)
            state.skillTargets.forEach { p ->
                drawRect(color, Offset(p.x * cellSize, p.y * cellSize), Size(cellSize, cellSize))
            }
        }
        // 网格线
        val line = Color.Black.copy(alpha = 0.15f)
        for (x in 0..width) {
            drawLine(line, Offset(x * cellSize, 0f), Offset(x * cellSize, height * cellSize), 1f)
        }
        for (y in 0..height) {
            drawLine(line, Offset(0f, y * cellSize), Offset(width * cellSize, y * cellSize), 1f)
        }
    }
}

/** 按翻转编码绘制瓦片，还原 Tiled 里的旋转/镜像。 */
private fun DrawScope.drawTile(img: ImageBitmap, flip: Int, x: Int, y: Int, cellSize: Float) {
    val size = cellSize.roundToInt()
    val dstOffset = IntOffset((x * cellSize).roundToInt(), (y * cellSize).roundToInt())
    val dstSize = IntSize(size, size)
    val pivot = Offset(dstOffset.x + size / 2f, dstOffset.y + size / 2f)

    fun draw() {
        drawImage(
            image = img,
            srcSize = IntSize(16, 16),
            dstOffset = dstOffset,
            dstSize = dstSize,
            filterQuality = FilterQuality.None, // 像素风保持锐利
        )
    }

    when (flip) {
        Flip.HORIZONTAL -> scale(-1f, 1f, pivot) { draw() }                              // 水平翻转
        Flip.VERTICAL -> scale(1f, -1f, pivot) { draw() }                                // 垂直翻转
        Flip.HORIZONTAL or Flip.VERTICAL -> rotate(180f, pivot) { draw() }               // 180° 旋转
        Flip.DIAGONAL -> rotate(90f, pivot) { scale(1f, -1f, pivot) { draw() } }         // 对角（转置）
        Flip.DIAGONAL or Flip.HORIZONTAL -> rotate(90f, pivot) { draw() }                // 顺时针 90°
        Flip.DIAGONAL or Flip.VERTICAL -> rotate(-90f, pivot) { draw() }                 // 逆时针 90°
        Flip.DIAGONAL or Flip.HORIZONTAL or Flip.VERTICAL ->
            rotate(90f, pivot) { scale(-1f, 1f, pivot) { draw() } }                      // 副对角翻转
        else -> draw()
    }
}
