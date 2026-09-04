package com.tacticgrid.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tacticgrid.core.engine.GameEffect
import com.tacticgrid.core.engine.GameEvent
import com.tacticgrid.core.model.GameState
import com.tacticgrid.core.model.GridPos
import com.tacticgrid.core.model.Item
import com.tacticgrid.core.model.ItemType
import com.tacticgrid.core.model.Phase
import com.tacticgrid.core.model.Side
import com.tacticgrid.core.model.Skill
import com.tacticgrid.core.model.SkillKind
import com.tacticgrid.core.model.Unit as GameUnit
import com.tacticgrid.core.model.UnitClass
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 飘出的伤害/治疗/拾取数字。 */
private data class FloatText(val id: Long, val pos: GridPos, val text: String, val color: Color)

// 棋盘外背景（深色布景）与 HUD 配色
private val HudBg = Color(0xFF1E2733)
private val HudPanel = Color(0xFF2A3548)
private val HudPanelLight = Color(0xFF35415A)
private val MpHpBlue = Color(0xFF29B6F6)
private val HealGreen = Color(0xFF43A047)
private val DangerRed = Color(0xFFE53935)
private val SkillOrange = Color(0xFFFF8F00)
private val SkillTeal = Color(0xFF26A69A)
private val TextSoft = Color(0xFFB8C2D4)
private val DisabledGray = Color(0xFF3B4557)

/**
 * 主界面：自适应分栏。
 * - 横屏：左侧大棋盘（不随状态缩放）+ 右侧 300dp 操作/信息栏；
 * - 竖屏：顶栏 + 大棋盘 + 固定高度底部操作条（棋盘同样不随状态缩放）。
 * 保存/读档/AI 档位收纳在「菜单」弹窗里。
 */
@Composable
fun GameScreen(vm: GameViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val aiLevel by vm.aiLevel.collectAsState()
    val busy by vm.busy.collectAsState()

    // 美术资源：加载整图并缓存裁剪（见 ArtMap，可填编号换图）
    val context = LocalContext.current
    val art = remember(context) { GameArt(context.applicationContext) }

    // 攻击闪烁与飘字等表现状态
    val hitFlash = remember { mutableStateMapOf<Int, Long>() }
    val floatTexts = remember { mutableStateListOf<FloatText>() }
    var nextId by remember { mutableStateOf(0L) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                is GameEffect.Attacked -> {
                    hitFlash[effect.targetId] = System.currentTimeMillis()
                    if (!effect.miss) {
                        val pos = vm.state.value.unit(effect.targetId)?.pos ?: return@collect
                        floatTexts += FloatText(nextId++, pos, "-${effect.damage}", DangerRed)
                    }
                }
                is GameEffect.Healed -> {
                    val pos = vm.state.value.unit(effect.targetId)?.pos ?: return@collect
                    floatTexts += FloatText(nextId++, pos, "+${effect.amount}", HealGreen)
                }
                is GameEffect.ItemPicked -> {
                    val pos = vm.state.value.unit(effect.unitId)?.pos ?: return@collect
                    floatTexts += FloatText(nextId++, pos, "拾取${itemName(effect.type)}", Color(0xFFFF8F00))
                }
                is GameEffect.ItemUsed -> {
                    val pos = vm.state.value.unit(effect.targetId)?.pos ?: return@collect
                    floatTexts += FloatText(nextId++, pos, itemText(effect.type), itemColor(effect.type))
                }
                else -> Unit
            }
        }
    }

    // 周期清理受击闪烁标记
    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            val now = System.currentTimeMillis()
            hitFlash.keys.filter { now - (hitFlash[it] ?: 0) > 300 }.forEach { hitFlash.remove(it) }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(HudBg)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= maxHeight) {
                // —— 横屏：左棋盘 + 右信息栏 ——
                LandscapeGame(
                    vm = vm,
                    state = state,
                    busy = busy,
                    art = art,
                    hitFlash = hitFlash,
                    floatTexts = floatTexts,
                    onFloatDone = { floatTexts.remove(it) },
                    onOpenSettings = { showSettings = true },
                )
            } else {
                // —— 竖屏：顶栏 + 棋盘 + 固定底栏 ——
                PortraitGame(
                    vm = vm,
                    state = state,
                    busy = busy,
                    art = art,
                    hitFlash = hitFlash,
                    floatTexts = floatTexts,
                    onFloatDone = { floatTexts.remove(it) },
                    onOpenSettings = { showSettings = true },
                )
            }
        }

        // 胜负结算浮层
        if (state.phase == Phase.GAME_OVER) {
            GameOverOverlay(
                winner = state.winner,
                turn = state.turnNumber,
                onRestart = { vm.restart() },
                onOpenMenu = { showSettings = true },
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            aiLevel = aiLevel,
            busy = busy,
            onDismiss = { showSettings = false },
            onSave = { vm.save() },
            onLoad = { vm.load() },
            onRestart = { vm.restart() },
            onToggleAi = { vm.setAiLevel(if (aiLevel == AiLevel.GREEDY) AiLevel.MINIMAX else AiLevel.GREEDY) },
        )
    }
}

// ============================================================
//  横屏：左侧大棋盘 + 右侧操作/信息栏
// ============================================================

@Composable
private fun LandscapeGame(
    vm: GameViewModel,
    state: GameState,
    busy: Boolean,
    art: GameArt?,
    hitFlash: Map<Int, Long>,
    floatTexts: List<FloatText>,
    onFloatDone: (FloatText) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        BoardStage(
            state = state,
            art = art,
            hitFlash = hitFlash,
            floatTexts = floatTexts,
            onFloatDone = onFloatDone,
            onTap = { onBoardTap(vm, it) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )

        // 分栏隔线
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color(0x44FFFFFF))
        )

        LandscapePanel(
            state = state,
            busy = busy,
            onOpenSettings = onOpenSettings,
            onEndTurn = { vm.dispatch(GameEvent.EndTurn) },
            onSelectItem = { vm.dispatch(GameEvent.SelectItem(it)) },
            onCancelItem = { vm.dispatch(GameEvent.CancelItem) },
            onSelectSkill = { vm.dispatch(GameEvent.SelectSkill(it)) },
            onCancelSkill = { vm.dispatch(GameEvent.CancelSkill) },
            onCancelSelection = { vm.dispatch(GameEvent.CancelSelection) },
        )
    }
}

@Composable
private fun LandscapePanel(
    state: GameState,
    busy: Boolean,
    onOpenSettings: () -> Unit,
    onEndTurn: () -> Unit,
    onSelectItem: (ItemType) -> Unit,
    onCancelItem: () -> Unit,
    onSelectSkill: (String) -> Unit,
    onCancelSkill: () -> Unit,
    onCancelSelection: () -> Unit,
) {
    val current = state.currentUnit()
    val canEndTurn = !busy && (state.phase == Phase.PLAYER_TURN || state.phase == Phase.UNIT_SELECTED)

    Surface(
        color = HudPanel,
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // —— 头部：标题 + 阶段 + 菜单 ——
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "像素战棋",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (busy) {
                    Text("敌方行动中…", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                MenuChip(onOpenSettings)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhaseBadge(state.phase)
                Text("回合 ${state.turnNumber}/${state.maxTurns}", color = TextSoft, fontSize = 12.sp)
            }
            Text(
                state.message,
                color = if (state.message == "敌方回合") DangerRed else TextSoft,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x2AFFFFFF))
            )

            // —— 中部：随状态变化的内容区 ——
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    state.phase == Phase.START || state.phase == Phase.GAME_OVER -> Unit

                    state.phase == Phase.ENEMY_TURN ->
                        HintChip("敌方回合：AI 正在行动，请稍候…", Modifier.fillMaxWidth())

                    // 正在为道具选择目标
                    state.usingItem != null -> {
                        val type = state.usingItem!!
                        HintChip(
                            "点击棋盘上黄色高亮的单位，对目标使用「${itemName(type)}」",
                            Modifier.fillMaxWidth(),
                        )
                        ActionChip("取消使用", Color(0xFF54627A), Modifier.fillMaxWidth()) { onCancelItem() }
                    }

                    // 正在为技能选择目标
                    state.usingSkillId != null -> {
                        val skill = current?.skills?.firstOrNull { it.id == state.usingSkillId }
                        val color = if (skill?.kind == SkillKind.HEAL) "绿色" else "橙色"
                        HintChip(
                            "点击棋盘上$color 高亮的单位，施放「${skill?.name ?: ""}」",
                            Modifier.fillMaxWidth(),
                        )
                        ActionChip("取消施放", Color(0xFF54627A), Modifier.fillMaxWidth()) { onCancelSkill() }
                    }

                    // 选中单位
                    current != null -> UnitActionColumn(
                        state = state,
                        unit = current,
                        onSelectItem = onSelectItem,
                        onSelectSkill = onSelectSkill,
                        onCancelSelection = onCancelSelection,
                    )

                    // 空闲
                    else -> IdleGuide()
                }
            }

            // —— 底部：结束回合 ——
            when {
                canEndTurn -> Button(
                    onClick = onEndTurn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF6C00),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("结束回合", fontSize = 14.sp)
                }

                busy -> Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("敌方回合中…", fontSize = 14.sp)
                }
            }
        }
    }
}

/** 右侧栏：单位详细面板（选中后展示，不影响棋盘大小）。 */
@Composable
private fun UnitActionColumn(
    state: GameState,
    unit: GameUnit,
    onSelectItem: (ItemType) -> Unit,
    onSelectSkill: (String) -> Unit,
    onCancelSelection: () -> Unit,
) {
    var bagOpen by remember { mutableStateOf(false) }
    LaunchedEffect(unit.id) { bagOpen = false }

    val acted = unit.id in state.actedIds
    val moved = unit.id in state.movedIds

    // —— 紧凑单位摘要卡（克制高度，把首屏空间留给技能按钮）——
    Surface(
        color = sideColor(unit.side).copy(alpha = 0.16f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, sideColor(unit.side).copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(sideColor(unit.side).copy(alpha = 0.35f))
                        .border(1.dp, sideColor(unit.side).copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(className(unit.unitClass), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            unit.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (unit.isHero) Text(" ★", color = Color(0xFFFFD54F), fontSize = 12.sp)
                    }
                    Text(
                        "${sideLabel(unit.side)} · ${className(unit.unitClass)}" + stateHint(moved, acted),
                        color = TextSoft,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            StatBar(
                fraction = if (unit.stats.maxHp > 0) unit.currentHp.toFloat() / unit.stats.maxHp else 0f,
                color = if (unit.side == Side.PLAYER) HealGreen else DangerRed,
                text = "HP ${unit.currentHp}/${unit.stats.maxHp}",
            )
            if (unit.stats.maxMp > 0) {
                StatBar(
                    fraction = unit.currentMp.toFloat() / unit.stats.maxMp,
                    color = MpHpBlue,
                    text = "MP ${unit.currentMp}/${unit.stats.maxMp}",
                )
            }
            Text(
                "攻${unit.stats.atk} · 防${unit.stats.def} · 移${unit.stats.mov} · 程${unit.stats.rng}",
                color = TextSoft,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }

    if (acted) {
        ActionChip("取消选中", Color(0xFF54627A), Modifier.fillMaxWidth()) { onCancelSelection() }
        return
    }

    if (bagOpen) {
        Text("背包——点击一种道具开始选择目标：", color = TextSoft, fontSize = 12.sp)
        unit.inventory.distinct().forEach { type ->
            ActionChip(itemName(type), itemColor(type), Modifier.fillMaxWidth()) { onSelectItem(type) }
        }
        ActionChip("返回", Color(0xFF54627A), Modifier.fillMaxWidth()) { bagOpen = false }
    } else {
        Text("行动", color = TextSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (unit.skills.isNotEmpty()) {
            unit.skills.forEach { skill ->
                val affordable = unit.currentMp >= skill.mpCost
                ActionChip(
                    text = "${skill.name}　·${skill.mpCost}蓝",
                    color = if (skill.kind == SkillKind.HEAL) SkillTeal else SkillOrange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = affordable,
                    onClick = { onSelectSkill(skill.id) },
                )
            }
            if (unit.skills.none { unit.currentMp >= it.mpCost }) {
                Text("（MP 不足，可用蓝瓶为队友/自己回蓝）", color = TextSoft, fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (unit.inventory.isNotEmpty()) {
                ActionChip("道具（${unit.inventory.size}）", Color(0xFF7E57C2)) { bagOpen = true }
            }
            ActionChip("取消选中", Color(0xFF54627A)) { onCancelSelection() }
        }
        HintChip(
            "点击棋盘：蓝色=移动 · 红色=攻击 · 橙/绿色=施法目标",
            Modifier.fillMaxWidth(),
        )
    }
}

/** 右侧栏空闲时的操作指引。 */
@Composable
private fun IdleGuide() {
    Surface(
        color = Color(0xFF243043),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("当前回合", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("1. 点击左侧棋盘上的一名我方单位", color = TextSoft, fontSize = 12.sp)
            Text("2. 蓝色高亮＝可移动范围", color = TextSoft, fontSize = 12.sp)
            Text("3. 点击红格普攻 / 点技能后再点橙、绿格施法", color = TextSoft, fontSize = 12.sp)
            Text("4. 注意蓝条 MP，魔法消耗可用蓝瓶补充", color = TextSoft, fontSize = 12.sp)
            Text("可在右上角「菜单」中存档、读档、调整电脑强度", color = TextSoft, fontSize = 12.sp)
        }
    }
}

// ============================================================
//  竖屏：顶栏 + 棋盘 + 固定高度底栏
// ============================================================

@Composable
private fun PortraitGame(
    vm: GameViewModel,
    state: GameState,
    busy: Boolean,
    art: GameArt?,
    hitFlash: Map<Int, Long>,
    floatTexts: List<FloatText>,
    onFloatDone: (FloatText) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .background(HudPanel)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "像素战棋",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            PhaseBadge(state.phase)
            Text("回合 ${state.turnNumber}/${state.maxTurns}", color = TextSoft, fontSize = 12.sp)
            MenuChip(onOpenSettings)
        }

        BoardStage(
            state = state,
            art = art,
            hitFlash = hitFlash,
            floatTexts = floatTexts,
            onFloatDone = onFloatDone,
            onTap = { onBoardTap(vm, it) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )

        PortraitBottomBar(
            state = state,
            busy = busy,
            onEndTurn = { vm.dispatch(GameEvent.EndTurn) },
            onSelectItem = { vm.dispatch(GameEvent.SelectItem(it)) },
            onCancelItem = { vm.dispatch(GameEvent.CancelItem) },
            onSelectSkill = { vm.dispatch(GameEvent.SelectSkill(it)) },
            onCancelSkill = { vm.dispatch(GameEvent.CancelSkill) },
            onCancelSelection = { vm.dispatch(GameEvent.CancelSelection) },
        )
    }
}

/** 竖屏固定高度底栏：高度恒定，棋盘不会因选中单位而缩小。 */
@Composable
private fun PortraitBottomBar(
    state: GameState,
    busy: Boolean,
    onEndTurn: () -> Unit,
    onSelectItem: (ItemType) -> Unit,
    onCancelItem: () -> Unit,
    onSelectSkill: (String) -> Unit,
    onCancelSkill: () -> Unit,
    onCancelSelection: () -> Unit,
) {
    val canEndTurn = !busy && (state.phase == Phase.PLAYER_TURN || state.phase == Phase.UNIT_SELECTED)

    Surface(
        color = HudPanel,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 10.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 第一行：消息 + 结束回合
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    state.message,
                    modifier = Modifier.weight(1f),
                    color = if (busy || state.message == "敌方回合") DangerRed else TextSoft,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (canEndTurn) {
                    Button(
                        onClick = onEndTurn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF6C00),
                            contentColor = Color.White,
                        ),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Text("结束回合", fontSize = 12.sp)
                    }
                }
            }

            // 第二行：情境操作（横向滚动，高度不变）
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PortraitActionRow(
                    state = state,
                    onSelectItem = onSelectItem,
                    onCancelItem = onCancelItem,
                    onSelectSkill = onSelectSkill,
                    onCancelSkill = onCancelSkill,
                    onCancelSelection = onCancelSelection,
                )
            }
        }
    }
}

/** 竖屏底栏第二行的情境内容（在横向滚动行内发射子项）。 */
@Composable
private fun PortraitActionRow(
    state: GameState,
    onSelectItem: (ItemType) -> Unit,
    onCancelItem: () -> Unit,
    onSelectSkill: (String) -> Unit,
    onCancelSkill: () -> Unit,
    onCancelSelection: () -> Unit,
) {
    val current = state.currentUnit()
    when {
        state.phase == Phase.START || state.phase == Phase.GAME_OVER -> Unit

        state.phase == Phase.ENEMY_TURN -> HintChip("敌方回合：AI 正在行动…")

        // 正在为道具选择目标
        state.usingItem != null -> {
            val type = state.usingItem!!
            HintChip("点击黄色高亮单位：使用 ${itemName(type)}")
            ActionChip("取消", Color(0xFF54627A)) { onCancelItem() }
        }

        // 正在为技能选择目标
        state.usingSkillId != null -> {
            val skill = current?.skills?.firstOrNull { it.id == state.usingSkillId }
            val color = if (skill?.kind == SkillKind.HEAL) "绿" else "橙"
            HintChip("点击${color}色高亮单位：施放「${skill?.name ?: ""}」")
            ActionChip("取消", Color(0xFF54627A)) { onCancelSkill() }
        }

        // 选中单位
        current != null -> PortraitUnitActions(
            state = state,
            unit = current,
            onSelectItem = onSelectItem,
            onSelectSkill = onSelectSkill,
            onCancelSelection = onCancelSelection,
        )

        // 空闲
        else -> HintChip("点选一名我方单位开始行动")
    }
}

/** 竖屏紧凑单位操作：信息 + 技能/道具/取消（一条横滑即可完成）。 */
@Composable
private fun PortraitUnitActions(
    state: GameState,
    unit: GameUnit,
    onSelectItem: (ItemType) -> Unit,
    onSelectSkill: (String) -> Unit,
    onCancelSelection: () -> Unit,
) {
    var bagOpen by remember { mutableStateOf(false) }
    LaunchedEffect(unit.id) { bagOpen = false }

    val acted = unit.id in state.actedIds
    HintChip("${unit.name}　HP ${unit.currentHp}/${unit.stats.maxHp}${if (unit.stats.maxMp > 0) "　蓝 ${unit.currentMp}/${unit.stats.maxMp}" else ""}")

    if (acted) {
        ActionChip("取消选中", Color(0xFF54627A)) { onCancelSelection() }
        return
    }

    if (bagOpen) {
        unit.inventory.distinct().forEach { type ->
            ActionChip(itemName(type), itemColor(type)) { onSelectItem(type) }
        }
        ActionChip("返回", Color(0xFF54627A)) { bagOpen = false }
    } else {
        val moved = unit.id in state.movedIds
        HintChip(if (moved) "点红格攻击·橙绿格施法" else "点蓝格移动·红格攻击·橙绿格施法")
        unit.skills.forEach { skill ->
            val affordable = unit.currentMp >= skill.mpCost
            ActionChip(
                text = "${skill.name}·${skill.mpCost}蓝",
                color = if (skill.kind == SkillKind.HEAL) SkillTeal else SkillOrange,
                enabled = affordable,
                onClick = { onSelectSkill(skill.id) },
            )
        }
        if (unit.inventory.isNotEmpty()) {
            ActionChip("道具", Color(0xFF7E57C2)) { bagOpen = true }
        }
        ActionChip("取消", Color(0xFF54627A)) { onCancelSelection() }
    }
}

// ============================================================
//  棋盘舞台：等比缩放并居中（不依赖底栏/信息栏状态）
// ============================================================

@Composable
private fun BoardStage(
    state: GameState,
    art: GameArt?,
    hitFlash: Map<Int, Long>,
    floatTexts: List<FloatText>,
    onFloatDone: (FloatText) -> Unit,
    onTap: (GridPos) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val cellSizeDp = minOf(
            maxWidth / state.grid.width.toFloat(),
            maxHeight / state.grid.height.toFloat(),
        )
        val cellSizePx = with(density) { cellSizeDp.toPx() }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(
                    cellSizeDp * state.grid.width.toFloat(),
                    cellSizeDp * state.grid.height.toFloat(),
                )
            ) {
                Board(state, cellSizePx, art, Modifier.fillMaxSize(), onTap = onTap)

                // 可交互道具
                for (item in state.items) {
                    key(item.id) { ItemMarker(item, cellSizeDp, cellSizePx, art) }
                }

                // 单位标记（独立 Composable，便于位移动画）
                for (unit in state.units) {
                    if (unit.isAlive) {
                        key(unit.id) {
                            UnitMarker(
                                unit = unit,
                                cellSizeDp = cellSizeDp,
                                cellSizePx = cellSizePx,
                                isCurrent = unit.id == state.currentUnitId,
                                isFlashing = unit.id in hitFlash,
                                art = art,
                            )
                        }
                    }
                }

                // 飘字
                for (ft in floatTexts) {
                    key(ft.id) { FloatingText(ft, cellSizePx) { onFloatDone(ft) } }
                }
            }
        }
    }
}

// ============================================================
//  顶栏小件
// ============================================================

@Composable
private fun MenuChip(onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HudPanelLight)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("菜单", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PhaseBadge(phase: Phase) {
    val (text, color) = when (phase) {
        Phase.PLAYER_TURN, Phase.UNIT_SELECTED -> "我方回合" to Color(0xFF64B5F6)
        Phase.ENEMY_TURN -> "敌方回合" to DangerRed
        Phase.GAME_OVER -> "战斗结束" to Color(0xFFFFD54F)
        Phase.START -> "准备" to TextSoft
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.18f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ============================================================
//  设置（暂停）弹窗
// ============================================================

@Composable
private fun SettingsDialog(
    state: GameState,
    aiLevel: AiLevel,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onRestart: () -> Unit,
    onToggleAi: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HudPanel,
        titleContentColor = Color.White,
        textContentColor = TextSoft,
        title = {
            Text("游戏菜单 · 暂停中", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "回合 ${state.turnNumber}/${state.maxTurns} · ${phaseLabel(state.phase)}" +
                        if (busy) "（敌方行动中）" else "",
                    fontSize = 13.sp,
                )
                Text(state.message, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onSave(); onDismiss() },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("存档") }
                    OutlinedButton(
                        onClick = { onLoad(); onDismiss() },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("读档") }
                }
                OutlinedButton(onClick = onToggleAi, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("电脑强度：${aiLabel(aiLevel)}（点击切换）")
                }
                TextButton(
                    onClick = { onRestart(); onDismiss() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重新开始本局", color = DangerRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("继续游戏", color = Color(0xFF64B5F6)) }
        },
    )
}

// ============================================================
//  胜负结算浮层
// ============================================================

@Composable
private fun GameOverOverlay(
    winner: Side?,
    turn: Int,
    onRestart: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val won = winner == Side.PLAYER
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = HudPanel,
            shadowElevation = 16.dp,
            modifier = Modifier.width(280.dp),
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (won) "胜利！" else "战败",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (won) HealGreen else DangerRed,
                )
                Text(
                    when {
                        won -> "敌方部队已被全歼"
                        winner == null -> "回合数用尽"
                        else -> "我方主角阵亡"
                    },
                    color = TextSoft,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Text("战斗持续了 $turn 个回合", color = TextSoft, fontSize = 12.sp)
                Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                    Text("再来一局")
                }
                OutlinedButton(onClick = onOpenMenu, modifier = Modifier.fillMaxWidth()) {
                    Text("打开菜单")
                }
            }
        }
    }
}

// ============================================================
//  棋盘上的单位 / 道具 / 飘字
// ============================================================

@Composable
private fun UnitMarker(
    unit: GameUnit,
    cellSizeDp: Dp,
    cellSizePx: Float,
    isCurrent: Boolean,
    isFlashing: Boolean,
    art: GameArt?,
) {
    // 位移动画：目标位置变化时自动 tween
    val offset by animateOffsetAsState(
        targetValue = Offset(unit.pos.x * cellSizePx, unit.pos.y * cellSizePx),
        animationSpec = tween(300),
    )
    // HP / MP 动画
    val hpFraction by animateFloatAsState(
        targetValue = if (unit.stats.maxHp > 0) unit.currentHp.toFloat() / unit.stats.maxHp else 0f,
        animationSpec = tween(400),
    )
    val mpFraction by animateFloatAsState(
        targetValue = if (unit.stats.maxMp > 0) unit.currentMp.toFloat() / unit.stats.maxMp else 0f,
        animationSpec = tween(400),
    )
    val sprite = art?.unitTile(unit.side, unit.unitClass)

    Box(
        Modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .size(cellSizeDp)
    ) {
        val chip = RoundedCornerShape(6.dp)
        val boxSize = cellSizeDp * 0.92f
        Box(
            Modifier
                .align(Alignment.Center)
                .size(boxSize)
                .clip(chip)
                .background(if (sprite != null) sideColor(unit.side).copy(alpha = 0.35f) else sideColor(unit.side))
                .then(
                    if (isCurrent) Modifier.border(2.dp, Color.White, chip)
                    else Modifier.border(1.dp, sideColor(unit.side).copy(alpha = 0.5f), chip)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (sprite != null) {
                Image(
                    bitmap = sprite,
                    contentDescription = unit.name,
                    modifier = Modifier.fillMaxSize(0.94f),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(classChar(unit.unitClass), color = Color.White, fontSize = (cellSizePx * 0.4f).sp)
            }
        }

        if (isFlashing) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(boxSize)
                    .clip(chip)
                    .background(Color.Red.copy(alpha = 0.5f))
            )
        }

        // HP / MP 双条（HP 在蓝条下方）
        val barWidth = cellSizeDp * 0.86f
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .width(barWidth),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (unit.stats.maxMp > 0) {
                MiniBar(mpFraction, MpHpBlue, Modifier.fillMaxWidth())
            }
            MiniBar(hpFraction, if (unit.side == Side.PLAYER) HealGreen else DangerRed, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MiniBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0x88000000))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(color)
        )
    }
}

@Composable
private fun ItemMarker(item: Item, cellSizeDp: Dp, cellSizePx: Float, art: GameArt?) {
    val sprite = art?.itemTile(item.type)
    Box(
        Modifier
            .offset {
                IntOffset(
                    (item.pos.x * cellSizePx + cellSizePx * 0.25f).roundToInt(),
                    (item.pos.y * cellSizePx + cellSizePx * 0.25f).roundToInt(),
                )
            }
            .size(cellSizeDp * 0.5f)
            .clip(CircleShape)
            .background(if (sprite != null) Color.Black.copy(alpha = 0.25f) else itemColor(item.type)),
        contentAlignment = Alignment.Center,
    ) {
        if (sprite != null) {
            Image(
                bitmap = sprite,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.9f),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(itemChar(item.type), color = Color.White, fontSize = (cellSizePx * 0.28f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FloatingText(ft: FloatText, cellSizePx: Float, onDone: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(200))
    LaunchedEffect(ft.id) {
        delay(700)
        visible = false
        delay(250)
        onDone()
    }
    Text(
        text = ft.text,
        color = ft.color.copy(alpha = alpha),
        fontSize = (cellSizePx * 0.45f).sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.offset {
            IntOffset(
                (ft.pos.x * cellSizePx + cellSizePx * 0.15f).roundToInt(),
                (ft.pos.y * cellSizePx - cellSizePx * 0.1f).roundToInt(),
            )
        },
    )
}

// ============================================================
//  小控件
// ============================================================

/** 深色 HUD 上的文字按钮条。 */
@Composable
private fun ActionChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) color else DisabledGray)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Color.White else Color(0xFF7C869A),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** 只读提示条。 */
@Composable
private fun HintChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x222E3F))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TextSoft, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun StatBar(fraction: Float, color: Color, text: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .height(7.dp)
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x40000000))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(color)
            )
        }
        Text(text, color = TextSoft, fontSize = 10.sp, maxLines = 1)
    }
}

// ============================================================
//  棋盘点击路由
// ============================================================

/** 点击棋盘：按当前模式路由到 技能目标 → 道具目标 → 攻击/治疗 → 选中 → 移动 → 取消。 */
private fun onBoardTap(vm: GameViewModel, pos: GridPos) {
    val s = vm.state.value
    if (s.phase != Phase.PLAYER_TURN && s.phase != Phase.UNIT_SELECTED) return

    val clickedUnit = s.unitAt(pos)
    when {
        // 使用道具：点击合法目标 → 使用；点击我方单位 → 切换选中；空白 → 取消
        s.usingItem != null && clickedUnit != null && pos in s.itemTargets ->
            vm.dispatch(GameEvent.UseItem(s.currentUnitId!!, s.usingItem!!, clickedUnit.id))
        s.usingItem != null && clickedUnit != null && clickedUnit.side == Side.PLAYER ->
            vm.dispatch(GameEvent.SelectUnit(clickedUnit.id))
        s.usingItem != null ->
            vm.dispatch(GameEvent.CancelItem)

        // 施放技能：点击合法目标 → 施放；点击我方单位 → 切换选中；空白 → 取消
        s.usingSkillId != null && clickedUnit != null && pos in s.skillTargets ->
            vm.dispatch(GameEvent.CastSkill(s.currentUnitId!!, s.usingSkillId!!, clickedUnit.id))
        s.usingSkillId != null && clickedUnit != null && clickedUnit.side == Side.PLAYER ->
            vm.dispatch(GameEvent.SelectUnit(clickedUnit.id))
        s.usingSkillId != null ->
            vm.dispatch(GameEvent.CancelSkill)

        // 选中单位后：点击攻击/治疗目标（优先于重新选中，保证牧师能奶队友）
        s.phase == Phase.UNIT_SELECTED && pos in s.attackableTiles && clickedUnit != null ->
            vm.dispatch(GameEvent.Attack(s.currentUnitId!!, clickedUnit.id))

        // 点击我方单位：选中
        clickedUnit != null && clickedUnit.side == Side.PLAYER ->
            vm.dispatch(GameEvent.SelectUnit(clickedUnit.id))

        // 点击移动范围：移动
        s.phase == Phase.UNIT_SELECTED && pos in s.reachableTiles ->
            vm.dispatch(GameEvent.MoveUnit(s.currentUnitId!!, pos))

        // 点击空白：取消选中
        s.phase == Phase.UNIT_SELECTED ->
            vm.dispatch(GameEvent.CancelSelection)
    }
}

// ============================================================
//  文案/配色工具
// ============================================================

private fun classChar(cls: UnitClass): String = when (cls) {
    UnitClass.MAGE -> "法"
    UnitClass.WARRIOR -> "战"
    UnitClass.TANK -> "坦"
    UnitClass.HEALER -> "牧"
    UnitClass.SPEARMAN -> "枪"
}

private fun className(cls: UnitClass): String = when (cls) {
    UnitClass.MAGE -> "法师"
    UnitClass.WARRIOR -> "战士"
    UnitClass.TANK -> "坦克"
    UnitClass.HEALER -> "牧师"
    UnitClass.SPEARMAN -> "枪兵"
}

private fun sideColor(side: Side): Color = when (side) {
    Side.PLAYER -> Color(0xFF5C6BC0)
    Side.ENEMY -> Color(0xFFE53935)
}

private fun sideLabel(side: Side): String = when (side) {
    Side.PLAYER -> "我方"
    Side.ENEMY -> "敌方"
}

private fun itemColor(type: ItemType): Color = when (type) {
    ItemType.HP_POTION_LARGE, ItemType.HP_POTION_SMALL -> Color(0xFFEF5350)
    ItemType.MP_POTION_LARGE, ItemType.MP_POTION_SMALL -> MpHpBlue
    ItemType.POISON_LARGE, ItemType.POISON_SMALL -> Color(0xFF66BB6A)
}

private fun itemChar(type: ItemType): String = when (type) {
    ItemType.HP_POTION_LARGE, ItemType.HP_POTION_SMALL -> "血"
    ItemType.MP_POTION_LARGE, ItemType.MP_POTION_SMALL -> "蓝"
    ItemType.POISON_LARGE, ItemType.POISON_SMALL -> "毒"
}

private fun itemName(type: ItemType): String = when (type) {
    ItemType.HP_POTION_LARGE -> "大血瓶"
    ItemType.HP_POTION_SMALL -> "小血瓶"
    ItemType.MP_POTION_LARGE -> "大蓝瓶"
    ItemType.MP_POTION_SMALL -> "小蓝瓶"
    ItemType.POISON_LARGE -> "大毒瓶"
    ItemType.POISON_SMALL -> "小毒瓶"
}

private fun itemText(type: ItemType): String = when (type) {
    ItemType.HP_POTION_LARGE -> "回满血"
    ItemType.HP_POTION_SMALL -> "+15血"
    ItemType.MP_POTION_LARGE -> "回满蓝"
    ItemType.MP_POTION_SMALL -> "+8蓝"
    ItemType.POISON_LARGE -> "-20血"
    ItemType.POISON_SMALL -> "-10血"
}

private fun phaseLabel(phase: Phase): String = when (phase) {
    Phase.START -> "准备阶段"
    Phase.PLAYER_TURN -> "我方回合"
    Phase.UNIT_SELECTED -> "单位已选中"
    Phase.ENEMY_TURN -> "敌方回合"
    Phase.GAME_OVER -> "战斗结束"
}

/** 单位摘要卡副标题里的移动/行动状态后缀。 */
private fun stateHint(moved: Boolean, acted: Boolean): String = when {
    moved && acted -> " · 已移动/行动"
    moved -> " · 已移动"
    acted -> " · 已行动"
    else -> ""
}

private fun aiLabel(level: AiLevel): String = when (level) {
    AiLevel.GREEDY -> "贪心"
    AiLevel.MINIMAX -> "极小极大"
}
