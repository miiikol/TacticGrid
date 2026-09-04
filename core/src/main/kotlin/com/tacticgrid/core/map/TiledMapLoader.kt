package com.tacticgrid.core.map

import com.tacticgrid.core.model.Flip
import com.tacticgrid.core.model.Grid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Tiled 导出的地图 JSON（仅取需要的字段）。 */
@Serializable
private data class TiledMap(
    val width: Int,
    val height: Int,
    val layers: List<TiledLayer> = emptyList(),
    val tilesets: List<TiledTileset> = emptyList(),
)

@Serializable
private data class TiledLayer(
    val name: String,
    val data: List<Long> = emptyList(),
    val type: String = "tilelayer",
)

@Serializable
private data class TiledTileset(
    val firstgid: Int,
)

/**
 * Tiled 地图加载器。
 *
 * 兼容真实 Tiled 导出的常见格式：
 * - gid 是 32 位无符号整数（JSON 中可能超过 Int.MAX_VALUE），故用 Long 解析；
 * - 高位 3 bit（H=0x80000000 / V=0x40000000 / D=0x20000000）是翻转标志，bit28 忽略；
 * - 翻转编码进 [Flip]，渲染时应用；碰撞判定只关心真实 gid；
 * - 多 tileset 时，按 firstgid 定位 gid 所属的 tileset，再换算成 tilemap.png 索引；
 * - 墙层：名字含 "wall" 的 tile layer（非 0 即阻挡）；
 * - 地板层：第一个非墙的 tile layer。
 */
object TiledMapLoader {

    private const val FLAG_H = 0x80000000L
    private const val FLAG_V = 0x40000000L
    private const val FLAG_D = 0x20000000L
    private const val GID_MASK = 0x0FFFFFFFL // 掩掉 bit28-31 的翻转标志

    private val json = Json { ignoreUnknownKeys = true }

    fun fromResource(path: String = "/map/map.json"): Grid? {
        val text = TiledMapLoader::class.java.getResourceAsStream(path)
            ?.bufferedReader()?.use { it.readText() }
            ?: return null
        return fromJson(text)
    }

    fun fromJson(text: String): Grid {
        val map = json.decodeFromString<TiledMap>(text)
        val firstgids = map.tilesets.map { it.firstgid }.sorted()

        val wallLayer = map.layers.firstOrNull { it.type == "tilelayer" && it.name.contains("wall", ignoreCase = true) }
        val floorLayer = map.layers.firstOrNull { it.type == "tilelayer" && it != wallLayer }

        /** 解析 gid → (tilemap.png 索引, 翻转编码)。 */
        fun decode(gid: Long): Pair<Int, Int> {
            if (gid == 0L) return 0 to Flip.NONE
            val h = (gid and FLAG_H) != 0L
            val v = (gid and FLAG_V) != 0L
            val d = (gid and FLAG_D) != 0L
            val flip = (if (h) Flip.HORIZONTAL else 0) or
                (if (v) Flip.VERTICAL else 0) or
                (if (d) Flip.DIAGONAL else 0)

            val real = (gid and GID_MASK).toInt()          // 去翻转标志后的真实 gid
            val first = firstgids.lastOrNull { it <= real } ?: 1 // 定位所属 tileset
            val index = (real - first).coerceAtLeast(0)    // tileset 内索引 == tilemap.png 索引
            return index to flip
        }

        val tiles = IntArray(map.width * map.height)
        val blocked = BooleanArray(map.width * map.height)
        val flips = IntArray(map.width * map.height)
        for (i in tiles.indices) {
            val wallGid = wallLayer?.data?.getOrNull(i) ?: 0L
            if (wallGid != 0L) {
                blocked[i] = true
                val (index, flip) = decode(wallGid)
                tiles[i] = index
                flips[i] = flip
            } else {
                blocked[i] = false
                val (index, flip) = decode(floorLayer?.data?.getOrNull(i) ?: 0L)
                tiles[i] = index
                flips[i] = flip
            }
        }
        return Grid(map.width, map.height, tiles.toList(), blocked.toList(), flips.toList())
    }
}
