# 示例索引

[English](README.md) | 简体中文

这些示例需要分别启用，不会自动加载，也不构成一套完整整合包。使用前检查各图的入口原料和工序，不要同时启用入口条件冲突的示例。

## 数据包配方

放入对应数据包的 `data/<namespace>/recipe/` 后重载；1.21.1 使用单数 `recipe`。

| 文件 | 内容 |
|---|---|
| [`datapack/branching.json`](datapack/branching.json) | 分支图配方 |
| [`datapack/item-first.json`](datapack/item-first.json) | 物品优先条件示例 |
| [`datapack/progress-actions.json`](datapack/progress-actions.json) | 加工后标记/检查点/路线操作 |

## KubeJS 脚本

复制所需文件到 `kubejs/server_scripts/`，再 `/reload`。

| 文件 | 内容 |
|---|---|
| [`kubejs/graph_assembly.js`](kubejs/graph_assembly.js) | 基础图 |
| [`kubejs/native_recipes.js`](kubejs/native_recipes.js) | 上游 KJS 配方对象、完整原生模板与物品简写；不需要 KubeJS Create |
| [`kubejs/item_first.js`](kubejs/item_first.js) | 条件结算 |
| [`kubejs/multi_workpiece.js`](kubejs/multi_workpiece.js) | 双工件与范围条件 |
| [`kubejs/three_path_outputs.js`](kubejs/three_path_outputs.js) | 三条路径与独立出口废品池 |
| [`kubejs/nested_probability_outputs.js`](kubejs/nested_probability_outputs.js) | 嵌套概率结算 |
| [`kubejs/filter_crafting.js`](kubejs/filter_crafting.js) | 定制内置过滤器教学图 |

`filter_crafting.js` 按同一个 `graph_assembly:graph_filter` ID 删除并替换内置教学配方；不要改用新 ID 与内置图并行启用，以免入口冲突。完整作者说明见 [KubeJS 指南](../docs/guide/kubejs.zh_cn.md)。

## 原生序列装配

[数据包](datapack/native-sequence.json) / [KubeJS](kubejs/native_sequence.js)：Create 原生序列，先经熔炉加工，再冲压，重复两轮。两个版本只启用一个，并检查是否与其他示例的入口条件冲突。

- [fluent_authoring.js](kubejs/fluent_authoring.js): 链式注册、条件边、出口成品池和原生序列。
- [additional_create_steps.js](kubejs/additional_create_steps.js): 粉碎、砂纸、洗涤、缠魂、分液的完整加工链。
