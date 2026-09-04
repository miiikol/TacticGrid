package com.tacticgrid.core.pathfinding

import com.tacticgrid.core.model.Grid
import com.tacticgrid.core.model.GridPos
import java.util.ArrayDeque

/** 四方向移动（战棋通常不斜移）。 */
private val DIRECTIONS = listOf(GridPos(1, 0), GridPos(-1, 0), GridPos(0, 1), GridPos(0, -1))

object Pathfinding {

    /**
     * 移动范围：BFS（每格代价 1，无特殊地形）。
     * @param blocked 额外不可落脚/不可穿越的格子（通常为任意存活单位）
     * @return 可达格 -> 到达该格所需步数
     */
    fun reachable(grid: Grid, start: GridPos, movePower: Int, blocked: Set<GridPos>): Map<GridPos, Int> {
        val dist = mutableMapOf(start to 0)
        val queue = ArrayDeque<GridPos>()
        queue.add(start)

        while (queue.isNotEmpty()) {
            val cur = queue.poll()
            val d = dist[cur] ?: continue
            if (d >= movePower) continue
            for (dir in DIRECTIONS) {
                val next = GridPos(cur.x + dir.x, cur.y + dir.y)
                if (!grid.contains(next)) continue
                if (grid.isBlocked(next)) continue
                if (next in blocked) continue
                if (next !in dist) {
                    dist[next] = d + 1
                    queue.add(next)
                }
            }
        }
        return dist
    }

    /**
     * 寻路：BFS（每格代价 1）。返回从 start 到 goal 的格子序列（含两端），不可达返回 null。
     */
    fun path(grid: Grid, start: GridPos, goal: GridPos, blocked: Set<GridPos>): List<GridPos>? {
        if (start == goal) return listOf(start)
        if (!grid.contains(goal) || grid.isBlocked(goal) || goal in blocked) return null

        val cameFrom = mutableMapOf<GridPos, GridPos>()
        val visited = mutableSetOf(start)
        val queue = ArrayDeque<GridPos>()
        queue.add(start)

        while (queue.isNotEmpty()) {
            val cur = queue.poll()
            if (cur == goal) return reconstruct(cameFrom, goal)
            for (dir in DIRECTIONS) {
                val next = GridPos(cur.x + dir.x, cur.y + dir.y)
                if (!grid.contains(next)) continue
                if (grid.isBlocked(next)) continue
                // 目标格允许被单位"走到"，但中间不可穿越单位
                if (next != goal && next in blocked) continue
                if (visited.add(next)) {
                    cameFrom[next] = cur
                    queue.add(next)
                }
            }
        }
        return null
    }

    private fun reconstruct(cameFrom: Map<GridPos, GridPos>, end: GridPos): List<GridPos> {
        val result = mutableListOf(end)
        var cur = end
        while (cameFrom.containsKey(cur)) {
            cur = cameFrom.getValue(cur)
            result.add(cur)
        }
        return result.reversed()
    }
}
