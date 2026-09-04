package com.tacticgrid.core.pathfinding

import com.tacticgrid.core.model.Grid
import com.tacticgrid.core.model.GridPos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathfindingTest {

    @Test
    fun `reachable 受阻挡与移动力限制`() {
        val grid = Grid.open(5, 5)
        val blocked = setOf(GridPos(1, 0))
        // 绕开 (1,0) 走到 (2,0) 需要 4 步，故移动力设为 4
        val reachable = Pathfinding.reachable(grid, GridPos(0, 0), 4, blocked)

        assertTrue(GridPos(0, 0) in reachable)
        assertTrue(GridPos(0, 1) in reachable)
        assertTrue(GridPos(2, 0) in reachable)
        assertFalse(GridPos(1, 0) in reachable) // 被单位阻挡
        assertFalse(GridPos(3, 0) in reachable) // 移动力不足（(3,0) 需 5 步）
    }

    @Test
    fun `墙格不可通行`() {
        // 中间一格是墙（blocked=true）
        val grid = Grid(3, 1, listOf(0, 0, 0), listOf(false, true, false))
        val reachable = Pathfinding.reachable(grid, GridPos(0, 0), 10, emptySet())
        assertTrue(GridPos(0, 0) in reachable)
        assertFalse(GridPos(1, 0) in reachable) // 墙
        assertFalse(GridPos(2, 0) in reachable) // 被墙挡住
    }

    @Test
    fun `寻路返回完整路径并绕开阻挡`() {
        val grid = Grid.open(5, 5)
        val path = Pathfinding.path(grid, GridPos(0, 0), GridPos(2, 0), setOf(GridPos(1, 0)))
        assertNotNull(path)
        assertEquals(GridPos(0, 0), path!!.first())
        assertEquals(GridPos(2, 0), path.last())
        assertTrue(path.none { it == GridPos(1, 0) })
    }

    @Test
    fun `不可达目标返回空`() {
        val grid = Grid(3, 1, listOf(0, 0, 0), listOf(false, true, false))
        assertTrue(Pathfinding.path(grid, GridPos(0, 0), GridPos(2, 0), emptySet()).isNullOrEmpty())
    }
}
