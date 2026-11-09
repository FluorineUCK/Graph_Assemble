# 配方格式与脚本 API

[English](recipe-format.md) | 简体中文

## 图与节点

图进度由物品承载，入口原料与结算产物也都是物品；流体可用于注液和工作盆的辅助消耗。完整原生配方可用于预览，并在[已适配设备](compatibility.zh_cn.md)中执行。

顶层字段：`type: "graph_assembly:assembly"`、`ingredient`（默认入口原料）、必填 `transitional_item`（物品 ID 字符串）、必填 `nodes` 数组、必填 `edges` 数组、可选 `outputs` 数组。

每个节点需要唯一 `id` 和内层 `recipe`。普通加工的 `recipe.type` 是原生 serializer ID，其他字段由对应 codec 解码；铁砧和酿造台使用下文的专用操作描述。Create 配方常用 `ingredients`、`results`、`processing_time`、`keep_held_item` 等字段。

**Create 简写模式（默认 `recipe_mode: "step"`）只填写辅料和副产物。** 系统补入主输入与主输出；完整 Create 配方应设置 `recipe_mode: "native"`，保留全部输入与输出定义。原版合成、熔炉族、营火、切石机和锻造台始终使用完整原生格式。

Create 6.0.10 原生物品副产物使用 `{ "id":"minecraft:flint", "count":1, "chance":0.25 }`；流体输入可使用 `{ "type":"neoforge:single", "fluid":"minecraft:water", "amount":250 }`。设备不能承载的辅助输入/输出会禁用整张图的执行，不会被忽略。

| 节点字段 | 默认值与含义 |
|---|---|
| `recipe_mode` | `step`；Create 简写自动补主输入／输出，`native` 表示完整原生定义；`recipe_id` 引用始终使用完整定义 |
| `start`, `finish` | `false`；独立属性，可同时为 true |
| `ingredient` | Start 继承图级原料；非 Start 不参与入口匹配 |
| `transitional_item` | 首次用图级物品，之后保持当前物品；显式值覆盖 |
| `mark_amounts` | `{}`，本次授予的标记名称到非负整数增量 |
| `max_mark_applications` | 省略为无限；每节点标记授予次数上限，非负整数，0 为不授予标记 |
| `checkpoint` | 普通节点 false；Start/finish 必定为 true |
| `branch_mode` | `choice`；可指定 `random` |
| `finish_mode` | `lenient`；可指定 `strict` |
| `outputs` | `[]`，该 finish 的附加产物规则 |
| `scrap` | `[]`，严格失败的废品权重池 |
| `workpiece_inputs` | `[]`，按解码后原生物品输入逻辑序号声明工件角色；见下节 |
| `progress_actions` | `[]`，加工成功后按顺序修改主工件进度；见下节 |
| `campfire` | `any`；仅营火节点可填 `normal` / `soul` / `any`，分别为普通营火／灵魂营火／两者皆可 |
| `position` | 自动平面布局；可写 `[x,y]`，相对画布中心的像素 |
| `sphere_position` | 自动 Fibonacci 球面；可写非零 `[x,y,z]`，归一化为单位球方向 |

`ingredient` 与 `transitional_item` 分别表示**入图前的原料**和**该步加工后的载体**，不是同时消耗的两个输入。例如 `insert_start` 用安山合金作 `ingredient`，用 `infested_filter_insert` 作 `transitional_item`：第一次加工直接消耗合金和声明的辅料，产出带进度的嵌件，不需要预先准备嵌件。另一入口可以使用属性过滤器并产出 `incomplete_graph_filter`；后续节点省略 `transitional_item` 时保持各自当前载体，不回退成图级默认物品。节点显式覆盖载体只改变该步输出，不改变它所需的输入。

是否已经入图依据 `graph_assembly:progress` 组件，而非物品 ID。作者也可以有意让入口原料与载体使用同一物品 ID：没有进度时按入口 Ingredient 初始化，已有进度时只能按原图出边继续，不会重新初始化；错误／损坏的进度不会当作普通原料重试。

图内简写预览在入口显示真实 Ingredient，保留标签、候选项和组件条件。后续节点沿上游推算工件载体，并按主工件 `current_nodes` 排除仅供辅件进入的分支。

预览不会完整模拟进度。多个载体都能到达同一节点时，只显示第一组可达的主输入／输出，其他载体不算作额外消耗。实际加工仍会检查工件的完整条件。`recipe_mode: "native"` 和 `recipe_id` 引用则保留完整原生布局与材料，不进行这些替换。

边：`{"from":"a","to":"b","weight":1,"bidirectional":false}`。双向展开为两条有向边；回路和自环可用。边权重必须为有限正数，只对源节点的 `random` 模式有概率意义。概率由满足条件的出边权重归一化而来。

可选 `requirements` 使用与节点工件角色、出口相同的标记／检查点格式：

```json
{"from":"heat","to":"finish","weight":3,
 "requirements":{"mark_ranges":{"quality":{"min":2}},"checkpoint_counts":{"heat":{"min":1}}}}
```

条件分支通过**边准入**实现，`branch_mode` 仍只有两种模式。`choice` 允许设备选择符合条件的后继；`random` 先完成本步的标记授予、检查点记录和清除，再检查边条件、目标主工件条件与计数容量，最后按权重抽取一次并保存目标。辅料、流体和热量等条件由目标设备在加工时检查。

没有可用边时，工件停工且不抽签；重载也不会重抽已有随机锁。双向边的两个方向使用相同条件。`enter_edge` 必须满足指定边的条件；`goto_node` 不经过边，但仍检查目标输入条件。未知边字段会报错，拼错的条件不会被忽略。

可选显示折点：`"waypoints": [[10,20],[30,40]]`，与节点 `position` 使用同一平面坐标系。最多 64 对有限数字，每个坐标绝对值不超过 `1000000`；省略或空数组保留自动走线，显式 `null`、维度错误和非数字会报出字段路径。路径依次经过起点、折点和终点，绘制与点选共享这条路径；折点不增加工序、checkpoint、概率或消耗，球面模式忽略它们。`bidirectional:true` 会倒序复制折点；若希望双向边分道，请分别定义两条边的折点。编辑显示折点不会改变工件的合法加工路径。

## 完整原生模板

完整配方包含主输入和主输出。为 Create 内联配方设置节点级 `recipe_mode: "native"`，即可直接沿用原生格式：

```json
{
  "id": "press",
  "start": true,
  "finish": true,
  "recipe_mode": "native",
  "recipe": {
    "type": "create:pressing",
    "ingredients": [{"item": "minecraft:iron_ingot"}],
    "results": [{"id": "create:iron_sheet"}]
  }
}
```

这是节点片段，仍需整图的入口原料、默认工件与结算规则。执行时，已适配 Create 类型将逻辑物品输入 0／输出 0 替换为本轮图工件，其余材料、加工条件和副产物保留；流体不占物品序号。图预览显示完整模板，页脚显示图结算产物。

KubeJS 的 `node(id, KubeRecipe)` 和 `nativeNode(id, json)` 自动写入该模式。`recipe_id` 则引用已经加载的配方，无需另设模式。`recipe_mode` 决定节点的配方表示方式。

## 加工后进度操作

`progress_actions` 放在节点选项中，与 `recipe` 同级。机器完成加工后，先授予标记、记录本次检查点，再按数组顺序执行操作；如果是出口节点，最后尝试结算。这些操作不会增加一道独立工序。

| 操作 | 配置与效果 |
|---|---|
| 清空标记 | `{"type":"clear_mark","mark":"defect"}`：删除该标记 |
| 扣减标记 | `{"type":"clear_mark","mark":"defect","count":2}`：扣除指定数量，不足则清零并删除名称；0 不改变状态，数量范围 `0..2147483647` |
| 清除指定检查点 | `{"type":"clear_checkpoint","node":"inspect"}`：删除该节点的**全部**检查点记录，其他记录顺序不变 |
| 清除最近检查点 | `{"type":"pop_checkpoint"}`：删除最后一条；列表为空时不变 |
| 指定下一工序 | `{"type":"goto_node","node":"inspect"}`：让指定节点成为下一步，不把它记为已经完成 |
| 进入指定边 | `{"type":"enter_edge","from":"inspect","to":"finish"}`：选定图内已有有向边，下一步加工其终点；不会执行或记录边的起点 |

每个节点至多一个改道操作（`goto_node` 或 `enter_edge`），它覆盖该节点普通或随机出边，不抽签。若本次 finish 已经成功结算或严格失败，则不再改道。目标仍需满足自己的输入条件；`current_nodes` 对照实际刚完成的节点，而不是 `enter_edge.from`。

清除不存在的标记或记录不产生效果。Start、finish 自动记录检查点，所以在这类节点执行 `pop_checkpoint` 会删除刚追加的本次记录。检查点清除也会影响 `checkpoint_counts` 统计，但不会改变工件入口、近期加工历史或 `mark_grant_counts` 中的授予次数；改道不是撤销加工，不返还消耗材料。双工件仅修改主工件进度。

待执行路线作为 `pending_route` 随工件保存，直到目标加工成功后消耗；此字段由引擎写入，不是配方字段。目标、指定边被删除，或当前节点的改道配置改变时停工并保留工件。旧工件没有该字段时维持原有行为。

完整返工示例：[`examples/datapack/progress-actions.json`](../../examples/datapack/progress-actions.json)。

## 多工件输入角色

主工件始终是逻辑物品输入 `0`。默认只有它能携带图进度；普通辅助输入必须没有 `graph_assembly:progress`。需要使用第二件、第三件已加工工件时，在**节点选项** `workpiece_inputs` 中逐一声明角色，而不是把带进度的物品当作普通 Ingredient 自动接受。

```json
{
  "id": "join",
  "recipe": {
    "type": "create:mixing",
    "ingredients": [{"item": "minecraft:iron_nugget"}]
  },
  "workpiece_inputs": [
    {"item_input": 0, "current_nodes": ["body", "polish"]},
    {"item_input": 1, "current_nodes": ["insert"],
     "requirements": {"mark_ranges": {"quality": {"min": 99, "max": 99}}}}
  ]
}
```

这是节点片段；整图还需 `body`、`polish`、`insert` 等节点和合法出边。角色字段：

| 字段 | 含义 |
|---|---|
| `item_input` | 必填非负整数；物品 Ingredient 逻辑序号，0 是主工件，不是机器物理库存 slot。Create 简写按补入主件后的材料编号，完整定义按原生物品输入编号；原版合成按下文非空格／数组顺序编号 |
| `current_nodes` | 可省略；提供时必须为非空、不重复的本图节点 ID 数组，限定输入工件的当前节点 |
| `requirements` | 可省略；检查该角色输入工件的标记与完整检查点，支持下文四种条件，并非只检查主工件 |

流体输入不占 `item_input` 序号。例如盆配方简写的 `ingredients` 依次为水与铁粒，补入主件后铁粒是逻辑物品输入 1；完整配方不补主件，首个物品输入为 0。不能用原始 JSON 下标或盆的实际 slot 下标代替。每个序号最多声明一次；超出该原生配方解码后物品输入数量会禁用图。

声明的每个工件必须携带有效进度、属于**同一张图**，且当前状态允许进入本节点：优先遵守已保存的指定路线（`pending_route`），没有指定路线时遵守已抽取的随机锁，否则按普通出边。`current_nodes` 和 `requirements` 只是附加筛选，不自行授予路线。主角色 0 可省略显式声明，仍受正常图遍历规则约束；一旦声明，它也必须是已入图工件，不能拿普通原料充当该角色。

所有输入仍需满足原生 Ingredient、数量、流体、热量及输出容量等机器条件。辅助输入按原生设备策略消费或保留：例如机械手的 `keep_held_item` 不会被角色声明改写。**只有主工件推进后的图进度进入主产物；辅助工件的标记、检查点、标记授予次数和随机锁都不合并。副产物不克隆工件进度。**

完整示例见 [`examples/kubejs/multi_workpiece.js`](../../examples/kubejs/multi_workpiece.js)，实际两路流程和范围结算教程见 [KubeJS 指南](../guide/kubejs.zh_cn.md#多工件盆合并示例)。

## 当前内联执行类型与机器条件

下面的上限不含主工件／主产物；简写自动补入主输入／输出，完整定义使用原有的物品输入 0／输出 0：

| 类型 | 物品辅助输入 | 流体辅助输入 | 额外物品输出 | 原生条件 |
|---|---:|---:|---:|---|
| `create:pressing` | 0 | 0 | 1 | 动力冲压 |
| `create:cutting` | 0 | 0 | 3 | 原生切割时间与设备条件 |
| `create:deploying` | 恰好 1 | 0 | 3 | `keep_held_item` 可保留辅助物品 |
| `create:filling` | 0 | 恰好 1 | 0 | 不接受副产物 |
| `create:milling` | 0 | 0 | 3 | `processing_time > 0` |
| `create:mixing` | 最多 63 | 最多 2 | 3 | 盆物品总输入上限 64、物品总输出上限 4；检查 `heat_requirement` 和实际热量 |
| `create:compacting` | 最多 63 | 最多 2 | 3 | 同一盆容量与热量约束，使用盆压缩路径而非单件冲压路径 |
| `create:crushing` | 0 | 0 | 6 | `processing_time` 沿用原生值（可为 0）；粉碎轮容量不足时保留未加工余料 |
| `create:sandpaper_polishing` | 0 | 0 | 0 | 手持／机械手砂纸；结算产物必须为至多一堆 |
| `create:splashing` | 0 | 0 | 11 | 原生水洗鼓风计时 |
| `create:haunting` | 0 | 0 | 11 | 原生缠魂鼓风计时 |
| `create:emptying` | 0 | 0 | 0 | 恰好一份非空流体输出；储罐与单堆物品交付容量检查 |

原生 `ingredients` 可混合物品和流体，但只有解码后的物品 Ingredient 参与角色编号。盆配方 `heat_requirement` 可沿用原生 `heated` / `superheated` 等要求，缺少热量或资源不能用图规则绕过。只有 `create:emptying` 接受原生流体输出；不支持流体工件或流体结算。分液简写的 `results` 写 `[{"id":"minecraft:water","amount":250}]`，主物品结果由图补入；完整原生模式同时填写物品结果和流体结果。

附属配方沿用这些原生类型时，适用相同的设备规则；自定义机器与加工类型的状态见[执行兼容性](compatibility.zh_cn.md)。

未安装可选模组时基础环境可运行，但包含缺失 serializer 的图不能执行。标准原版图节点也可由对应 Create 替代设备执行，包括鼓风烟熏与熔炼；沿用各设备的配置、自动化排除及原生条件。完整边界见[执行兼容性](compatibility.zh_cn.md)。


## 原版背包与工作台

标准 `minecraft:crafting_shaped` / `minecraft:crafting_shapeless` 节点使用完整原生配方（包括 `result`），或严格的 `{type, recipe_id}` 引用。支持原生背包、工作台及合成器；另支持确切原生 `ArmorDyeRecipe` 与 `RepairItemRecipe`。其他特殊合成、自定义配方子类或第三方菜单不在范围内。

有序合成以未镜像图案的非空格按行编号，重复字符每格单独计数；无序合成按 `ingredients` 顺序编号。主角色 0 的材料由图原料／工件替换，其余材料保留原生条件；每格消耗一份。实际输出由图进度与结算决定，原生 `result` 不额外产出。镜像、平移不会交换主辅身份。

左右键取出一整轮，第一堆进鼠标，其余进背包；Shift 全部进背包并逐轮复验。原生返还物先回原格，再进背包。所有可能结果及返还物都有空间才抽签并消费。换位只接受空目标，图预览禁用中键复制和直接丢弃。

节点写法见 [KubeJS 指南](../guide/kubejs.zh_cn.md#背包与工作台合成)。

## 原版熔炉族

`minecraft:smelting`、`minecraft:smoking`、`minecraft:blasting` 在原版设备中分别交给熔炉、烟熏炉、高炉，只接纳标准配方类；Create 鼓风的对应路径见[执行兼容性](compatibility.zh_cn.md#create-的原版替代设备)。使用完整原生 JSON 或严格 `{type, recipe_id}` 引用；逻辑物品输入只有 0，燃料不是辅助工件。`cookingtime > 0`，`experience` 须为有限非负数；原生 `result` 仅供解码，实际产物按图结算。

每一种可能产物组合都必须能完整放入一个输出槽。组合中的物品及组件相同才可合并，且总数不得超过槽位与物品堆叠上限；不会丢弃第二种产物，也不会为了适应空位重抽。空结果可以完成一轮。空间不足时不消费输入、不抽签；已燃烧的燃料继续按原版计时。

补入相同物品、相同组件的原料会保留当前加工进度；换件或修改当前图／引用定义会重新计时。读取存档后也会重新检查定义与工件，不直接沿用旧计划出料。经验只在成功加工后记录，可按原版规则通过取出产物或拆除机器领取；漏斗取出产物不会提前领取经验。此适配不支持自定义子类。

示例见 [KubeJS 指南](../guide/kubejs.zh_cn.md#熔炉烟熏炉与高炉)。

## 原版营火

标准 `minecraft:campfire_cooking` 可在营火或灵魂营火执行。节点条件 `campfire: "normal"` 限定普通营火，`campfire: "soul"` 限定灵魂营火；省略或填写 `"any"` 时两者均可。条件与 `recipe` 同级，不写进原生配方，只影响图步骤，不改变普通食物配方。完整原生 JSON 或 `{ type, recipe_id }` 引用仍沿用原生 `cookingtime`。

显式 `campfire` 只能用于 `minecraft:campfire_cooking` 节点。非法值、`null`、非字符串及误写进 `recipe` 都报字段路径错误。相同原料可用不同火种消歧；同一火种仍有多个匹配步骤时不加工，不靠定义顺序选取。

四个格位各自处理一件物品，不是四输入配方。每道工序只有逻辑输入 0 这一主工件，不能声明辅工件。熄灭时按原版降温，重新点燃后继续计时；完成后掉落完整图结算结果，不受熔炉单输出槽限制。营火不消耗燃料物品、不发熔炉经验，也没有原生漏斗库存输入。换件或图／引用定义变化后重新计时；特殊配方子类和附属自定义营火不在此适配范围内。

## 原版染色与修理

确切原生 `minecraft:crafting_special_armordye` 与 `minecraft:crafting_special_repairitem` 支持背包、工作台及原版合成器。染色被染物为逻辑输入 0，非空染料格按从左到右、从上到下的顺序作为输入 1、2……；修理第一非空格为主工件、第二格为辅助工件，分别校验，仅主件进度继承。

非 finish 步骤使用原生结果作为载体推进图，图级默认载体不覆盖；finish 按图产物结算。节点级 `transitional_item` 不允许。原生修理移除名称和普通附魔，诅咒与耐久按原生结果处理。Create 特殊图执行、其他特殊合成未开放。

## 酿造台操作

`graph_assembly:brewing` 描述原版酿造台的一步操作。它通过原生 `PotionBrewing` 查询效果，不另注册药水公式。

| 字段 | 含义 |
|---|---|
| `type` | 固定为 `graph_assembly:brewing` |
| `primary` | `bottle`（默认）或 `ingredient` |
| `addition` | 可选 Ingredient，限定非主件输入 |

仅接受这三个字段；不支持 `recipe_id`、结果、时间或费用字段。节点级 `transitional_item` 禁用；非 finish 以实际原生结果承载进度，忽略图默认载体。

物理槽 0–2 为瓶子，3 为共用材料，4 为燃料。逻辑输入 0 总是主件、1 总是另一输入：`bottle` 模式为“瓶子、材料”，`ingredient` 模式为“材料、瓶子”。带进度辅件必须声明 `workpiece_inputs` 的输入 1，独立校验同图、路径与要求；结果只继承主件历史。

材料主件向全部合法瓶子分发，每瓶独立推进、独立随机选择；材料只扣一次。任一带进度输入不合法、两模式竞争或容量不足，整轮拒绝。未认领的普通瓶在材料主件模式不动；普通材料下的瓶主件轮次可同时处理合法普通瓶。

每个参与酿造的瓶槽只能放一件物品，带进度的材料也只能有一件。每个原瓶槽可接收空结果或一件产物；所有可能的结算组合都必须满足这一容量要求，检查通过后才抽签。图产物不会溢出或掉落。原生材料返还物按原生规则处理；带进度的烈焰粉不能当燃料消耗。

图定义或工件改变使旧计时失效。普通燃料补充不重置工序。实际完成保留原生 Pre/Post 事件；取消不推进图，不退还已使用的时间和燃料。Post 可改变参与瓶的结果；若改写材料、燃料或未参与瓶，图轮次拒绝提交。

示例见 [KubeJS 指南](../guide/kubejs.zh_cn.md#酿造台)。接入范围为确切原版酿造台，不包括独立附属设备或 Create 流体加工。

## 铁砧操作

`graph_assembly:anvil` 是图节点的操作描述，不是独立可执行配方；不支持 `recipe_id` 或自定义 `result`、费用、扣料字段。

| 字段 | 含义 |
|---|---|
| `type` | 固定为 `graph_assembly:anvil` |
| `action` | 可选：`work`（默认，修理或附魔增强）、`repair`、`enchant`、`rename`、`combine` |
| `addition` | 可选 Ingredient，限定右槽辅助输入；`rename` 禁用 |
| `name` | 仅用于 `rename` 的精确目标名；空字符串移除名称，省略接受任意实际改名 |

只有实际产生对应效果的原生操作才推进图。`combine` 只承接宿主已有合法双输入组合，结果须在名称、修理费用和图进度之外发生变化；不要求结果与输入同物品。`rename` 右槽为空，不能声明辅工件。

左槽为逻辑输入 0 主工件，右槽为输入 1 辅工件；双方分别校验，仅继承左件进度。主件和原生结果恰为一件，带进度辅件恰好消耗一件。保留原生结果、等级费用与扣料；非 finish 使用原生结果载体，图默认载体不覆盖，节点级 `transitional_item` 禁用。finish 仍满足原生操作条件，再按图结算，不额外发放原生结果。

适用于确切原版 `AnvilMenu`，不自动开放独立或派生菜单。示例见 [KubeJS 指南](../guide/kubejs.zh_cn.md#铁砧)。

## 原版合成器

合成器复用上述标准有序／无序合成格式与逻辑角色，不新增字段。每次红石上升沿重新匹配并加工一轮；禁用槽保留原版行为。产物及原生返还物从前方输出，容器未接收的部分按原版弹出，不回填网格。

背包／工作台把产物交给鼠标或玩家背包，并优先将返还物放回原格；合成器则从前方出料，不回填网格。因此，即使使用同一配方，交付位置和容量规则也不同。

## 原版切石机

标准 `minecraft:stonecutting` 节点使用完整原生 JSON（包括 `result`），或严格 `{type, recipe_id}` 引用。本节描述原版切石机菜单，只接纳标准配方类；相同节点也可交给 Create 动力锯。逻辑输入只有 0，且是唯一主工件；原生 `result` 供解码，实际产物由图结算替代。

原生列表供玩家选择可执行的图步骤，选择绑定图与节点。左右键取出完整一轮，Shift 按输入和容量逐轮加工；所有可能结果须先通过容量检查，再抽签并消费。关闭菜单沿用原生输入返还行为。

节点写法见 [KubeJS 指南](../guide/kubejs.zh_cn.md#切石机)。

## 原版锻造台

标准 `minecraft:smithing_transform` 使用完整原生 JSON 或严格 `{type, recipe_id}` 引用。逻辑输入 0 是基底（物理槽 1），输入 1 是模板（物理槽 0），输入 2 是附加材料（物理槽 2）。图入口／当前工件替换基底条件；另外两格保留原生条件，可声明为辅助工件。

每轮三格各消耗一件，分别校验主辅工件，产物只继承主工件进度。原生 `result` 仅供解码；实际图产物在容量预检后完整交付。普通锻造不受影响。

标准 `minecraft:smithing_trim` 沿用三格角色，但保留原生基底、模板和材质条件。合法结果保留盔甲及原生组件并推进图；已有相同图案和材质则不消耗、不推进。中间工件沿用原生纹饰结果，图级默认不覆盖它，节点级 `transitional_item` 明确禁止；finish 按图结算，不额外发放盔甲。未知配方子类和附属菜单未开放。示例见 [KubeJS 指南](../guide/kubejs.zh_cn.md#锻造台)。

## 完整原生配方引用（预览与已验证执行）

需要引用已有原生配方时，在节点 `recipe` 中同时填写 serializer `type` 和已加载的 `recipe_id`：

```json
{
  "id": "filling",
  "start": true,
  "finish": true,
  "recipe": {
    "type": "create:filling",
    "recipe_id": "create:filling/chocolate_glazed_berries"
  }
}
```

这是节点片段，仍需放在普通图配方的 `nodes` 中，并提供顶层原料、工件、边及结算规则。需要安装提供目标配方的模组；ID 不存在或 serializer 不匹配时不注册该预览图。

- 引用直接使用完整原生配方对象，**不添加主工件或中间输出，也不合并其他内联字段**。已有材料、数量及该配方的自定义字段原样保留。
- 图节点嵌入原生 JEI 布局，保留实际输入/输出槽、动画及材料悬浮提示；槽位的配方/用途快捷键使用 JEI 注册的材料类型，保留其原生类型与数量。
- 引用配方的材料/产物可查询到该预览图；图底部成功产物仍来自图的结算规则，和节点中的原生产物不同。
- 只有上述已适配设备可执行引用：Create 使用独立原生副本，将物品输入 0 和产物 0 交给图工件规则，其余材料、条件和副产物保留；原版合成按背包／工作台或合成器的交付规则处理，熔炉族按单输入／单输出槽规则处理，切石机按原生列表选择与取出规则处理。各路径都检查角色与容量。其他设备仅预览，不能据此推进图进度。
- 结构合法、可原生解码的其他内联步骤也可预览；执行是否启用由独立设备检查决定。简写生成的中间工件不作为外部材料或可获得成品加入 JEI 索引；完整模板和引用保留原生材料索引。

## 完成条件与结算

图级和节点级 `outputs` 使用相同规则。`start` / `finish` 省略表示不限制入口／出口；`requirements` 省略或 `{}` 表示不检查标记和检查点。图级规则与当前 finish 的规则共同参与结算。`results` 和 `scrap` 的物品叶子使用 **`item`/`count`/`weight`/`components`/`chance`**，物品键不同于内层原生配方的 **`id`**。默认数量、权重、chance 均为 1，组件默认为空；每个叶子的最大数量必须能装进一个正常物品堆。

两种递归组：`{"mode":"all","results":[...]}` 全部执行；`{"mode":"weighted","results":[...]}` 按局部权重选择一个子条目。组还可带 `weight` 和 `chance`，可任意组合嵌套。匹配规则的顶层条目仍合并为一个加权池，选中后执行整棵子树，不摊平子池。叶子 `chance` 每个物品独立判定（与 Create 一致）；组 `chance` 整组仅判定一次。范围 `0..1`，0 必不产出，1 必执行。空 all 合法、空 weighted 非法；概率未命中不重抽、不转入废品池。详见 [KubeJS 概率组合说明](../guide/kubejs.zh_cn.md#默认加权选一用嵌套组表达同时与独立概率产出) 和 [完整示例](../../examples/kubejs/nested_probability_outputs.js)。

结算可返回多个物品堆；设备预留同时产出组合的容量后才消耗输入。注液器仍只有单堆输出 API，实际工件可能产生多堆结算时会停工，不截断产物；非结算注液与单堆出口正常。

先完成加工、更新物品及标记、追加本节点检查点、按顺序执行 `progress_actions`，随后仅在 finish 节点尝试结算。宽容失败保持更新后的状态并继续；严格失败消耗主工件，抽取 `scrap`（省略则无主产物）。副产物独立保留。只有仍未结算且没有改道操作的随机节点才抽取下一条边。

工件进度保存在 `graph_assembly:progress` 组件；中间工件最大堆叠数为 1。配方重载不重置状态，不重新抽签；图缺失、当前节点失效或锁定边失效时停工并显示原因。

保存状态中的 `marks` 是当前标记数量，`mark_grant_counts` 是各节点累计授予次数。每次授予只为该节点计一次，即使同时增加多个标记；清除标记不重置计数。非空 `mark_amounts` 即使所有增量都是 0，也会计次；空映射不计次。

标记数量和各节点标记授予次数的范围为 `0..2147483647`。即将授予标记而导致任一计数溢出的步骤不会交给设备执行，原料、流体和进度保留，并显示节点/标记或标记授予计数原因；不进行饱和、回绕或自动重抽随机分支。已有 `max_mark_applications` 达到上限后不会再增加计数，仍可正常加工；选择分支中未溢出的其他步骤不受影响。

完整最小配方（无条件，但仍仅在 finish 结算）：

```json
{"type":"graph_assembly:assembly",
 "ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
 "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:pressing"}}],
 "edges":[],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
```

每条产物规则仍可独立设置 `start` / `finish`；它们不受条件开关影响。关闭检查不会停止加工记录，其他规则仍可能使用它们。

以下均为单条规则的 `requirements` 值（所引用节点必须存在）：

| 用途 | 配置 |
|---|---|
| 仅标记、精确匹配 | `{"marks":{"precision":3}}` |
| 仅标记、达到最低数量 | `{"mode":"partial","marks":{"precision":3}}` |
| 仅检查点、完整历史相等 | `{"checkpoints":["start","wash","finish"]}` |
| 仅检查点、按顺序包含 | `{"mode":"partial","checkpoints":["start","finish"]}` |
| 两项 AND | `{"mode":"partial","marks":{"precision":3},"checkpoints":["start","wash","finish"]}` |
| 局部覆盖 | `{"mode":"partial","marks":{"mode":"exact","values":{"precision":3}},"checkpoints":{"mode":"exact","nodes":["start","finish"]}}` |
| 局部无序包含 | `{"checkpoints":{"mode":"unordered","nodes":["wash","finish"]}}` |

公共模式默认为 `exact`：标记键及数量必须完全相等；检查点完整序列必须完全相等。`partial`：标记最低数量、允许额外名称与超额；检查点有序子序列、允许额外经过，但重复项须有相应重复访问。自动记录的 Start / finish **参与判断，不被过滤**。

### 数量范围与检查点访问次数

`requirements` 支持两种独立的范围条件，也可用于 `workpiece_inputs[].requirements`：

```json
{
  "mark_ranges": {"quality": {"min": 2, "max": 4}},
  "checkpoint_counts": {
    "polish": {"max": 0},
    "join": {"min": 1, "max": 1}
  }
}
```

- `mark_ranges`：按标记名称检查累计数量；没有该标记时按 0 计算。
- `checkpoint_counts`：按节点 ID 统计**完整检查点序列**中的出现次数；从未记录按 0 计算，不是统计 Tooltip 最近三步，也不是标记授予次数。入口／出口自动记录，普通节点需 `checkpoint: true` 才计数；循环访问可重复累加。
- 每个范围至少提供 `min` 或 `max` 一端；不能写空对象。端点都是 `0..2147483647` 的整数，且 `min <= max`，**两端均包含**。
- 省略 `min` 等价于 0；省略 `max` 等价于 `2147483647`。`{ "max": 0 }` 对标记表示必须为 0，对检查点表示不得有该节点的检查点访问记录。
- 检查点键和 `current_nodes` 引用的节点必须存在；普通非检查点节点即便加工过也不会出现在 `checkpoint_counts` 中，需要统计时显式启用 `checkpoint`。

`marks`、`checkpoints`、`mark_ranges`、`checkpoint_counts` 同时配置时必须**全部满足（AND）**。范围条件只检查列出的名称，不要求其他标记或检查点为空；exact / partial / unordered 的含义不变，公共 `mode` 也不影响范围条件。省略某类条件就不检查该类。空范围映射 `{}` 不添加限制，但其中的单个范围对象不能是 `{}`。

局部检查点支持 `exact` / `partial` / `unordered`；`partial` 表示按顺序包含。`unordered` 沿用 containsAll，不要求重复次数；它不能用作公共或标记模式。完整对象必须提供 mode 与 values / nodes。标记完整形式用对象值的 `values` 识别；`{"mode":2,"values":3}` 仍是两个普通标记的简写。

**显式空条件不是关闭：** exact marks `{}` 要求无标记；exact checkpoints `[]` 要求空历史（finish 自动记录，所以通常无法满足）。partial 的 `{}` / `[]` 不限制实际记录，但仍显示为已启用。不要用空集合代替省略来关闭判定。

所有匹配的图级与当前 finish 规则进入同一加权池，只抽取一次。没有首条规则或 exact 优先，也不会每条规则各发一个产物。相同产物重复出现时权重叠加；单条规则里的归一化概率不代表最终概率。

JEI 分别显示未启用、完全匹配、至少满足、按顺序包含和无序包含；显式空要求另有提示。页面 tooltip 说明公共默认、局部覆盖后的有效模式。成品、废品与物品／流体输入索引保持独立。

JEI 原生 Info 页按图级／节点级规则显示上述条件，并按结算输出关联；图画布顶部不再放置“结算规则”按钮。成功产物与严格失败废品分槽；宽容失败的 `scrap` 不属于可获得废品。

现行数据包示例见 [`examples/datapack/branching.json`](../../examples/datapack/branching.json) 和 [`examples/datapack/item-first.json`](../../examples/datapack/item-first.json)；不要同时启用相同入口的示例。

## 装配类型发现与诊断

普通节点的 `recipe.type` 使用原生 **serializer ID**，它不一定与 `RecipeType` 同名。执行设备与条件见[兼容性参考](compatibility.zh_cn.md)，包括原版酿造台及铁砧操作。其他已注册 serializer 会先尝试原生解码，再报告接口声明与设备适配状态；解码失败和 serializer 未注册会分别说明原因。

启动、数据包重载和管理员命令 `/graph_assembly compatibility` 会检查当前加载的独立配方及 `create:sequenced_assembly` 内步骤，不限命名空间。报告包含样例来源；没有加载实例的类型不会凭注册表或反射猜测能力。仅发现第三方接口不会自动启用执行，仍需明确的 codec、设备消耗、进度传递与输出容量适配。

组件内部保存完整 JSON 状态，NBT 与网络采用每块最多 8192 UTF-16 单位的 Unicode 安全字符串分块，不截断完整检查点记录；旧单字符串 NBT 进度不读取。数据包作者无需操作这些内部字段。未发布开发数据不保证在格式变更后沿用；本模组不自动改写存档或清空旧工件。

## KubeJS

面向配方作者的完整教程、可复制脚本与排错说明见 [KubeJS 中文使用指南](../guide/kubejs.zh_cn.md)。

安装 KubeJS 时，插件自动提供全局 `GraphAssembly`；未安装时整个可选包不加载。无需 CreateJS。

```javascript
ServerEvents.recipes(function (event) {
    var graph = GraphAssembly.create('minecraft:iron_nugget')
        .ingredient({item: 'minecraft:iron_ingot'})
        .node('s', {type: 'create:pressing'}, {start: true})
        .node('f', {type: 'create:cutting', processing_time: 40}, {finish: true})
        .edge('s', 'f')
        .output({results: [{item: 'minecraft:gold_ingot'}]});
    event.custom(graph.build()).id('example:graph');
});
```

构建方法：`create(transitionalItem)`、`ingredient(value)`、`node(id, recipe[, options])`、`nativeNode(id, recipe[, options])`、`edge(from,to[, options])`、`weightedEdge(from,to,weight)`、`bidirectionalEdge(from,to)`、`output(ruleOrItem)`、`build()`。边选项示例：`.edge('a','b',{weight:1,waypoints:[[10,20],[30,40]]})`，显式 `from/to` 参数优先。

`node` 接受原有简写或本次事件创建的 KubeJS 配方对象；后者在调用时保存完整快照，不再独立注册为普通配方。`nativeNode` 接受完整原生 JSON，两种完整入口均设置 `recipe_mode: "native"`。入口可用材料 ID、标签或 Ingredient；产物可用物品字符串、ItemStack 或原有规则，保留数量及组件。数组简写是等权候选池，同时产出多项请使用 `mode: "all"` 组。

KubeJS 的 `keepIngredient`、`damageIngredient`、`modifyResult` 及自定义 ingredient action 尚未接入，嵌入时会报错；Create 原生 `keep_held_item` 仍可使用。完整示例见[原生配方与材料写法](../../examples/kubejs/native_recipes.js)。

构建器输出与数据包相同的 JSON。复杂节点、布局、产物和检查点配置在对应的 JSON 字段中；`workpiece_inputs` 写在 `.node(...)` 第三个参数，范围写在相应 `requirements` 内。下方的链式接口可用于常见配置。`build()` 使用同一个 `GraphCodec` 校验，没有另一套脚本默认值；也可直接通过 `event.custom` 提供完整 JSON。完整脚本见 [基础图](../../examples/kubejs/graph_assembly.js)、[条件结算](../../examples/kubejs/item_first.js)、[双工件](../../examples/kubejs/multi_workpiece.js) 和 [内置图过滤器替换示例](../../examples/kubejs/filter_crafting.js)。

## 真正的 Create 序列装配

外层仍为 `create:sequenced_assembly`，在 `sequence` 数组中混用 Create 原生步骤和本模组的 `graph_assembly:sequence_step`。完整示例：[数据包](../../examples/datapack/native-sequence.json)、[KubeJS event.custom](../../examples/kubejs/native_sequence.js)。无需 CreateJS，也不使用图配方 Builder。

```json
{
  "type": "graph_assembly:sequence_step",
  "step": {
    "recipe": {
      "type": "minecraft:smelting",
      "ingredient": {"item": "minecraft:iron_ingot"},
      "result": {"id": "minecraft:iron_nugget"},
      "cookingtime": 200,
      "experience": 0.0
    }
  }
}
```

- `step` 仅接受既有 `recipe`、可选 `recipe_mode` 和营火步骤的 `campfire`；不接受节点 ID、边、mark/checkpoint、图结算或多工件规则。`recipe_id` 引用及完整模板沿用已有设备校验；原版加工声明仍须提供其原生必填字段，不能因包装为序列而省略。
- 只接入已有明确设备适配器的加工族，具体限制沿用[兼容性参考](compatibility.zh_cn.md)。包装不会让任意第三方 serializer 自动可执行；递归序列、未知族拒绝。内层加工定义或引用不合法时，该外层配方的包装步骤整体停用并记录原因。
- 外层 `ingredient`、`transitional_item`、`loops`、`results` 完全采用 Create 格式。进度仅保存为 Create 原生组件，不保存图进度，不注册另一个图配方。示例实际按“熔炉 → 压板”做两轮。
- Create 负责中间载体、最终物品及权重抽取，完整保留外层载体／产物组件；内层原生计算仍用于设备合法性、材料、费用与余物，但它的主产物不额外发放，也不会自动把修理／染色／锻造组件合并到外层产物。Create 加工的副产物按现有设备规则处理。
- 预览和容量检查不会抽签。所有可能的最终产物都能放下时，才消耗输入。只能容纳一件物品的位置（如酿造瓶槽）要求最终结果数量为 1。重载会清除旧绑定，已保存的原生工件会按当前同 ID 配方重新匹配。
- 包装步骤在 Create 的 JEI 序列位置**只显示目标方块**，不添加动画或额外材料槽。每次绘制都会读取当前步骤，避免共用 serializer 的不同设备显示成同一个方块。普通原生步骤仍使用 Create 自带的动画。

`sequence_step` 是序列内部的操作描述，不是可独立执行的普通配方；这也不是任意图配方到线性序列的自动转换。

### 补充的链式接口

图构建器支持 `register(event,id)`、`finishOutput(nodeId,results[,requirements])`、`scrap(nodeId,results)`、`requirements(nodeId,requirements)`、`conditionalEdge(from,to,requirements[,weight])`、`branchMode(nodeId,mode)`、`progressAction(nodeId,action)`。按 ID 操作前须先添加节点；出口池与废料池均追加而非覆盖。`requirements()` 设置主角色 0，保留现有角色的 `current_nodes` 和辅件声明。

原生序列入口为 `GraphAssembly.sequence(transitionalItem)`：`ingredient`、`step`、`loops`、`output`、`build`、`register`，步骤选项仅有 `recipe_mode` 和 `campfire`；详细脚本见 [KubeJS 指南](../guide/kubejs.zh_cn.md)。
