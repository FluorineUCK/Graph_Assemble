# Graph Assembly：KubeJS 使用指南

[English](kubejs.md) | 简体中文

面向整合包作者与配方作者。本文对应 **Graph Assembly 0.1.1、Minecraft 1.21.1**。工件及结算仅支持物品；已适配机器可将流体作为原生辅助资源。

图装配把一次加工表示为节点，把允许进行的下一道工序表示为有向边。工件在设备间移动时携带当前节点、加工标记与检查点记录，抵达出口后按规则结算。

## 目录

1. [安装与脚本位置](#1-安装与脚本位置)
2. [构建你的第一张图：冲压后切割](#2-构建你的第一张图冲压后切割)
3. [原料、工件、辅料与产物](#3-原料工件辅料与产物)
4. [完整加工链与设备限制](#4-完整加工链与设备限制)
5. [构建器 API](#5-构建器-api)
6. [节点选项](#6-节点选项)
7. [选择分支](#7-选择分支)
8. [随机分支、循环与奖励上限](#8-随机分支循环与奖励上限)
9. [完成条件与产物结算](#9-完成条件与产物结算)
10. [附属执行适配与完整原生配方预览](#10-附属执行适配与完整原生配方预览)
11. [直接 JSON、修改与删除](#11-直接-json修改与删除)
12. [重载、排错与发布检查](#12-重载排错与发布检查)
13. [内置图过滤器：双工件教学配方](#13-内置图过滤器双工件教学配方)

## 1. 安装与脚本位置

### 版本与依赖

| 组件 | 本文对应版本 / 用途 |
|---|---|
| Minecraft | 1.21.1 |
| Java | 21 |
| NeoForge | 开发验证基线 21.1.234 |
| Create | 6.0.10，开发构建 6.0.10-281；同时安装其所需依赖 |
| Graph Assembly | 0.1.1 |
| KubeJS NeoForge | 开发验证基线 `2101.7.2-build.379` |
| Rhino | 对应基线 `2101.2.7-build.81` |
| KubeJS Create（可选） | `2101.3.1-build.18`；提供 `event.recipes.create.*`，基础示例不需要它 |
| JEI（可选） | 图内预览支持 `19.39.0.368`–`19.70.0.469`（含两端） |

使用本文脚本需要安装 KubeJS；只用数据包编写图配方时不需要 KubeJS。`GraphAssembly` 全局对象由本模组的 KubeJS 插件提供。

将配方脚本保存为实例或服务器目录下的 UTF-8 文件，如：

```text
kubejs/
└─ server_scripts/
   └─ graph_assembly_recipes.js
```

配方必须放在 `ServerEvents.recipes(function (event) { ... })` 内。修改后执行 `/reload`；多人环境由服务器加载配方脚本，玩家无需在自己的 `server_scripts` 中重复注册。客户端和服务器都安装 Graph Assembly、Create 及所需依赖；JEI 用于客户端展示。

本文中，所有完整示例均使用独立配方 ID。可先单独启用一个示例，确认结果后再组合。特别注意使用同一原料、同一入口工序的示例，不能同时启用。

## 2. 构建你的第一张图：冲压后切割

将以下完整脚本放入 `kubejs/server_scripts/graph_assembly_recipes.js`：

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({ item: 'minecraft:iron_ingot' })
        .node('press', {
            type: 'create:pressing'
        }, { start: true })
        .node('cut', {
            type: 'create:cutting',
            processing_time: 40
        }, { finish: true })
        .edge('press', 'cut')
        .output({
            results: [{ item: 'minecraft:gold_ingot', count: 1 }]
        });

    event.custom(graph.build()).id('my_pack:graph/basic');
});
```

对应实际流程如下：

1. 将铁锭交给动力冲压机：执行入口 `press`，生成携带图进度的铁粒工件。
2. 将这个工件交给动力锯：执行 `cut`。
3. `cut` 是出口，且产物规则没有条件，因此结算为一个金锭。

没有图进度的普通铁粒将不能直接进行第二步工序。注意工件物品 ID 与节点 ID 的区别：`minecraft:iron_nugget` 是承载进度的物品，`press`、`cut` 是这张图中的步骤名称。

`my_pack:graph/basic` 是整张配方的 ID，可替换为你想要的命名空间和路径。tips：后续尽量保持配方 ID 与节点 ID 稳定，以便现有工件继续工作，同时也能减少你的工作量 ;-)

## 3. 原料、工件、辅料与产物

| 概念         | 写在哪里 | 含义                                                           |
|--------------|---|----------------------------------------------------------------|
| 入口原料     | `.ingredient(...)`，或入口节点的 `ingredient` | 尚未进入图的物品                                               |
| 默认工件     | `GraphAssembly.create('物品ID')` | 首次加工后承载进度的物品                                       |
| 节点工件变体 | 节点选项 `transitional_item` | 此步骤成功后换成另一种工件物品                                 |
| 工序辅料     | 节点 `recipe.ingredients`，或相应原生资源字段 | 普通辅助物品、声明角色的辅助工件，以及注液／盆的原生流体等资源 |
| 工序副产物   | 节点 `recipe.results` | 该道加工产生的额外物品                                         |
| 最终产物     | `.output({ results: [...] })` 或节点选项 `outputs` | 在出口满足结算规则时产生                                       |
| 失败废品     | 出口节点选项 `scrap` | 严格出口类型没有匹配产物规则时抽取                             |

### Create 简写工序不填写主工件

对 `.node(id, rawJson, options)` 中下文已适配的 Create 简写类型，系统会自动插入主输入和主输出。`ingredients` **只写辅料**，`results` **只写副产物**，不要重复写当前工件。附加字段由各自的原生 codec 解释。完整原生模板见下节；工作台、熔炉等节点仍按各自小节填写。

入口 `.ingredient(...)` 和节点选项 `ingredient` 可使用物品 ID、标签字符串、`Ingredient.of(...)` 或原生 Ingredient JSON，例如 `'minecraft:iron_ingot'`、`'#c:ingots/iron'`、`{ tag: 'c:ingots/iron' }` 旧式 `recipe` JSON 中的辅料仍按原生格式填写，数量简写与 `Fluid.of(...)` 等将交给上游配方构建器解析。

简单结算可写 `.output('2x minecraft:gold_ingot')` 或 `.output(Item.of(...))`，保留数量及物品组件。需要条件、权重、概率或同时产出时，继续使用下文的结算规则与嵌套组。节点 `outputs`、`scrap` 中的物品条目也支持上述简写。

注意不要混用两种产物字段：

| 所在位置 | 格式 | 概率含义 |
|---|---|---|
| 工序 `recipe.results` | `{ "id": "minecraft:flint", "count": 1, "chance": 0.25 }` | Create 原生副产物概率 |
| 图结算 `results` / `scrap` | `{ "item": "minecraft:gold_ingot", "count": 1, "weight": 3, "chance": 0.5 }` | 外层按权重选条目，再对物品逐个概率判定；也支持嵌套组 |

### 可选：复用上游配方构建器

`node()` 可直接嵌入本次 `ServerEvents.recipes` 中新建的上游配方对象。普通合成、熔炉、切石、锻造等使用 KubeJS 自带方法；下面的 Create 方法另需安装 KubeJS Create：

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient('minecraft:iron_ingot')
        .node('press', event.recipes.create.pressing(
            'minecraft:iron_nugget', 'minecraft:iron_ingot'), { start: true })
        .node('cut', event.recipes.create.cutting(
            'minecraft:iron_nugget', 'minecraft:iron_nugget').processingTime(40), { finish: true })
        .edge('press', 'cut')
        .output('minecraft:gold_ingot');
    event.custom(graph.build()).id('my_pack:graph/native_builder');
});
```

这是完整配方写法：输入包含主件，输出包含代表主件的产物。加工时依照已有图规则替换主物品位置，其余辅料、副产物、流体和原生参数保留；最终产物仍由图结算决定。工序快照不会独立注册为普通配方，后续修改上游构建器也不改变已经嵌入的节点。修改链式参数应放在 `.node(...)` 之前。

完整原生 JSON 使用 `.nativeNode(id, json, options)`，并以 `recipe_mode: 'native'` 标记。此模式使用完整定义，不补入主输入；Create 逻辑物品输入 0 仍由当前工件替换。缺省或 `'step'` 沿用简写约定，原 JSON 简写仅适用于 `step`。已有普通配方继续用 `{ type, recipe_id }` 引用，不从 `event.forEachRecipe` 取得对象后交给节点。

可复用 `.processingTime()`、`.heated()`、`.superheated()`、`.keepHeldItem()`、`.xp()`、`.cookingTime()` 等原生参数。普通 `shaped` / `shapeless` 会转为原版合成；`keepIngredient`、`damageIngredient`、`modifyResult`、`noMirror`、`noShrink` 等特殊行为本轮不承接，使用时明确报错。`CreateItem.of(..., chance)` 用于原生工序副产物，不作为图结算简写。

完整模板不扩大执行支持范围；不支持的设备仍只能预览。仅需 KubeJS 的完整示例见 [native_recipes.js](../../examples/kubejs/native_recipes.js)。

### 可选：注册专用工件

正式整合包可用专用物品区分普通材料与工件。下面是**另一个文件**，放入 `kubejs/startup_scripts/graph_assembly_items.js`，修改后重启游戏或服务器：

```javascript
StartupEvents.registry('item', function (event) {
    event.create('graph_workpiece');
});
```

随后把配方中的 `GraphAssembly.create('minecraft:iron_nugget')` 改为 `GraphAssembly.create('kubejs:graph_workpiece')`。注册语句只负责物品存在，名称和贴图另行配置；物品一旦携带图进度，其工件堆叠上限由图装配限制为 1。

## 4. 完整加工链与设备限制

下面的示例中包含冲压、机械手、注液、切割和磨粉，并展示辅料、副产物与加工标记：

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({ item: 'minecraft:copper_ingot' })
        .node('press', {
            type: 'create:pressing'
        }, { start: true })
        .node('install', {
            type: 'create:deploying',
            ingredients: [{ item: 'minecraft:iron_nugget' }],
            keep_held_item: false
        }, { rewards: { precision: 1 } })
        .node('wash', {
            type: 'create:filling',
            ingredients: [{
                type: 'neoforge:single',
                fluid: 'minecraft:water',
                amount: 250
            }]
        }, { checkpoint: true })
        .node('cut', {
            type: 'create:cutting',
            processing_time: 40,
            results: [{ id: 'minecraft:flint', count: 1, chance: 0.25 }]
        }, { rewards: { precision: 1 } })
        .node('mill', {
            type: 'create:milling',
            processing_time: 100
        }, { finish: true })
        .edge('press', 'install')
        .edge('install', 'wash')
        .edge('wash', 'cut')
        .edge('cut', 'mill')
        .output({
            requirements: {
                mode: 'partial',
                marks: { precision: 2 },
                checkpoints: ['press', 'wash', 'mill']
            },
            results: [{ item: 'minecraft:gold_ingot', count: 2 }]
        });

    event.custom(graph.build()).id('my_pack:graph/five_steps');
});
```

`wash` 是自定义节点名，这里实际使用**注液器**，不是鼓风机洗涤。注液消耗 250 mB 水；`processing_time` 是原生配方处理时间参数。

### 当前可执行类型

以下为 Create 加工类型，数量均**不包含自动插入的主工件及主输出**：

| `recipe.type` | 设备 | 物品辅料 | 流体辅料 | 额外物品输出上限 |
|---|---|---------:|---------:|---:|
| `create:pressing` | 动力冲压机 |        0 |        0 | 1 |
| `create:cutting` | 动力锯 |        0 |        0 | 3 |
| `create:deploying` | 机械手 |        1 |        0 | 3 |
| `create:filling` | 注液器 |        0 |        1 | 0 |
| `create:milling` | 石磨 |        0 |        0 | 3 |
| `create:mixing` | 搅拌盆 |  最多 63 |   最多 2 | 3 |
| `create:compacting` | 盆压缩 |  最多 63 |   最多 2 | 3 |

- 机械手支持 `keep_held_item`；`true` 表示保留手持辅料，具体工具行为仍遵循 Create。
- 磨粉必须填写大于 0 的 `processing_time`。
- 注液不能配置副产物；当前执行适配不接受流体主工件、流体结算产物或原生流体输出。
- 搅拌／盆压缩的原生物品输入上限为 **64（含主件，所以最多 63 个辅助物品 Ingredient）**，物品输出上限为 **4（含主产物，所以最多 3 项副产物）**，最多 2 个辅助流体输入。此为配方容量，不是说盆有 64 个物理库存槽；实际匹配还受库存与输出空间限制。
- 盆内联 `ingredients` 可混合物品与流体；保留原生 `heat_requirement`，例如 `'heated'`、`'superheated'`。需要热量时必须实际提供对应热源，图配方不会绕过它。
- 超过设备容量的配方会禁用整张图的执行，不会静默忽略多余材料。
- 设备会检查输出空间；动力锯的大批量加工可能因最坏输出数量超过容量而等待，需要上游分批投入。

附属配方沿用第 4 节所列受支持的原生类型时，按相同规则校验；自定义加工类型不启用图执行。Create 对标准原版配方的替代设备接纳见下表。任意原生配方的**图内预览**本身都不构成执行支持。

### 使用 Create 的替代设备

同一个标准原版图节点可交给对应 Create 设备，无需复制节点或改配方格式：

| 原版节点类型 | 可用的 Create 设备 |
|---|---|
| `minecraft:crafting_shapeless` | 搅拌器＋工作盆：材料数大于 1，且不是当前可压缩配方 |
| 标准合成中的 4 或 9 个等价材料 | 动力冲压机＋工作盆：按原始材料判定压缩资格，再替换主工件 |
| `minecraft:stonecutting` | 动力锯 |
| 标准有序／无序合成 | 机械合成器 |
| `minecraft:smoking` | 鼓风烟熏 |
| `minecraft:smelting`／`minecraft:blasting` | 鼓风熔炼：先找熔炼配方，再找高炉配方 |

锯、工作盆和机械合成器遵守各自的 Create 配置开关；所有路径都遵守对应设备的自动化排除规则，引用配方按原 ID 判断。鼓风沿用原生介质、计时和食品判定，一次处理不会连续跳过多个节点。营火配方不是鼓风烟熏来源。

原生 `result` 仍须填写；它是解码模板，不会在图产物之外额外发放。完整条件见[执行兼容性](../reference/compatibility.zh_cn.md#create-的原版替代设备)。

### 背包与工作台合成

标准 `minecraft:crafting_shaped`、`minecraft:crafting_shapeless` 可作为图节点，分别受背包 2×2、工作台 3×3 的尺寸限制。使用完整原生配方 JSON，或用 `{ type, recipe_id }` 引用已有配方；**不自动追加主材料**。主件占原配方的逻辑输入 0，由图入口原料或当前工件替换。

- 有序合成按未镜像图案从左到右、从上到下给非空格编号，重复字符逐格编号。镜像和平移不改变角色编号。
- 无序合成按 `ingredients` 数组编号。每个非空输入格扣除 1 件，单格堆叠不能替代多个配方格。
- 辅料默认是普通物品；带进度的辅助工件须在 `workpiece_inputs` 声明，与主件同图且能合法进入本节点。只继承主件进度。
- 原生 `result` 仍须填写以供原生 codec 解码，实际结果由图的工件与出口结算规则决定。

例如下列节点片段需要前一步工件与木棍：

```javascript
.node('assemble', {
    type: 'minecraft:crafting_shaped',
    pattern: ['A B'],
    key: {
        A: { item: 'minecraft:iron_nugget' },
        B: { item: 'minecraft:stick' }
    },
    result: { id: 'minecraft:iron_nugget' }
})
```

这里 A 是主角色 0，B 是辅料角色 1，中间空格不编号。三格宽的图案只能放进工作台。

左右键取一整轮，第一堆产物到鼠标，其余到背包；Shift 连续合成。返还物先回原格，再放背包；容量不足时保留输入。数字键／副手换位要求目标槽为空；图预览不支持中键复制和 Q 键直接丢出。

染色与修理的特殊图步骤见下文；其他特殊合成及第三方菜单尚未接入。

### 原版合成器

标准 `minecraft:crafting_shaped`、`minecraft:crafting_shapeless` 图节点也可在原版合成器执行，无需另写一种配方。使用上文相同的完整原生 JSON 或 `{ type, recipe_id }` 引用，沿用主辅工件校验；逻辑输入 0 为主工件，不是固定的物理槽号。

每次红石上升沿加工一轮；禁用槽沿用原版规则。产物和原生返还物经合成器前方送入容器，放不下的部分按原版行为弹出。返还物不回填输入网格。支持标准有序／无序配方及下文所列两类特殊配方；其他特殊合成、自定义配方子类或附属机器不在范围内。

### 原版染色与修理

背包、工作台及原版合成器支持确切原生 `ArmorDyeRecipe`（`minecraft:crafting_special_armordye`）和 `RepairItemRecipe`（`minecraft:crafting_special_repairitem`）。沿用原生匹配与结果，不新增格式版本。

染色时被染物是唯一主工件（逻辑输入 0）；每个非空染料格按从左到右、从上到下编号为辅助输入 1、2……。修理时第一件非空物品为主工件，第二件为辅助工件；分别校验，只有主件进度被继承。原生修理会移除名称和普通附魔；诅咒与耐久按原生结果处理。

辅助物品若已有图进度，仍须在 `workpiece_inputs` 中声明对应角色，并能进入当前节点；未声明的辅料只能使用普通物品。

非 finish 节点以原生结果作为工件载体并推进图；图级默认载体不覆盖它。finish 按图产物结算，不额外发放原生结果。此两类节点不允许设置 `transitional_item`。

```javascript
.node('dye', { type: 'minecraft:crafting_special_armordye', category: 'misc' })
.node('repair', { type: 'minecraft:crafting_special_repairitem', category: 'misc' })
```

Create 的特殊图步骤未开放；其他特殊合成未开放。

### 酿造台

酿造节点使用 `graph_assembly:brewing`，药水效果由原生酿造规则决定。`primary` 选择谁提供图进度；`addition` 限定另一个输入：

| `primary` | 输入 0（主件） | 输入 1（辅件 / `addition`） |
|---|---|---|
| `bottle`（默认） | 单瓶 | 共用酿造材料 |
| `ingredient` | 共用酿造材料 | 单瓶 |

```js
// 瓶子保留自己的进度，以地狱疣酿造
.node('brew_from_bottle', {
  type: 'graph_assembly:brewing',
  addition: { item: 'minecraft:nether_wart' }
})

// 材料把进度分发给所有符合条件的瓶子
.node('brew_from_ingredient', {
  type: 'graph_assembly:brewing', primary: 'ingredient',
  addition: { item: 'minecraft:potion' }
})
```

每瓶独立推进和抽取下一步／结算产物，共用材料只消耗一份。材料主件模式中，各瓶从同一材料进度出发，后续互不影响；瓶主件模式中，各瓶保留各自历史。瓶子和带进度材料都须为一件。辅件若也带进度，须声明 `workpiece_inputs: [{ item_input: 1 }]`，双方分别校验，只继承主件进度。

任一带进度输入不满足角色、路径或原生酿造条件，整轮不加工。材料主件模式中，未被图认领的普通瓶保持不变；瓶主件配普通材料时，可混合的普通瓶仍按原生规则酿造。

中间步骤保留原生药水结果及组件，图默认载体不覆盖它，节点不能设置 `transitional_item`。finish 每瓶可结算为空或产出一件物品；**每一种可能结果**都须符合此容量，超出时整轮不加工，不截断或掉落图产物。燃料、时长与材料返还物沿用原版。描述符不提供 `recipe_id`、`result`、费用或时间字段。

### 铁砧

铁砧图节点使用 `graph_assembly:anvil`，在原版铁砧界面操作。它描述要完成的操作，不创建新的原生配方，也不使用 `recipe_id`。

| `action` | 要完成的操作 |
|---|---|
| `work`（默认） | 修理或附魔增强，不含仅改名 |
| `repair` | 修理 |
| `enchant` | 附魔增强 |
| `rename` | 改名；右槽须为空 |
| `combine` | 宿主已有的合法双输入组合；仅改名或修改修理费用不算 |

`addition` 可用 Ingredient 限定右槽材料，`rename` 不使用此字段。`name` 只用于 `rename`：指定精确目标名，空字符串移除名称，省略则接受任意实际改名。

```javascript
.node('repair', {
  type: 'graph_assembly:anvil', action: 'repair',
  addition: { item: 'minecraft:iron_ingot' }
}, { start: true })
.node('finish', {
  type: 'graph_assembly:anvil', action: 'rename', name: '修复完成'
}, { finish: true })
.edge('repair', 'finish')
```

例如以受损铁剑入图：先用铁锭修理，再改名为“修复完成”进入 finish。图入口、默认载体和结算产物仍按通常方式声明。

左槽固定为逻辑输入 0 主工件，右槽为逻辑输入 1 辅助工件。两件带进度物品须分别声明、分别校验，结果只继承左件进度；主件与原生结果均须为一件，带进度辅件每次只能消耗一件。普通材料按原生规则扣除。

结果、等级费用和材料消耗沿用宿主规则。中间步骤保留原生结果，图默认载体不覆盖它；节点级 `transitional_item` 不可用。finish 按图结算，不额外发放原生装备。

### 熔炉、烟熏炉与高炉

标准 `minecraft:smelting`、`minecraft:smoking`、`minecraft:blasting` 分别交给原版熔炉、烟熏炉和高炉执行。使用完整原生 JSON 或 `{ type, recipe_id }`；逻辑输入 0 是唯一主工件，燃料不算辅料，不能声明第二个工件角色。

- `cookingtime` 是加工所需 tick，须大于 0；`experience` 是每轮成功加工的经验，须为有限非负数。燃料时长、熄火冷却、空桶返还沿用原版规则。
- 原生 `ingredient` 的位置交给图入口原料或当前工件；`result` 仍须填写供原生解码，但实际产物来自图规则，不额外发放原生结果。
- 炉子只有一个输出槽。**每一种可能的产物组合都要能完整放入**，不能只挑当前放得下的结果。组合内相同物品、相同组件可以合并；同一轮同时输出不同物品或超出堆叠上限时停工。随机选一的不同产物可在空槽时正常结算。
- 满槽不会消耗本轮原料或抽签；已点燃的燃料仍按原版继续燃烧。完成的图工件可用漏斗取出。
- 特殊配方子类及附属自定义炉子尚未接入。

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({ item: 'minecraft:raw_iron' })
        .node('smelt', {
            type: 'minecraft:smelting',
            ingredient: { item: 'minecraft:raw_iron' },
            result: { id: 'minecraft:iron_ingot' },
            experience: 0.7,
            cookingtime: 200
        }, { start: true, finish: true })
        .output({ results: [{ item: 'minecraft:iron_nugget' }] });
    event.custom(graph.build()).id('my_pack:graph/raw_iron');
});
```

这个例子消耗粗铁和燃料，结算得到铁粒；原生 `result` 中的铁锭不会再发一份。

### 营火与灵魂营火

标准 `minecraft:campfire_cooking` 可在营火或灵魂营火执行。节点条件 `campfire: "normal"` 限定普通营火，`campfire: "soul"` 限定灵魂营火；省略或填写 `"any"` 时两者均可。条件与 `recipe` 同级，不写进原生配方，只影响图步骤，不改变普通食物配方。完整原生 JSON 或 `{ type, recipe_id }` 引用仍沿用原生 `cookingtime`。

```js
.node("soul_cook", {
  type: "minecraft:campfire_cooking",
  ingredient: { item: "minecraft:potato" },
  result: { id: "minecraft:baked_potato" },
  cookingtime: 600
}, { campfire: "soul" })
```

同一种原料可分别设置 `normal` 与 `soul` 入口，按放入的营火进入不同分支。已入图的工件仍需符合自己的路线；放错营火不会消耗工件或退回普通加工。

四个格位可各自处理一件物品。每道工序只有逻辑输入 0 这一主工件，不能声明辅工件。熄灭时按原版降温，重新点燃后继续计时；完成后掉落完整图结算结果，不受熔炉单输出槽限制。营火不消耗燃料物品、不发熔炉经验，也没有原生漏斗库存输入。换件或图／引用定义变化后重新计时；特殊配方子类和附属自定义营火不在此适配范围内。

### 切石机

标准 `minecraft:stonecutting` 节点使用完整原生 JSON（包括 `result`），或严格 `{ type, recipe_id }` 引用。逻辑输入 0 是唯一主工件，不声明辅助工件；原生材料位置由图入口原料或当前工件替换，实际产物来自图规则。

- 在切石机原生列表中选择可执行的图步骤；多个图或节点可以分别选择。
- 左右键完成一整轮，第一堆到鼠标，其余到背包；Shift 在输入和容量允许时逐轮加工。每种可能产物都须有空间，不能丢弃或重抽来适应空位。
- 工件进度或配方定义变化后重新选择；关闭菜单时按原版行为返还输入。

节点示例：

```javascript
.node('cut', {
  type: 'minecraft:stonecutting',
  ingredient: { item: 'minecraft:stone' },
  result: { id: 'minecraft:stone_slab' }
}, { start: true })
```

`result` 供原生解码。此处介绍原版切石机菜单；相同标准节点也可由 Create 动力锯执行。

### 锻造台

标准 `minecraft:smithing_transform` 可在原版锻造台执行，使用完整原生 JSON 或 `{ type, recipe_id }` 引用。主工件是中间的基底：逻辑输入 0 对应基底，1 对应左侧模板，2 对应右侧附加材料。

三格各消耗一件。模板或附加材料可通过 `workpiece_inputs` 声明为辅助工件；双方分别校验图与路线，产物只继承主工件进度。原生 `result` 仅供解码，不在图产物之外另发一份升级结果。

```javascript
.node('upgrade', {
  type: 'minecraft:smithing_transform',
  template: { item: 'minecraft:netherite_upgrade_smithing_template' },
  base: { item: 'minecraft:diamond_sword' },
  addition: { item: 'minecraft:netherite_ingot' },
  result: { id: 'minecraft:netherite_sword', count: 1 }
})
```

图入口原料或当前工件替换 `base` 条件；模板与附加材料仍按原生条件匹配。左右键领取完整一轮，Shift 按容量逐轮加工。

标准 `minecraft:smithing_trim` 也可执行图步骤，输入角色和三格扣料规则相同，但**不替换原生 `base` 条件**：当前工件必须能完成这次纹饰。

```js
{
  type: 'minecraft:smithing_trim',
  template: { item: 'minecraft:coast_armor_trim_smithing_template' },
  base: { item: 'minecraft:diamond_chestplate' },
  addition: { item: 'minecraft:amethyst_shard' }
}
```

- 中间步骤保留纹饰后的盔甲、名称、耐久和附魔，再附上主工件的新进度；图级 `transitional_item` 不会把它替换成其他物品。
- 纹饰节点不得设置节点级 `transitional_item`。
- 已有相同图案和材质时，按原版视为无变化，不消费材料、不推进。
- finish 仍须能完成原生纹饰，然后按图结算，不额外发放盔甲。

其他动态合成、未知配方子类和附属锻造菜单不随此项开放。

## 5. 构建器 API

除 `build()` 外，以下方法均返回构建器，可继续链式调用：

| 方法 | 参数与作用 |
|---|---|
| `GraphAssembly.create(transitionalItem)` | 创建新图；传入默认工件的物品 ID 字符串 |
| `.ingredient(value)` | 设置默认入口 Ingredient，接受 ID、标签、Ingredient 对象或 JSON；再次调用替换先前值 |
| `.node(id, recipe)` | 添加节点，使用默认选项 |
| `.node(id, recipe, options)` | 添加节点；`recipe` 接受旧式 JSON 或新建的上游 KJS 配方对象，`options` 为节点选项 |
| `.nativeNode(id, recipe, options)` | 嵌入完整原生配方 JSON；`options` 可省略 |
| `.edge(from, to)` | 添加一条权重为 1 的有向边；可用第三个参数传 `options`，如 `{ weight: 2 }` 或 `{ waypoints: [[x, y]] }` |
| `.weightedEdge(from, to, weight)` | 添加正数权重的有向边 |
| `.bidirectionalEdge(from, to)` | 添加 `from → to` 与 `to → from` 两条边 |
| `.output(rule)` | 追加图级结算规则，也接受物品字符串、ItemStack 或等权候选数组，可多次调用 |
| `.build()` | 校验图结构并返回 JSON 副本；**不会自行注册配方** |

最后必须调用 `event.custom(graph.build()).id('命名空间:路径')` 注册。

注意：

- 节点 ID 在同一张图内必须唯一；边与条件中引用的节点必须存在。
- 每张图至少有一个 `start: true` 的入口；入口需要自己的原料或图级默认原料。
- 同一对节点不能重复添加相同方向的边。自环使用 `.edge('loop', 'loop')`，不要用同名端点调用 `bidirectionalEdge`。
- 权重必须是有限正数，不能是 0、负数、`NaN` 或 `Infinity`。
- `build()` 校验图格式与引用关系；物品、原生配方解码、设备适配和冲突还会在游戏加载配方时检查。通过 `build()` 不等于设备一定能执行。
- 构建器不生成 `format_version`；没有 `formatVersion()`、`chance()`、`requirements()` 等额外链式方法。条件放进 `.output(...)`，节点配置放进 `.node(...)` 的第三个参数。

## 6. 节点选项

`.node(id, recipe, options)` 的第三个参数支持：

| 字段 | 默认 / 行为 |
|---|---|
| `start` | `false`；入口标记 |
| `recipe_mode` | 缺省或 `'step'` 沿用简写约定；`'native'` 表示完整原生模板，不补主输入；上游配方对象及 `nativeNode()` 自动填写 |
| `finish` | `false`；出口标记，可与 `start` 同时为 `true` |
| `ingredient` | 入口继承图级原料；可为不同入口单独指定；非入口不用于下一步匹配 |
| `transitional_item` | 首次加工默认用图级工件，后续默认保留当前物品；显式值替换成功加工后的工件物品 |
| `rewards` | `{}`；成功加工增加的标记，如 `{ precision: 1 }` |
| `reward_limit` | 不填写为无限；限制**此节点为每个工件发放奖励的次数**，不是限制加工次数；0 表示不发奖 |
| `checkpoint` | 普通节点默认 `false`；入口和出口始终记录检查点，即使填写 `false` |
| `branch_mode` | `'choice'`；也可为 `'random'`，控制本节点加工完成后的出边 |
| `finish_mode` | `'lenient'`；也可为 `'strict'`，控制出口未匹配任何结算规则时的行为 |
| `outputs` | `[]`；本节点作为当前出口时加入的结算规则，不覆盖图级规则 |
| `scrap` | `[]`；严格失败废品池，格式同图结算的 `results` |
| `workpiece_inputs` | `[]`；声明按原生物品逻辑输入编号识别的工件角色，见下节 |
| `progress_actions` | `[]`；成功加工后按顺序修改主工件进度，见下节 |
| `position` | 自动布局；可写 `[x, y]`，平面画布中心为原点，x 向右、y 向下 |
| `sphere_position` | 自动球面布局；可写非零 `[x, y, z]`，归一化为球面方向 |

标记名由作者自行定义，不需要提前注册。奖励和上限必须是 `0..2147483647` 的整数；累计奖励将溢出时，该步骤会在消耗材料前停工，不会回绕或截断。

布局坐标只影响预览，不决定加工顺序。加工必须符合当前合法路线，双工件须分别检查：已保存的指定路线（`pending_route`）优先；没有指定路线时遵守随机锁，没有锁时才按普通出边。`progress_actions` 的改道可覆盖普通出边或随机抽签，但目标自己的输入条件仍需满足。宽容出口未匹配结算规则时保留加工后的工件进度，并优先遵循指定路线；没有指定路线时才按出边继续，不能回滚到加工前状态。成功加工后的顺序为奖励 → 追加检查点 → `progress_actions` → 出口结算 → 未结算路线处理。

### 多工件盆合并示例

将仓库的完整脚本 [`examples/kubejs/multi_workpiece.js`](../../examples/kubejs/multi_workpiece.js) 单独复制到 `kubejs/server_scripts/`，执行 `/reload`。它使用既有 `.node(..., options)` 和 `.output(...)`，不需要新 Builder 方法。该示例已在真实 KubeJS 环境加载；不要与入口条件重叠的图同时启用。

实际准备和加工两件**同图**工件：

1. 金锭经冲压进入 `body`，生成铁粒主工件，`quality = 2`。
2. 紫水晶碎片经切割进入 `insert`，生成另一件铁粒辅助工件，`quality = 99`。
3. 主件可以直接去 `join`，或先用机械手消耗石英完成一次 `polish`；抛光会记录检查点并使主件 `quality = 4`。
4. 把主件和 `insert` 辅件一同放入盆，经搅拌完成 `join`。两件都按原生规则消费，主产物只承接主件进度；辅助件的 99 不加到主质量里。
5. 主产物再经冲压到 `finish`：未抛光路线得到绿宝石；恰好抛光一次的路线得到钻石。

合并角色写在 `.node` 的**第三个参数**，不是 `recipe` 内：

```javascript
.node('join', {
    type: 'create:mixing',
    ingredients: [{ item: 'minecraft:iron_nugget' }]
}, {
    checkpoint: true,
    workpiece_inputs: [
        { item_input: 0, current_nodes: ['body', 'polish'] },
        { item_input: 1, current_nodes: ['insert'],
          requirements: { mark_ranges: { quality: { min: 99, max: 99 } } } }
    ]
})
```

这里 `ingredients` 的铁粒是辅助件 Ingredient；Create 盆配方的主件由系统自动补入。此处 `item_input` 是**补入主输入后，经原生 codec 解码所得的物品 Ingredient 逻辑序号**：0 永远是主件，1 是第一个辅助物品，**不是盆的物理 slot 编号，也不是混合物品／流体 JSON 数组的原始下标**。流体不计入这个编号。原版合成的编号见上文“背包与工作台合成”。

- 每个逻辑编号最多声明一次，不能超出原生配方实际物品输入数。原生 Ingredient 仍要匹配；角色不是替代 Ingredient 的另一套物品选择器。
- 声明角色必须携带有效进度、属于目标**同一张图**，且当前状态的合法路线允许进入 `join`：优先检查已保存的指定路线（`pending_route`），没有指定路线时检查随机锁或普通出边。`current_nodes` 或条件不自行授予路线；目标仍须实际成功加工并满足适用的输入、机器和结算条件。
- `current_nodes` 可省略，表示不增加“当前节点在列表内”的限制；提供时必须非空、无重复且引用本图节点。即使省略，也必须符合当前的指定路线／随机锁／普通出边规则。
- 角色 `requirements` 针对该件输入自己的标记和完整检查点。普通未声明辅助输入不得携带图进度，不能偷偷拿另一张图或不合格工件顶替普通材料。
- 角色 0 可不显式声明，主工件仍遵循正常遍历规则；显式声明角色 0 后必须使用已入图工件，不能用普通入口原料充当它。
- 只有主件推进后的进度进入主产物；辅助件的标记、检查点、奖励次数和锁不合并，副产物不克隆图进度。保留还是消费仍遵循机器原生规则，例如机械手 `keep_held_item`。

该脚本的两个出口条件分别要求：

| 主路线 | 主质量范围 | `polish` 检查点次数 | 结算 |
|---|---|---|---|
| `body → join → finish` | `min: 2, max: 2` | `max: 0` | 绿宝石 |
| `body → polish → join → finish` | `min: 4, max: 4` | `min: 1, max: 1` | 钻石 |

脚本还用原有 `checkpoints` 的 `partial` 条件要求有序经过主路线。数量范围与旧条件同时成立才结算，不会因为只达到质量就忽略路线；字段规则见第 9 节。

### 加工后清除与改道

节点第三个参数可写 `progress_actions`。例如在返工成功后扣除 2 个缺陷标记，让 `inspect` 重新成为下一步：

```javascript
.node('rework', {
    type: 'create:milling', processing_time: 40
}, {
    progress_actions: [
        { type: 'clear_mark', mark: 'defect', count: 2 },
        { type: 'goto_node', node: 'inspect' }
    ]
})
```

这是构建器片段，`inspect` 必须在同一张图中定义。省略 `clear_mark.count` 清空该标记；填非负整数则扣除相应数量，不足清零，0 不变。

其他操作：

- `{ type: 'clear_checkpoint', node: 'inspect' }`：删除 `inspect` 的全部检查点记录。
- `{ type: 'pop_checkpoint' }`：删除最近一条检查点记录，空列表不变。
- `{ type: 'enter_edge', from: 'inspect', to: 'finish' }`：选择已有的 `inspect → finish` 边，下一步加工 `finish`。不执行 `inspect`，也不补记它的进度。

每节点最多一个 `goto_node` 或 `enter_edge`。加工成功后先发放奖励、追加本次检查点，再按列表顺序操作，最后结算；已结算则不再改道。Start / finish 自动记检查点，所以在它们上面执行 `pop_checkpoint` 删除的是本次记录。

改道会覆盖普通出边和随机抽签，但不豁免目标输入条件。入口、近期加工历史、奖励授予次数保持原值，不退还材料；双工件只修改主工件。目标的 `current_nodes` 检查真实刚完成的节点，而非所选边的起点。`goto_node` 只指定下一工序，不等于跳过目标的加工、入口材料或结算条件。

完整数据包示例见 [progress-actions.json](../../examples/datapack/progress-actions.json)，放到 `data/my_pack/recipe/progress_actions.json` 后可单独测试：

1. 幻翼膜冲压 → 切割检查 → 直接冲压出口：缺陷为 3，得到燧石废品。
2. 检查后先磨粉返工：缺陷扣到 1，清除旧检查记录与最近检查点；随后必须重新切割检查，再冲压得到钻石。
3. 检查后由机械手用红石修复：清空缺陷，进入 `inspect → finish` 边；直接冲压得到钻石。

工件会保存指定路线；运输、过滤和读档不重复执行操作。目标或边被删除、来源节点改道配置变化时停工并保留工件。详细字段见[配方格式](../reference/recipe-format.zh_cn.md#加工后进度操作)。

## 7. 选择分支

`choice` 表示后继工序都可选，通常由实际送往的设备和材料决定；切石机则沿用原生配方列表选择步骤。下面两条分支都使用机械手，通过不同辅料区分：

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({ item: 'minecraft:andesite' })
        .node('start', { type: 'create:pressing' }, { start: true })
        .node('iron_route', {
            type: 'create:deploying',
            ingredients: [{ item: 'minecraft:iron_nugget' }]
        }, { finish: true })
        .node('gold_route', {
            type: 'create:deploying',
            ingredients: [{ item: 'minecraft:gold_nugget' }]
        }, { finish: true })
        .edge('start', 'iron_route')
        .edge('start', 'gold_route')
        .output({
            finish: 'iron_route',
            results: [{ item: 'create:andesite_alloy' }]
        })
        .output({
            finish: 'gold_route',
            results: [{ item: 'minecraft:gold_ingot' }]
        });

    event.custom(graph.build()).id('my_pack:graph/choice');
});
```

不要给同一个 `choice` 节点连接两条条件无法区分的工序，例如两个没有辅料的冲压步骤。节点名不同、坐标不同或处理时间不同，都不能充当设备选择条件；可静态识别的歧义会禁用整张图。

同样，不同图的入口若在同一设备上接受相同原料与辅助条件，也会冲突。发布示例时使用不同入口原料，或仅启用其中一个。

## 8. 随机分支、循环与奖励上限

随机模式写在**出边源节点**上。权重决定下一步，而不是最终产物概率。

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({ item: 'minecraft:clay_ball' })
        .node('start', { type: 'create:pressing' }, { start: true })
        .node('polish', {
            type: 'create:cutting', processing_time: 40
        }, {
            rewards: { quality: 1 },
            reward_limit: 3,
            checkpoint: true,
            branch_mode: 'random'
        })
        .node('finish', { type: 'create:pressing' }, {
            finish: true,
            finish_mode: 'strict',
            scrap: [{ item: 'minecraft:dead_bush' }]
        })
        .edge('start', 'polish')
        .weightedEdge('polish', 'polish', 3)
        .weightedEdge('polish', 'finish', 1)
        .output({
            requirements: { mode: 'partial', marks: { quality: 3 } },
            results: [{ item: 'minecraft:diamond' }]
        });

    event.custom(graph.build()).id('my_pack:graph/random_loop');
});
```

每次完成 `polish` 后，按 3:1 抽取下一步：75% 再切割一次，25% 前往出口冲压。抽中后目标保存在工件中，查询 JEI、过滤器或重新加载配方不会重新抽签；需要把工件送往锁定目标对应的设备。

奖励最多发放三次，之后仍可继续循环。出口加工完成时，`quality >= 3` 得钻石，否则严格失败产生枯萎的灌木。循环不是定时自动执行：每次仍需设备完成加工。

## 9. 完成条件与产物结算

### 结算规则

`.output(rule)` 和节点选项 `outputs` 接受相同规则：

| 字段 | 作用 |
|---|---|
| `start` | 可选，限定该工件最初进入的入口节点 |
| `finish` | 可选，限定当前出口节点 |
| `requirements` | 可选，标记／检查点匹配、标记范围与检查点次数范围 |
| `results` | 必填、非空，候选产物数组 |

只有完成 `finish: true` 的节点才尝试结算。先增加该节点奖励、记录检查点，再执行 `progress_actions`，然后判断规则；条件可以包含当前出口和本次新增加的标记。若操作清除了相关标记或检查点，条件看到的是操作后的当前保留状态。

### 条件写法

以下是 `requirements` 的 JSON 片段，不是独立脚本；其中节点名必须在你的图中存在：

| 目标 | `requirements` |
|---|---|
| 不检查条件 | 省略，或 `{}` |
| 标记恰好一致 | `{ "marks": { "precision": 2 } }` |
| 标记达到下限 | `{ "mode": "partial", "marks": { "precision": 2 } }` |
| 检查点历史完全相同 | `{ "checkpoints": ["start", "wash", "finish"] }` |
| 按顺序经过，允许中间多出记录 | `{ "mode": "partial", "checkpoints": ["start", "finish"] }` |
| 只要求经过，不要求顺序 | `{ "checkpoints": { "mode": "unordered", "nodes": ["wash", "finish"] } }` |

同时配置 `marks` 和 `checkpoints` 时，两者都要满足。

- `exact` 是默认模式。标记不能多键或多数量；检查点要求与当前保留序列完全一致，不能多项、少项或顺序不同。检查点可被 `progress_actions` 清除，因此不是不可删的审计日志。
- `partial` 对标记表示最低数量；对检查点表示有序子序列。重复要求需要相应次数的历史记录。
- `unordered` 仅用于检查点的局部模式，检查是否包含所列节点，不按重复项计访问次数。
- 入口和出口自动进入检查点历史；普通节点只有 `checkpoint: true` 才进入。循环访问会保留重复检查点。
- `requirements: {}` 是关闭检查；`requirements: { marks: {} }` 默认是要求**没有任何标记**；`checkpoints: []` 默认要求空历史，通常在出口不可能成立。

局部模式可覆盖公共模式，例如：

```json
{
  "mode": "partial",
  "marks": { "mode": "exact", "values": { "precision": 2 } },
  "checkpoints": { "mode": "partial", "nodes": ["start", "finish"] }
}
```

### 标记范围、访问次数与禁止访问

`requirements.mark_ranges` 对指定标记的累计数量作区间检查；`requirements.checkpoint_counts` 对指定节点在**完整检查点序列**中的出现次数作区间检查。它们也能写在 `workpiece_inputs` 的角色 `requirements` 内。

例如以下条件要求质量在 2～4 之间、`join` 恰好记录一次、`polish` 从未留下检查点记录：

```json
{
  "mark_ranges": { "quality": { "min": 2, "max": 4 } },
  "checkpoint_counts": {
    "join": { "min": 1, "max": 1 },
    "polish": { "max": 0 }
  }
}
```

- 区间**包含两端**；每个区间至少提供 `min` 或 `max` 一端。省略下限是 0，省略上限是 `2147483647`；端点须为该范围内的整数，且下限不能高于上限。
- `quality: { min: 3 }` 是至少 3；`quality: { max: 3 }` 是 0～3；`{ min: 3, max: 3 }` 是恰好 3；`{ max: 0 }` 是必须为 0。缺失的标记或未记录的检查点按 0 计算。
- `checkpoint_counts: { polish: { max: 0 } }` 可用于“禁止抛光路线”，前提是 `polish` 确实设了 `checkpoint: true`。普通非检查点节点加工过也不产生检查点，不能靠计数判断是否经过。
- 次数统计当前保留的完整检查点序列（清除或弹出的记录不再计入），不是 Tooltip 的最近三步，也不是 `reward_limit` 的奖励次数。入口／出口自动计入，重复访问检查点会重复计数；检查点键必须引用图中存在的节点。
- 四类条件 `marks`、`checkpoints`、`mark_ranges`、`checkpoint_counts` 同时配置时取 **AND**。范围只约束列出的名称，不改变旧 exact / partial / unordered 语义，也不受公共 `mode` 影响。
- 省略条件就不检查。空范围映射如 `mark_ranges: {}` 不添加限制，但单个范围如 `quality: {}` 非法；这与旧 `marks: {}` 的默认 exact 空标记要求不同。

### 默认加权选一；用嵌套组表达同时与独立概率产出

图级规则与当前出口规则中，所有满足条件的 `results` 的**最外层条目**合并成一个加权池，选中一个条目后递归执行。每条规则不是独立抽奖，也不是先写的优先。普通物品条目的旧行为不变；组条目可以同时产出多种物品。

例如以下规则片段中，如果它是唯一匹配规则，则 75% 得两个金锭，25% 得一个铁锭：

```json
{
  "results": [
    { "item": "minecraft:gold_ingot", "count": 2, "weight": 3 },
    { "item": "minecraft:iron_ingot", "count": 1, "weight": 1 }
  ]
}
```

`count`、`weight` 默认均为 1。数量必须是正整数且能放进该物品的一个正常物品堆；可选 `components` 使用 Minecraft 1.21.1 原生物品组件 JSON，不是旧版 `nbt` 字符串。

固定 A，再独立按概率输出 B、C，应把它们放进一个 `all` 组，不是写成三条顶层规则：

```json
{"results": [{"mode": "all", "results": [
  {"item": "minecraft:diamond"},
  {"item": "minecraft:emerald", "count": 3, "chance": 0.5},
  {"item": "minecraft:gold_ingot", "chance": 0.25}
]}]}
```

- `all`：执行每个子条目。子条目的 `weight` 不影响是否执行。
- `weighted`：按子条目的局部权重选一个；选中的可以是物品，也可以是一整个组。`all` / `weighted` 可相互递归嵌套，不会被摊平成一个全局池。
- 物品 `chance` 与 Create 原生含义同步，范围为 `0..1`，默认 `1`。`count: 3, chance: 0.5` 独立判定三次，得到 0～3 个；不是 50% 得到整堆 3 个。各数量的概率也不是均匀分布。
- 组 `chance` 整组判定一次。若需要“50% 产出整堆 3 个”，写 `{"mode":"all","chance":0.5,"results":[{"item":"minecraft:emerald","count":3}]}`，只有 0 或 3 个结果。
- 组自身的 `weight` 仅在父级加权选取时使用；每层 `weighted` 单独归一化。`chance` 未命中不会改选兄弟条目，也不会重抽。
- 成功规则被选中但概率全部未命中，仍然结束图装配，不触发废品池。空 `all` 组也可显式表示无物品产出；空 `weighted` 组非法。
- `scrap` 支持完全相同的嵌套结构。只在严格出口没有匹配成功结果池时使用。
- 物品条目只允许 `item/count/weight/components/chance`；组只允许 `mode/results/weight/chance`。字段拼写错误会按具体 JSON 路径报错。
- 额外工序 `recipe.results` 仍是**独立于结算**的原生副产物，失败或成功概率为空时也不会取消。图结算中的物品继续用 `item`，不是原生 `id`。

可直接复制 [嵌套概率示例](../../examples/kubejs/nested_probability_outputs.js)：固定钻石、独立 0～3 个绿宝石与概率金锭，并附加 `weighted → all → weighted` 奖励。示例的成功规则无条件，因此其废品示范需给成功规则添加条件后才会触发。

**设备容量：** 同时产出的最大组合会在工作盆、石磨和切割中预留；空间不足保留输入，不通过重抽避开大组合。注液器是单物品堆 API：当前工件若可能结算出多个物品堆，该步骤不执行、不消耗物品或流体；把多产物出口放在冲压、机械手、工作盆或其他支持结果列表的设备上。普通注液步骤及单堆结算不受影响。

**JEI：** 候选数量合并显示，如 `0-3`；整组概率的离散数量保持 `0,3`，不误写成连续范围。候选图标代表产出条目，不是同物品多个奖励累加后的总数。Info 页列出组路径、局部权重和概率语义，悬浮说明保留所属组。

### 出口没有任何规则匹配时

- `lenient`：保留本次加工后的工件、奖励、检查点及 `progress_actions` 操作结果；有已保存的指定路线（`pending_route`）时优先按它继续，否则按随机锁／出口出边继续，只有既无指定路线也无出边时才等待，不能自动返回先前节点。目标仍需实际成功加工并满足相应条件。
- `strict`：消耗主工件，从 `scrap` 池抽取一个条目；未配置 `scrap` 时没有主产物。宽容出口不会使用 `scrap`。
- 工序副产物独立保留，不因结算失败而撤销。

## 10. 附属执行适配与完整原生配方预览

### 首发执行边界

执行范围见第 4 节。附属配方沿用受支持的原生类型时，按相同规则校验；附属自定义加工类型只能尝试通用 JEI 预览，不代表机器能执行图节点。

CCB、Fantasizing 专用执行适配已撤下，不再承诺相关机器保护含图进度工件。备用适配代码不纳入首发。

### 完整原生配方引用：预览与已验证执行路径

`recipe_id` 引用已加载的完整原生配方，按其自身 CODEC 保留附加字段，不猜测各附属的 JSON 格式。**只有已有执行适配的设备类型可以据此加工**；识别到 serializer、`ProcessingRecipe` 或 `IAssemblyRecipe` 不会自动授予执行能力。其他类型仍仅可预览。

以下完整脚本引用 Create 自带的注液配方，展示甜浆果和 250 mB 巧克力两个输入：

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:paper')
        .ingredient({ item: 'minecraft:sweet_berries' })
        .node('native_filling', {
            type: 'create:filling',
            recipe_id: 'create:filling/chocolate_glazed_berries'
        }, { start: true, finish: true })
        .output({ results: [{ item: 'create:chocolate_glazed_berries' }] });

    event.custom(graph.build()).id('my_pack:preview/chocolate');
});
```

`recipe_id` 指向已加载配方，不是物品 ID；`type` 必须与该配方的 **serializer ID** 相符，不一定等于 JEI 分类 ID。

引用模式与内联模式不同：

- 声明只允许 `type` 和 `recipe_id` 两个字段。附加条件应在被引用的原生配方中配置，不合并 `ingredients` 等覆盖字段。
- 展示保留原生 JEI 定义；执行使用独立原生副本，将逻辑物品输入 0 作为主工件、产物 0 替换为图进度／结算产物，其余原生材料、流体条件及副产物仍保留。原始配方不被修改。
- 图内使用原生 JEI 布局和材料提示。材料槽可以查询配方/用途，原生流体字段保留其类型与数量；没有附属模组专用倍率参数。
- 图底部的结算产物仍来自 `.output(...)`，不会自动复制原生配方输出。
- 已适配类型可用引用模式，仍须通过设备的角色、容量、流体产物等验证；不满足则整图禁用并输出原因。它不是任意机器自动兼容开关。
- 原生引用删除、同 ID 替换或数据包重载会撤销旧图操作。发布／改配方请使用正常数据包重载；直接替换 RecipeManager 不保证第三方私有配方索引会重建。
- 目标配方不存在、serializer 不匹配或所需模组未安装时，无法建立该引用；没有可匹配原生 JEI 布局时，节点保留不可用提示。

通用预览读取原生 JEI 布局，不按模组名称启用执行。原生布局缺失或创建失败时，节点保留不可用提示。

原生机器能在图内预览，不代表它已获得图执行适配。多工件合并仅限已明确适配路径并需角色声明，流体仍不能承载图进度。当前可执行范围为第 4 节的七种 Create 加工、背包／工作台与合成器标准合成、三种原版熔炉加工、营火、原版切石机、标准锻造、染色／修理／纹饰、原版铁砧及酿造台；其中满足第 4 节对应条件的节点也可由 Create 替代设备执行，包括鼓风烟熏与熔炼；范围见第 4 节。

## 11. 直接 JSON、修改与删除

### 不使用构建器

也可以直接使用 `event.custom`，与构建器生成的图采用同一格式：

```javascript
ServerEvents.recipes(function (event) {
    event.custom({
        type: 'graph_assembly:assembly',
        ingredient: { item: 'minecraft:iron_ingot' },
        transitional_item: 'minecraft:iron_nugget',
        nodes: [{
            id: 'press',
            start: true,
            finish: true,
            recipe: { type: 'create:pressing' }
        }],
        edges: [],
        outputs: [{ results: [{ item: 'minecraft:gold_ingot' }] }]
    }).id('my_pack:graph/direct_json');
});
```

此例与第 2 节使用同一原料和冲压入口，**二选一启用**。

### 开发期旧脚本清理

`format_version` 已取消，填写任何值都会报错，包括原先的 2。已使用 `requirements` 的脚本只需删除该顶层字段；构建器会自动生成无版本字段的 JSON。

更早的格式 1 还有条件层级与默认值差异：规则上的 `marks` / `checkpoints` 要移入 `requirements`；如果原来省略 `marks`，但仍希望保持“必须没有标记”，需显式添加 `requirements: { marks: {} }`。当前省略条件表示不检查，并不要求为空。完整说明见 [开发期旧文件清理](../reference/recipe-format.zh_cn.md#开发期旧文件清理)。本模组不自动转换旧配方或旧工件。

### 删除已有图

使用确切配方 ID 删除，例如删除本模组示例图：

```javascript
ServerEvents.recipes(function (event) {
    event.remove({ id: 'example:script_graph' });
});
```

修改既有图时，更新原脚本；替换数据包提供的图时，按 ID 删除再用相同 ID 注册完整图。不要依赖通用 `replaceInput` / `replaceOutput` 自动遍历并重写嵌套节点、条件和结算池。

直接使用数据包时，1.21.1 路径为 `data/<namespace>/recipe/<path>.json`，其中 `recipe` 是单数。数据包与 KubeJS 不要重复维护相同 ID 的不同版本。

## 12. 重载、排错与发布检查

### 调试顺序

1. 执行 `/reload`，确认 KubeJS 没有脚本加载错误。
2. 管理员执行 `/graph_assembly compatibility`，查看执行适配情况及禁用原因。
3. 检查实例/服务器的 `logs/latest.log` 与 `logs/kubejs/server.log`；搜索配方 ID、`Disabled graph`、`Ambiguous` 或具体字段路径。
4. 用**新入口原料**测试一次完整设备链，确认工件提示、辅料消耗与出口产物。
5. 安装 JEI 时，从原料用途或最终产物配方进入“图装配”；低倍率为点，放大后展示节点。结算说明在 JEI 原生 Info 页按产物关联，不在画布顶部放独立 Settlement 按钮。

### 常见问题

| 现象 | 检查方法 |
|---|---|
| `GraphAssembly is not defined` | 确认装的是本模组与对应 KubeJS 版本，脚本在服务器侧；查看插件加载日志 |
| `.build()` 报节点或字段路径错误 | 检查节点唯一性、边目标、入口原料及 `requirements` 层级；`graph_assembly:script_validation` 是构建器的校验名称，不是最终配方 ID |
| `graph.format_version: Remove format_version...` | 删除旧的顶层版本字段；如果来自早期格式 1，还需按第 11 节整理条件层级与默认值 |
| 能在 JEI 看见，设备不加工 | 检查是否为仅预览引用或不支持设备；查看兼容性命令，不以“能显示”判断可执行 |
| `Deploying requires exactly one auxiliary item ingredient` | 机械手的内联 `ingredients` 只填一个辅料，不填主工件 |
| `Spout requires exactly one fluid ingredient...` | 流体放在 `ingredients` 中，恰好一个，不填写副产物 |
| `Millstone requires processing_time > 0` | 补充正数 `processing_time` |
| `workpiece_inputs.item_input ... exceeds native item input count` | 使用解码后物品逻辑序号，0 主件；流体不计数，不能写物理 slot 下标 |
| 盆里有同物品工件却不合并 | 检查角色声明、同图、当前节点和条件；路线先检查 `pending_route` 的目标及来源操作配置（指定边还需存在），没有指定路线时再检查随机锁／普通出边；普通未声明辅料不接受进度组件 |
| `Ambiguous ... conditions` | 入口或选择分支无法区分；改用不同辅料/设备/入口，或在确实需要随机选择时设置源节点 `branch_mode: 'random'` |
| 达到出口仍是工件 | 检查 `finish`、条件、入口/出口限制；默认宽容失败会保留工件 |
| 检查点条件始终不满足 | 入口和出口也会记录；普通节点需 `checkpoint: true`；检查 `exact` 是否被额外循环记录破坏 |
| `checkpoint_counts` 的禁止访问条件没拦住路线 | 该节点是否开启检查点？计数看当前保留的完整检查点序列，不看普通近期历史；检查是否有 `clear_checkpoint` / `pop_checkpoint` 清除了记录，并检查其他 AND 条件 |
| 随机分支一直只允许某一步 | 正常行为，目标已锁定；查询和 `/reload` 不重抽 |
| 旧工件路线失效或停工 | 检查原配方、入口和当前节点；有 `pending_route` 时检查指定目标、指定边（若有）和来源节点的 `progress_actions` 配置是否仍一致，否则检查随机锁／普通出边。指定路线优先，但不绕过目标实际加工与条件；恢复相应 ID/结构，或用新原料测试新图 |

`/reload` 不会清空现有工件进度。发布更新时，改配方 ID、删除节点或变更随机连接都可能让旧工件无法继续；不要把配方重载当作工件迁移工具。

### 随整合包发布前

- [ ] 固定并说明 Minecraft、加载器、Create、KubeJS、Rhino 与可选 JEI 版本。
- [ ] 分发需要的 `server_scripts`；使用自定义物品时一并分发 `startup_scripts`、名称与贴图资源。
- [ ] 关闭演示脚本及相同入口的重复配方，替换示例命名空间与产物。
- [ ] 清理旧 `format_version` 字段；所有结算条件统一写在 `requirements` 内。
- [ ] 检查没有被禁用的生产图，逐条测试入口、分支、辅料、输出容量及出口条件。
- [ ] 多工件图分别测试两路制备、同图角色与错误角色拒绝；确认只有主质量／历史继承，范围端点和禁止访问条件符合预期。
- [ ] 将仅预览图与可执行配方分开说明，不把第三方预览列成设备执行兼容。
- [ ] 对已有存档的更新保持 ID 稳定，并验证旧工件能否继续。

### 一张图按路径产出三种物品

可直接复制 [三路径脚本](../../examples/kubejs/three_path_outputs.js)，操作流程和独立失败池见 [三路径说明](examples/three-path-outputs.zh_cn.md)。同一粗铁入口经切割、机械手或注液路线，分别在自己的严格出口结算铁锭、金锭或钻石；过度加工或次数不足进入对应出口的废品池。

`scrap` 绑定 finish，因此本例使用三个 finish，不新增条件废品字段。各 finish 的 `outputs` 检查有序路径、标记范围和 checkpoint 次数，图级 `outputs` 保持空，避免无条件规则绕过失败。UI 默认总览同时展示成功候选；选择 finish 后只显示该出口的成功候选和失败池，候选较多时通过 `+N` 展开。完整条件仍在 JEI 原生 Info 页。入口候选不是整条图的完整耗材表，辅工件／流体等请查看节点详情。

出口列表过长时，可拖动右侧 Create 原生滚动条、点击轨道或使用滚轮；候选物品弹层使用翻页按钮或 PageUp/PageDown。窗口缩放后仍保留选中的出口和图视角；弹层中的物品继续支持 JEI 原生配方/用途查询。

完整字段定义见 [配方格式](../reference/recipe-format.zh_cn.md)；脚本见 [基础示例](../../examples/kubejs/graph_assembly.js)、[条件结算示例](../../examples/kubejs/item_first.js)、[双工件＋范围条件](../../examples/kubejs/multi_workpiece.js) 与 [三路径＋独立废品池](../../examples/kubejs/three_path_outputs.js)。这些示例不自动启用；模组另外内置下述图过滤器教学配方。

## 13. 内置图过滤器：双工件教学配方

无需 KubeJS 或 JEI，模组已通过原生数据包提供 `graph_assembly:graph_filter`。总消耗：1 个属性过滤器（`create:attribute_filter`）、1 个安山合金、2 个虫蚀方块；确定产出 **1 个未配置的图装配过滤器**。没有随机废品、失败池或额外副产物。

| 路线 | 设备和工序 | 工件 |
| --- | --- | --- |
| 主体 | 属性过滤器 → 动力冲压机冲压 → 动力锯裁切 | `incomplete_graph_filter`，先 `body_start` 再 `body_ready` |
| 嵌件 | 安山合金 + 2 虫蚀方块 → 无热源工作盆搅拌 → 动力冲压机冲压 | `infested_filter_insert`，先 `insert_start` 再 `insert_ready` |
| 汇合 | 两件准备好的工件 → 工作盆压合 | `assemble`，产出 `graph_filter` |

半成品 ID 均位于 `graph_assembly` 命名空间。两件工件分别保留自己的进度，最终输入角色同时校验当前节点和完整有序检查点历史：主件必须为 `body_start → body_ready`，辅件必须为 `insert_start → insert_ready`。最终结算只继承主件上下文，不合并辅件历史。原料、未准备好的半成品、无进度或其他图的同物品均不能替代；输出满时等待，不消耗输入。

入口原料不是中间物品：`body_start` 读取图级 `ingredient`（属性过滤器），`insert_start` 用节点级 `ingredient`（安山合金）。`insert_start.transitional_item` 必须保留为 `graph_assembly:infested_filter_insert`，否则第一次加工会使用图级默认的主体载体；这并不要求入口消耗一件 insert。后续 `insert_ready` 无需重复填写载体，实际加工与图内预览都沿用 insert 分支的载体。不要把主原料或载体重复添加进 `recipe.ingredients`；该数组只列本步额外消耗的辅料。

`graph_assembly:filter_infested_blocks` 为物品标签，默认包含七种原版虫蚀方块：石头、圆石、深板岩、石砖、苔石砖、裂纹石砖、錾制石砖。两个虫蚀输入可用相同或不同变种，也可来自一个数量为 2 的堆叠。搅拌入口的逻辑主输入始终是安山合金，虫蚀方块仅为普通辅料；不会为裸虫蚀方块建立单输入图入口，也不删除或接管附魔工业原有的四虫蚀石头压合、虫蚀圆石粉碎配方。

### 用 KubeJS 替换，而非重复注册

需要定制时，将 [filter_crafting.js](../../examples/kubejs/filter_crafting.js) 复制到实例的 `kubejs/server_scripts/`，然后 `/reload`。脚本按 ID 删除内置配方，再以 **同一个** `graph_assembly:graph_filter` ID 注册完整图；七种虫蚀物品通过 `ServerEvents.tags('item', ...)` 加入同名标签。两个专用半成品由本模组注册，不需要额外 `startup_scripts`。不填写 `format_version`，不将此图改名后与内置图同时启用，否则相同入口会产生歧义。

原生数据包路径：`data/graph_assembly/recipe/graph_filter.json` 与 `data/graph_assembly/tags/item/filter_infested_blocks.json`。修改标签即可调整允许的虫蚀原料；两个独立 Ingredient 表示消耗两件，不是一个标签槽消耗一件。不要用通用配方替换函数猜测嵌套的角色与检查点规则。

### 平面连线折点

边的可选 `waypoints` 为 `[[x, y], ...]`，与节点 `position` 使用同一平面坐标系。最多 64 对坐标；每个数必须有限且绝对值不大于 `1000000`。节点到各折点再到目标组成显示路径；**只影响平面绘制与点选**，球面忽略这些折点，不增加工序、连通关系、checkpoint、权重抽签或材料消耗。

构建器同样支持：

```javascript
graph.edge('body_start', 'body_ready', {
    waypoints: [[-108.675, -231.525], [-61.425, -278.775], [203.175, -278.775], [250.425, -231.525]]
});
```

JSON 边设置 `bidirectional: true` 时，反向边使用倒序折点。作者显式设计的折线路径不自动错开双向车道；需要分离时请分别提供两个方向的边与坐标。未填写折点的旧边保留既有路由行为。教学图的 sus 轮廓只有 5 个真实节点、4 条真实边，所有拐角都只是显示折点。

布局时为**展开后的完整预览**留空间，而非只看折叠圆点：本例的工作盆搅拌／压合采用 Create 原生 177×103 布局，冲压／切割缩略布局为 49×84。教学图已经拉大节点与折点的间距，并错开汇合处、搅拌处，避免相邻预览和无关连线重叠。坐标与模型使用相同缩放，因此仅滚轮放大不能修复原本过密的坐标。

带人工折点的大图首次以平面打开时自动缩小、居中显示轮廓（缩放下限仍为 0.25）；放大到 1.25 后显示原生机器预览，可拖动查看局部。之后重新打开或调整窗口大小保留用户视角，不反复自动取景。此初始化不改变球面初始相机或无折点旧图；切换平面／球面仍保留当前缩放。
