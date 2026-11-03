# Graph Assembly / 图装配

[English](README.md) | 简体中文

<img src="src/main/resources/logo.png" alt="图装配图标" width="128" height="128">

Graph Assembly 为 Minecraft 1.21.1、NeoForge 21.1.234 和 Create 6.0.10 提供有向图加工配方。图中的节点代表加工步骤，工件携带进度并在抵达出口后结算。需要 Java 21。

版本：**[0.1.0](https://github.com/FluorineUCK/Graph_Assemble/releases/tag/v0.1.0)**。

## 支持范围

- 选择分支、随机分支与循环；按标记、有序检查点及访问次数决定出口产物和失败品池。
- 多工件分别校验、合并后继承主工件进度；加工成功后可清除标记或检查点、指定下一工序。
- Create 的 pressing、cutting、deploying、filling、milling、mixing、compacting 七类加工。
- 背包、工作台及原版合成器的标准有序／无序图合成；支持染色与修理配方，并保留原生结果。合成器按红石上升沿加工一轮。
- 原版酿造台支持两种主件模式：瓶子作主件时各自保留进度；材料作主件时只消耗一份材料，并将同一份图进度分发给每个瓶子，各瓶独立推进与结算。
- 原版熔炉、烟熏炉、高炉、营火、灵魂营火、切石机、锻造台和铁砧的标准图加工。营火四格分别计时；铁砧支持修理、附魔增强、显式改名及合法组合。
- 原版配方可通过 Create 对应替代设备执行图步骤，包括工作盆、动力锯、机械合成器及鼓风烟熏／熔炼。
- KubeJS 配方作者可使用 `node(id, KubeRecipe)` 将原生配方作为节点，或用 `nativeNode()` 提供配方 JSON；材料输入支持物品／材料简写。
- 图进度和结算产物为物品；流体仅用于已适配步骤的原生辅助消耗。KubeJS 与 JEI 可选。
- 单个筛选器最多 256 个去重目标。详见[玩家使用指南](docs/guide/player.zh_cn.md)。

附属自定义机器不会默认获得图配方执行支持。完整范围见[兼容性参考](docs/reference/compatibility.zh_cn.md)。

## 安装

客户端和服务端均需安装本模组、Create 及其依赖。版本要求：Minecraft 1.21.1、NeoForge 21.1.234 或更高的 21.1 版本、Create 6.0.10。JEI 19.39.0.368 与 KubeJS NeoForge 2101.7.2-build.379 为可选依赖。

## 文档

- 玩家：[使用指南](docs/guide/player.zh_cn.md)
- 配方作者：[KubeJS 指南](docs/guide/kubejs.zh_cn.md)、[配方格式参考](docs/reference/recipe-format.zh_cn.md)
- 资源包作者：[资源包替换指南](docs/guide/resource-packs.zh_cn.md)、[图集坐标](docs/reference/art/atlas.zh_cn.md)
- [文档导航](docs/README.zh_cn.md)
- [贡献指南](CONTRIBUTING.zh_cn.md)

## 构建

- Windows：`./gradlew.bat test build`
- Linux：`bash ./gradlew test build`

## 许可证

本项目采用 [MIT 协议](LICENSE)。画布背景衍生素材保留[上游 MIT 许可与署名](src/main/resources/META-INF/licenses/create-factory-controller.txt)，详见[素材来源记录](docs/reference/art/sources.zh_cn.md)。
