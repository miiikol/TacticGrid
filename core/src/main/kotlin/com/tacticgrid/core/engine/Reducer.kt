package com.tacticgrid.core.engine

import com.tacticgrid.core.geometry.inAttackRange
import com.tacticgrid.core.model.GameState
import com.tacticgrid.core.model.GridPos
import com.tacticgrid.core.model.ItemType
import com.tacticgrid.core.model.Phase
import com.tacticgrid.core.model.Side
import com.tacticgrid.core.model.Skill
import com.tacticgrid.core.model.SkillKind
import com.tacticgrid.core.model.Unit
import com.tacticgrid.core.model.UnitClass
import com.tacticgrid.core.pathfinding.Pathfinding
import kotlin.random.Random

/** reducer 的输出：新状态 + 供 UI 消费的表现副作用。 */
data class EngineResult(
    val state: GameState,
    val effects: List<GameEffect> = emptyList(),
)

// 随机数混洗常量（Knuth 乘法散列），保证同一 seed 下结果确定
private const val PRIME = 0x9E3779B9L

/**
 * 纯函数 reducer：Event -> State -> State。
 * 除随机（由 state 内种子确定性派生）外无任何副作用，便于 JVM 单测。
 */
fun reduce(state: GameState, event: GameEvent): EngineResult = when (event) {
    is GameEvent.StartGame -> startGame(state)
    is GameEvent.SelectUnit -> selectUnit(state, event.id)
    is GameEvent.MoveUnit -> moveUnit(state, event.unitId, event.target)
    is GameEvent.Attack -> act(state, event.attackerId, event.targetId)
    is GameEvent.CancelSelection -> cancelSelection(state)
    is GameEvent.EndTurn -> endTurn(state)
    is GameEvent.SelectItem -> selectItem(state, event.itemType)
    is GameEvent.CancelItem -> cancelItem(state)
    is GameEvent.UseItem -> useItem(state, event.userId, event.itemType, event.targetId)
    is GameEvent.SelectSkill -> selectSkill(state, event.skillId)
    is GameEvent.CancelSkill -> cancelSkill(state)
    is GameEvent.CastSkill -> castSkill(state, event.userId, event.skillId, event.targetId)
}

private fun startGame(state: GameState): EngineResult {
    if (state.phase != Phase.START) return EngineResult(state)
    return EngineResult(
        state.copy(
            phase = Phase.PLAYER_TURN,
            turnNumber = 1,
            movedIds = emptySet(),
            actedIds = emptySet(),
            message = "玩家回合",
        )
    )
}

private fun selectUnit(state: GameState, id: Int): EngineResult {
    if (state.phase != Phase.PLAYER_TURN && state.phase != Phase.UNIT_SELECTED) return EngineResult(state)
    val unit = state.unit(id) ?: return EngineResult(state)
    if (unit.side != Side.PLAYER || !unit.isAlive) return EngineResult(state)
    if (id in state.movedIds && id in state.actedIds) {
        return EngineResult(state.copy(message = "该单位已结束行动"))
    }

    val reachable = if (id in state.movedIds) {
        emptySet()
    } else {
        Pathfinding.reachable(state.grid, unit.pos, unit.stats.mov, state.occupiedTiles(id)).keys
    }
    return EngineResult(
        state.copy(
            phase = Phase.UNIT_SELECTED,
            currentUnitId = id,
            reachableTiles = reachable,
            attackableTiles = attackableTilesFor(state, unit),
            message = "选中 ${unit.name}",
        )
    )
}

private fun moveUnit(state: GameState, unitId: Int, target: GridPos): EngineResult {
    val unit = state.unit(unitId) ?: return EngineResult(state)
    if (!canAct(state, unit)) return EngineResult(state)
    if (unitId in state.movedIds) return EngineResult(state.copy(message = "该单位本回合已移动"))
    if (target == unit.pos) return EngineResult(state)
    if (state.unitAt(target) != null) return EngineResult(state.copy(message = "目标格被占据"))

    val blocked = state.occupiedTiles(unitId)
    val reachable = Pathfinding.reachable(state.grid, unit.pos, unit.stats.mov, blocked)
    if (target !in reachable) return EngineResult(state.copy(message = "不可到达"))
    val path = Pathfinding.path(state.grid, unit.pos, target, blocked)
        ?: return EngineResult(state.copy(message = "无路可走"))

    // 拾取落点上的道具（若有）：加入背包，稍后手动使用
    val item = state.items.firstOrNull { it.pos == target }
    val movedUnit = unit.copy(pos = target)
    val finalUnit = if (item != null) movedUnit.copy(inventory = movedUnit.inventory + item.type) else movedUnit
    val newUnits = replace(state.units, unitId) { finalUnit }
    val effects = buildList {
        add(GameEffect.Moved(unitId, unit.pos, target, path))
        if (item != null) add(GameEffect.ItemPicked(unitId, item.id, item.type))
    }
    return EngineResult(
        state.copy(
            units = newUnits,
            items = if (item != null) state.items.filter { it.id != item.id } else state.items,
            movedIds = state.movedIds + unitId,
            reachableTiles = emptySet(),
            attackableTiles = attackableTilesFor(state.copy(units = newUnits), finalUnit),
            message = if (item != null) "移动完成，拾取了道具" else "移动完成",
        ),
        effects,
    )
}

private fun act(state: GameState, attackerId: Int, targetId: Int): EngineResult {
    val attacker = state.unit(attackerId) ?: return EngineResult(state)
    val target = state.unit(targetId) ?: return EngineResult(state)
    if (!canAct(state, attacker)) return EngineResult(state)
    if (attackerId in state.actedIds) return EngineResult(state.copy(message = "该单位本回合已行动"))
    if (!attacker.isAlive || !target.isAlive) return EngineResult(state)

    // 治疗者：对受伤友军治疗
    if (attacker.unitClass == UnitClass.HEALER) {
        if (target.side != attacker.side) return EngineResult(state.copy(message = "治疗者无法攻击"))
        if (target.currentHp >= target.stats.maxHp) return EngineResult(state.copy(message = "目标生命值已满"))
        if (!inAttackRange(attacker.pos, target.pos, attacker.stats.rng, attacker.isMelee)) {
            return EngineResult(state.copy(message = "目标不在治疗范围"))
        }
        val heal = computeHeal(attacker)
        val healed = (target.currentHp + heal).coerceAtMost(target.stats.maxHp)
        val next = state.copy(
            units = replace(state.units, targetId) { it.copy(currentHp = healed) },
            actedIds = state.actedIds + attackerId,
            attackableTiles = emptySet(),
            message = "${attacker.name} 治疗了 ${target.name}",
        )
        return EngineResult(next, listOf(GameEffect.Healed(attackerId, targetId, heal)))
    }

    // 战斗单位：攻击敌方
    if (target.side == attacker.side) return EngineResult(state.copy(message = "不能攻击友军"))
    if (!inAttackRange(attacker.pos, target.pos, attacker.stats.rng, attacker.isMelee)) {
        return EngineResult(state.copy(message = "目标不在攻击范围"))
    }

    val random = Random(state.rngSeed xor (state.rngCounter * PRIME))
    val combat = computeCombat(attacker, target, random)
    val newHp = (target.currentHp - combat.damage).coerceAtLeast(0)

    val effects = buildList {
        add(GameEffect.Attacked(attackerId, targetId, combat.damage, combat.crit, combat.miss))
        if (newHp <= 0) add(GameEffect.UnitDied(targetId))
    }
    val next = state.copy(
        units = replace(state.units, targetId) { it.copy(currentHp = newHp) },
        actedIds = state.actedIds + attackerId,
        rngCounter = state.rngCounter + 1,
        attackableTiles = emptySet(),
        message = if (combat.miss) "${attacker.name} 攻击未命中" else "${attacker.name} 对 ${target.name} 造成 ${combat.damage} 点伤害",
    )

    val (final, outcomeEffects) = checkOutcome(next)
    return EngineResult(final, effects + outcomeEffects)
}

private fun cancelSelection(state: GameState): EngineResult {
    if (state.phase != Phase.UNIT_SELECTED) return EngineResult(state)
    return EngineResult(
        state.copy(
            phase = Phase.PLAYER_TURN,
            currentUnitId = null,
            reachableTiles = emptySet(),
            attackableTiles = emptySet(),
            usingItem = null,
            itemTargets = emptySet(),
            usingSkillId = null,
            skillTargets = emptySet(),
        )
    )
}

/** 从背包选择道具，进入"选择目标"阶段。 */
private fun selectItem(state: GameState, itemType: ItemType): EngineResult {
    val user = state.currentUnit() ?: return EngineResult(state)
    if (state.phase != Phase.UNIT_SELECTED || user.side != Side.PLAYER) return EngineResult(state)
    if (itemType !in user.inventory) return EngineResult(state.copy(message = "背包里没有该道具"))

    val targets = itemTargetsFor(state, user.id, itemType)
    if (targets.isEmpty()) return EngineResult(state.copy(message = "没有可用目标"))
    return EngineResult(
        state.copy(
            usingItem = itemType,
            itemTargets = targets,
            reachableTiles = emptySet(),
            attackableTiles = emptySet(),
            usingSkillId = null,
            skillTargets = emptySet(),
            message = "选择道具目标",
        )
    )
}

/** 取消使用道具。 */
private fun cancelItem(state: GameState): EngineResult {
    if (state.usingItem == null) return EngineResult(state)
    val user = state.currentUnit() ?: return EngineResult(state)
    return EngineResult(
        state.copy(
            usingItem = null,
            itemTargets = emptySet(),
            reachableTiles = emptySet(),
            attackableTiles = attackableTilesFor(state, user),
            message = "已取消使用道具",
        )
    )
}

/** 使用道具作用于目标单位。 */
private fun useItem(state: GameState, userId: Int, itemType: ItemType, targetId: Int): EngineResult {
    val user = state.unit(userId) ?: return EngineResult(state)
    val target = state.unit(targetId) ?: return EngineResult(state)
    if (state.phase != Phase.UNIT_SELECTED || state.currentUnitId != userId) return EngineResult(state)
    if (itemType !in user.inventory) return EngineResult(state.copy(message = "背包里没有该道具"))
    if (userId in state.actedIds) return EngineResult(state.copy(message = "该单位本回合已行动"))
    if (!target.isAlive) return EngineResult(state)
    if (target.pos !in itemTargetsFor(state, userId, itemType)) return EngineResult(state.copy(message = "目标无效"))

    val newTarget = applyItem(target, itemType)
    val newUser = user.copy(inventory = user.inventory.minus(itemType))
    val newUnits = replace(replace(state.units, targetId) { newTarget }, userId) { newUser }

    val effects = buildList {
        add(GameEffect.ItemUsed(userId, targetId, itemType))
        if (!newTarget.isAlive) add(GameEffect.UnitDied(targetId))
    }
    val next = state.copy(
        units = newUnits,
        actedIds = state.actedIds + userId,
        usingItem = null,
        itemTargets = emptySet(),
        usingSkillId = null,
        skillTargets = emptySet(),
        attackableTiles = emptySet(),
        message = "${user.name} 对 ${target.name} 使用了道具",
    )
    val (final, outcomeEffects) = checkOutcome(next)
    return EngineResult(final, effects + outcomeEffects)
}

/** 从背包选择技能并进入"选择目标"阶段。 */
private fun selectSkill(state: GameState, skillId: String): EngineResult {
    val user = state.currentUnit() ?: return EngineResult(state)
    if (state.phase != Phase.UNIT_SELECTED || user.side != Side.PLAYER) return EngineResult(state)
    if (user.id in state.actedIds) return EngineResult(state.copy(message = "该单位本回合已行动"))
    val skill = user.skills.firstOrNull { it.id == skillId }
        ?: return EngineResult(state.copy(message = "该单位没有此技能"))
    if (user.currentMp < skill.mpCost) return EngineResult(state.copy(message = "魔力不足，无法施放"))

    val targets = skillTargetsFor(state, user, skill)
    if (targets.isEmpty()) {
        val why = if (skill.kind == SkillKind.HEAL) "范围内没有受伤的友军" else "范围内没有可攻击的敌人"
        return EngineResult(state.copy(message = why))
    }
    return EngineResult(
        state.copy(
            usingSkillId = skillId,
            skillTargets = targets,
            reachableTiles = emptySet(),
            attackableTiles = emptySet(),
            message = "选择「${skill.name}」的目标",
        )
    )
}

/** 取消施放技能，回到普通行动态。 */
private fun cancelSkill(state: GameState): EngineResult {
    if (state.usingSkillId == null) return EngineResult(state)
    val user = state.currentUnit() ?: return EngineResult(state)
    return EngineResult(
        state.copy(
            usingSkillId = null,
            skillTargets = emptySet(),
            reachableTiles = emptySet(),
            attackableTiles = attackableTilesFor(state, user),
            message = "已取消施放技能",
        )
    )
}

/** 对目标施放技能：扣 MP、结算伤害/治疗，并记为一次行动。 */
private fun castSkill(state: GameState, userId: Int, skillId: String, targetId: Int): EngineResult {
    val user = state.unit(userId) ?: return EngineResult(state)
    val target = state.unit(targetId) ?: return EngineResult(state)
    if (state.phase != Phase.UNIT_SELECTED || state.currentUnitId != userId) return EngineResult(state)
    if (userId in state.actedIds) return EngineResult(state.copy(message = "该单位本回合已行动"))
    val skill = user.skills.firstOrNull { it.id == skillId }
        ?: return EngineResult(state.copy(message = "该单位没有此技能"))
    if (user.currentMp < skill.mpCost) return EngineResult(state.copy(message = "魔力不足"))
    if (!user.isAlive || !target.isAlive) return EngineResult(state)
    if (target.pos !in skillTargetsFor(state, user, skill)) return EngineResult(state.copy(message = "目标无效"))

    // 结算技能效果（治疗只作用于友军，伤害只作用于敌人）
    val afterUser = user.copy(currentMp = user.currentMp - skill.mpCost)
    val effects = mutableListOf<GameEffect>()
    val random = Random(state.rngSeed xor (state.rngCounter * PRIME))
    // 先生成"目标 HP 已变化"的中间状态，再叠加施法者扣蓝与行动记录
    val afterTarget: GameState = when (skill.kind) {
        SkillKind.HEAL -> {
            val amount = skillHeal(skill)
            val healed = (target.currentHp + amount).coerceAtMost(target.stats.maxHp)
            effects += GameEffect.Healed(userId, targetId, amount)
            state.copy(units = replace(state.units, targetId) { it.copy(currentHp = healed) })
        }
        SkillKind.DAMAGE -> {
            val combat = computeSkill(user, target, skill, random)
            val newHp = (target.currentHp - combat.damage).coerceAtLeast(0)
            effects += GameEffect.Attacked(userId, targetId, combat.damage, combat.crit, combat.miss)
            if (newHp <= 0) effects += GameEffect.UnitDied(targetId)
            state.copy(units = replace(state.units, targetId) { it.copy(currentHp = newHp) })
        }
    }
    val next = afterTarget.copy(
        units = replace(afterTarget.units, userId) { afterUser },
        actedIds = state.actedIds + userId,
        rngCounter = if (skill.kind == SkillKind.DAMAGE) state.rngCounter + 1 else state.rngCounter,
        usingSkillId = null,
        skillTargets = emptySet(),
        attackableTiles = emptySet(),
        message = "${user.name} 施放「${skill.name}」",
    )
    val (final, outcomeEffects) = checkOutcome(next)
    return EngineResult(final, effects + outcomeEffects)
}

/** 道具可作用的目标位置：血瓶/蓝瓶→友军，毒瓶→敌军。 */
private fun itemTargetsFor(state: GameState, userId: Int, itemType: ItemType): Set<GridPos> {
    val user = state.unit(userId) ?: return emptySet()
    return when (itemType) {
        ItemType.HP_POTION_LARGE, ItemType.HP_POTION_SMALL ->
            state.units.filter { it.side == user.side && it.isAlive && it.currentHp < it.stats.maxHp }.map { it.pos }.toSet()
        ItemType.MP_POTION_LARGE, ItemType.MP_POTION_SMALL ->
            state.units.filter { it.side == user.side && it.isAlive && it.currentMp < it.stats.maxMp }.map { it.pos }.toSet()
        ItemType.POISON_LARGE, ItemType.POISON_SMALL ->
            state.units.filter { it.side != user.side && it.isAlive }.map { it.pos }.toSet()
    }
}

/** 当前技能可作用的目标位置：伤害技能→射程内敌人，治疗技能→射程内受伤友军。 */
private fun skillTargetsFor(state: GameState, actor: Unit, skill: Skill): Set<GridPos> =
    state.units
        .filter { it.isAlive && it.id != actor.id }
        .filter {
            when (skill.kind) {
                SkillKind.DAMAGE -> it.side != actor.side
                SkillKind.HEAL -> it.side == actor.side && it.currentHp < it.stats.maxHp
            }
        }
        .filter { inAttackRange(actor.pos, it.pos, skill.range, skill.range <= 1) }
        .map { it.pos }
        .toSet()

private fun endTurn(state: GameState): EngineResult = when (state.phase) {
    Phase.PLAYER_TURN, Phase.UNIT_SELECTED -> EngineResult(
        state.copy(
            phase = Phase.ENEMY_TURN,
            currentUnitId = null,
            reachableTiles = emptySet(),
            attackableTiles = emptySet(),
            movedIds = emptySet(),
            actedIds = emptySet(),
            usingItem = null,
            itemTargets = emptySet(),
            usingSkillId = null,
            skillTargets = emptySet(),
            message = "敌方回合",
        )
    )
    Phase.ENEMY_TURN -> {
        val next = state.copy(
            phase = Phase.PLAYER_TURN,
            turnNumber = state.turnNumber + 1,
            movedIds = emptySet(),
            actedIds = emptySet(),
            message = "玩家回合",
        )
        val (final, effects) = checkOutcome(next)
        EngineResult(final, effects)
    }
    else -> EngineResult(state)
}

/** 胜负判定：全歼敌军胜；主角阵亡或回合超限负。 */
private fun checkOutcome(state: GameState): Pair<GameState, List<GameEffect>> {
    val enemyAlive = state.units.any { it.side == Side.ENEMY && it.isAlive }
    val playerAlive = state.units.any { it.side == Side.PLAYER && it.isAlive }
    val heroAlive = state.units.any { it.side == Side.PLAYER && it.isAlive && it.isHero }

    return when {
        !enemyAlive -> state.copy(phase = Phase.GAME_OVER, winner = Side.PLAYER, message = "胜利！敌军全灭") to
            listOf(GameEffect.GameOver(true))
        !playerAlive || !heroAlive -> state.copy(phase = Phase.GAME_OVER, winner = Side.ENEMY, message = "失败：主角阵亡") to
            listOf(GameEffect.GameOver(false))
        state.turnNumber > state.maxTurns -> state.copy(phase = Phase.GAME_OVER, winner = Side.ENEMY, message = "失败：回合超限") to
            listOf(GameEffect.GameOver(false))
        else -> state to emptyList()
    }
}

/** 判断某个单位当前是否允许行动（玩家选中 or 敌方回合）。 */
private fun canAct(state: GameState, actor: Unit): Boolean = when (state.phase) {
    Phase.UNIT_SELECTED -> actor.side == Side.PLAYER && state.currentUnitId == actor.id
    Phase.ENEMY_TURN -> actor.side == Side.ENEMY
    else -> false
}

/** 当前单位可作用的合法目标位置（治疗者→受伤友军，其余→敌方）。 */
private fun attackableTilesFor(state: GameState, actor: Unit): Set<GridPos> =
    state.units
        .filter { it.isAlive && it.id != actor.id }
        .filter {
            if (actor.unitClass == UnitClass.HEALER) it.side == actor.side && it.currentHp < it.stats.maxHp
            else it.side != actor.side
        }
        .filter { inAttackRange(actor.pos, it.pos, actor.stats.rng, actor.isMelee) }
        .map { it.pos }
        .toSet()

/** 用 [transform] 替换指定 id 的单位，其余保持不变。 */
private fun replace(units: List<Unit>, id: Int, transform: (Unit) -> Unit): List<Unit> =
    units.map { if (it.id == id) transform(it) else it }
