# TacticGrid 回合制战棋 · Turn-Based Tactics SRPG

一个单人回合制战棋（SRPG）游戏，单机、离线、无后端。聚焦「逻辑深度 + UI 表现」，采用 Kotlin + Jetpack Compose + 纯 Kotlin 核心模块架构，游戏规则层可脱离 Android 在 JVM 单测中运行。

*A single-player turn-based strategy RPG (SRPG) — offline and backend-free. Built with Kotlin + Jetpack Compose and a pure-Kotlin core module whose game rules run in JVM unit tests independent of Android.*

---

## 功能特性 · Features

- **方格战棋 Grid Tactics**：32×20 地牢地图（地板 / 墙），五类职业（法师 / 战士 / 坦克 / 牧师 / 枪兵），职业克制环形循环。
- **回合制 Turn-based**：每个单位可「移动 + 行动」各一次，玩家阶段与敌方阶段交替进行。
- **职业技能 Skills**：五职业各带专属主动技（火球术 / 重斩 / 盾击 / 圣愈术 / 突刺），施放消耗魔力（MP），蓝瓶可回复。
- **可交互道具 Items**：血瓶（回血）、蓝瓶（回蓝）、毒瓶（投掷伤害），各分大小；每局随机刷新，拾取进背包后手动选择目标使用。
- **地图自定义 Custom Map**：用 Tiled 编辑并导出地图（floor / wall 两层），游戏运行时加载。
- **敌方 AI Enemy AI**：两档可切换——贪心策略与极小极大（α-β 剪枝，深度 2）。
- **自适应界面 Adaptive UI**：横屏左右分栏（左棋盘 + 右侧信息/操作栏），竖屏顶栏 + 棋盘 + 固定底部操作条；棋盘尺寸不随选中状态改变。
- **存档 / 读档 Save & Load**：基于 kotlinx.serialization 的 JSON 存档。

---

## 架构设计 · Architecture

采用 **UDF（单向数据流）+ 纯函数 reducer**，核心规则层与 UI 层彻底分离：

```
┌──────────────┐      GameEvent       ┌───────────────────────┐
│  UI (Compose) │ ───────────────────▶ │   reduce（纯函数）       │
│  只发事件/渲染  │ ◀─────────────────── │   Event → State+Effect │
└──────────────┘       StateFlow       └──────────┬────────────┘
                                                   │
                                     ┌─────────────▼────────────┐
                                     │  core（纯 Kotlin 模块）     │
                                     │  model / engine / ai / ... │
                                     └───────────────────────────┘
```

- **core 模块**：纯 Kotlin、零 Android 依赖，含 `model`（数据类）、`engine`（reducer + 伤害公式 + 胜负判定）、`pathfinding`（BFS 移动范围 / 寻路）、`map`（Tiled 地图加载）、`ai`（贪心 + α-β）、`serialization`（存档）。
- **app 模块**：Compose UI，只负责渲染与交互，通过 `StateFlow<GameState>` 收集状态，用户操作只发 `GameEvent`。
- **状态管理**：单一不可变 `GameState` + 纯函数 `reduce(state, event)`，`GameEffect` 仅用于表现动画，不参与状态计算。

---

## 技术栈 · Tech Stack

| 类别 Category | 技术 Technology |
|--------------|-----------------|
| 语言 Language | Kotlin |
| UI 框架 UI | Jetpack Compose（Material3） |
| 状态管理 State | StateFlow + 纯函数 reducer（手动 UDF，无 Hilt） |
| 异步 Asynchronous | Kotlin Coroutines / Flow |
| 寻路 Pathfinding | BFS 移动范围 + BFS 寻路 |
| AI | 贪心 + 极小极大（α-β 剪枝） |
| 序列化 Serialization | kotlinx.serialization |
| 测试 Testing | JUnit（纯 JVM）、Kover 覆盖率 |

---

## 项目结构 · Project Structure

```
SRPG/
├── app/                               # Android + Compose UI 层
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/tacticgrid/app/
│       ├── MainActivity.kt
│       └── ui/                        # 棋盘 / 面板 / 主题 / ViewModel
├── core/                              # 纯 Kotlin 模块（零 Android 依赖）
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/com/tacticgrid/core/
│       │   ├── model/                 # GameState / Unit / Grid / Item ...
│       │   ├── engine/                # reducer + 伤害公式 + 胜负判定
│       │   ├── pathfinding/           # BFS 移动范围 / 寻路
│       │   ├── map/                   # Tiled JSON 地图加载
│       │   ├── ai/                    # 危险图 / 贪心 / 极小极大
│       │   ├── geometry/              # 攻击范围距离判定
│       │   ├── serialization/         # 存档序列化
│       │   └── GameFactory.kt         # 关卡与初始状态工厂
│       └── test/                      # 纯 JVM 单元测试
├── gradle/                            # Gradle 版本目录
└── build.gradle.kts / settings.gradle.kts
```

---

## 环境要求 · Requirements

- **Android Studio**（推荐最新稳定版）
- **JDK 17+**（AGP 8.x 要求）
- **Android SDK**：`compileSdk 34`、`minSdk 24`、`targetSdk 34`

---

## 构建与运行 · Build & Run

```bash
# 核心单元测试（纯 JVM，无需 Android SDK）
./gradlew :core:test

# 覆盖率报告（Kover）
./gradlew :core:koverReport

# 构建并安装到设备
./gradlew :app:installDebug
```

> 说明：本仓库未提交 `gradle-wrapper.jar`（二进制），用 Android Studio 打开项目会自动生成，或手动执行 `gradle wrapper` 补全。

---

## 地图绘制 · Map Editing (Tiled)

用 [Tiled](https://www.mapeditor.org/) 编辑地图并导出 JSON，游戏启动时自动加载：

1. **新建地图**：方向 `Orthogonal`，尺寸 `32×20`，瓦片 `16×16` px。
2. **新建 Tileset**：选择 `tilemap.png`，`tile width/height = 16`，`margin = 0`、`spacing = 1`（瓦片之间有 1px 空隙）。
3. **建两个 Tile Layer**（名字务必一致）：
   - `floor`：铺地板瓦片（决定渲染，可通行）。
   - `wall`：只在墙的位置放瓦片（有值即不可通行）。
4. **导出**：`File → Export As → JSON`，取消勾选压缩，保存为 `map.json`。
5. **放置**：把 `map.json` 覆盖到 `core/src/main/resources/map/map.json`。

> 单位出生点与道具位置在 `core/.../GameFactory.kt` 中配置，需保证它们落在 `floor`（可通行）格上。

---

## 测试 · Testing

项目使用 **JUnit**，覆盖纯 Kotlin 核心规则层（可脱离 Android 运行）：

- `ReducerTest`：回合流转、移动 / 攻击 / 治疗、技能施放、道具拾取 / 使用、胜负判定。
- `RulesTest`：伤害公式、暴击 / 命中、职业克制、道具效果、随机可复现。
- `PathfindingTest`：BFS 移动范围、寻路、墙阻挡。
- `AiTest`：贪心与极小极大 AI 事件序列。
- `SerializationTest`：存档 JSON 往返一致性。

```bash
./gradlew :core:test
```

---

## 许可证 · License

本项目为毕业设计/求职作品，仅供学习与展示使用。
