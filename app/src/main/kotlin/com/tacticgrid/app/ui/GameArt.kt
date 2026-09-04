package com.tacticgrid.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tacticgrid.core.model.ItemType
import com.tacticgrid.core.model.Side
import com.tacticgrid.core.model.UnitClass

/**
 * 美术映射表：把游戏语义（职业/道具）映射到 Kenney「Tiny Dungeon」瓦片编号。
 * 编号等于 `Tiles/tile_00XX.png` 中的 XX（0..131）。
 */
object ArtMap {

    // 城堡方（玩家）五职业立绘
    val playerUnits: Map<UnitClass, Int> = mapOf(
        UnitClass.MAGE to 84,      // 魔法师
        UnitClass.WARRIOR to 97,   // 刀盾兵
        UnitClass.TANK to 96,      // 重装战士/坦克
        UnitClass.HEALER to 99,    // 牧师
        UnitClass.SPEARMAN to 98,  // 长枪手
    )

    // 野蛮人方（敌人）五职业立绘
    val enemyUnits: Map<UnitClass, Int> = mapOf(
        UnitClass.MAGE to 111,     // 邪恶法师
        UnitClass.WARRIOR to 87,   // 维京刀斧手
        UnitClass.TANK to 109,     // 独眼巨人/坦克
        UnitClass.HEALER to 100,   // 巫婆
        UnitClass.SPEARMAN to 121, // 鬼怪
    )

    // 道具图标（大/小 × 血瓶/蓝瓶/毒瓶）
    val items: Map<ItemType, Int> = mapOf(
        ItemType.HP_POTION_LARGE to 127,   // 大血瓶
        ItemType.HP_POTION_SMALL to 115,   // 小血瓶
        ItemType.MP_POTION_LARGE to 128,   // 大蓝瓶
        ItemType.MP_POTION_SMALL to 116,   // 小蓝瓶
        ItemType.POISON_LARGE to 126,      // 大毒瓶
        ItemType.POISON_SMALL to 114,      // 小毒瓶
    )
}

/**
 * Kenney「Tiny Dungeon」美术加载器。
 *
 * 依赖 app/src/main/assets/tiles/tilemap.png（132 格 16x16，12 列 x 11 行，格间 1px）。
 * 整图只解码一次，按需裁出单格并缓存为 [ImageBitmap]，供 Canvas drawImage 使用。
 */
class GameArt(context: Context) {

    companion object {
        private const val TILE = 16   // 单格边长（px）
        private const val GAP = 1     // 格间距（px）
        private const val COLS = 12   // 每行格数
        private const val ROWS = 11   // 总行数（tilemap 共 132 格）
        private const val COUNT = COLS * ROWS
    }

    private val sheet: Bitmap? = runCatching {
        context.assets.open("tiles/tilemap.png").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private val cache = HashMap<Int, ImageBitmap>()

    /** 瓦片编号是否存在（0..131 且整图加载成功）。 */
    private fun valid(index: Int): Boolean = index in 0 until COUNT && sheet != null

    /** 裁出第 [index] 格并缓存（行优先）。 */
    fun tile(index: Int): ImageBitmap? {
        if (!valid(index)) return null
        return cache.getOrPut(index) {
            val x = (index % COLS) * (TILE + GAP)
            val y = (index / COLS) * (TILE + GAP)
            Bitmap.createBitmap(sheet!!, x, y, TILE, TILE).asImageBitmap()
        }
    }

    /** 单位立绘（按阵营 + 职业）。 */
    fun unitTile(side: Side, cls: UnitClass): ImageBitmap? {
        val map = if (side == Side.PLAYER) ArtMap.playerUnits else ArtMap.enemyUnits
        return map[cls]?.let(::tile)
    }

    /** 道具图标。 */
    fun itemTile(type: ItemType): ImageBitmap? = ArtMap.items[type]?.let(::tile)
}
