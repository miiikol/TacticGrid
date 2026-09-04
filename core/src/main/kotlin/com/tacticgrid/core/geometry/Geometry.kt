package com.tacticgrid.core.geometry

import com.tacticgrid.core.model.GridPos

/**
 * 攻击范围判定。
 * - 近战（rng<=1）：曼哈顿距离，即上下左右四格
 * - 远程（rng>=2）：欧氏距离（整数平方比较，避免浮点），允许斜向
 */
fun inAttackRange(from: GridPos, to: GridPos, rng: Int, isMelee: Boolean): Boolean =
    if (isMelee) {
        from.manhattanTo(to) <= rng
    } else {
        val dx = from.x - to.x
        val dy = from.y - to.y
        dx * dx + dy * dy <= rng * rng
    }
