# Graph Assembly / 图装配

[English](README.md) | 简体中文

<img src="src/main/resources/logo.png" alt="图装配图标" width="128" height="128">

Graph Assembly 为Create添加了新的图加工配方！现在你可以为你的世界加入更多有趣（或者猎奇）的配方了。
与Create原生的加工配方相同，工件将携带进度并在抵达出口后结算。但是与之相比，你将有更高的自由度，能创造更多有趣的东西，或者让你的配方设计更上一层楼！

版本：**[0.1.2](https://github.com/FluorineUCK/Graph_Assemble/releases/tag/v0.1.2)**。

## 能做什么

- 选择分支、随机分支、条件分支或者循环：最大化自由度，尽情释放你的创意（或者恶意）。
- 多工件汇合：多件同图工件可以共同参与加工，分别校验进度，但产物只继承主工件，不叠加辅件的标记和历史
- 可清除的标记或检查点：新的机制！自由改变物品的标记来产出不同东西，进入不同的工序。当然你也可以让它们消失。
- 完全的 Create 加工支持：pressing（动力冲压）、cutting（切割）、deploying（机械手加工）、filling（注液）、milling（磨粉）、mixing（动力搅拌）、compacting（动力压缩），以及 crushing（粉碎）、sandpaper_polishing（砂纸打磨）、splashing（洗涤）、haunting（缠魂）、emptying（分液）。
- 也许还有原版机器？：背包、工作台及原版合成器支持有序／无序图合成；支持染色与修理配方。
酿造台支持两种主件模式：瓶子作主件时各自保留进度；材料作主件时只消耗一份材料，每个瓶子从同一份主工件进度独立推进与结算，随机结果不保证相同。
原版熔炉、烟熏炉、高炉、营火、灵魂营火、切石机、锻造台和铁砧的标准图加工。铁砧支持修理、附魔增强、改名及各种合法的组合。
- 手工执行太繁琐了？：原版配方可通过 Create 对应替代设备执行图步骤，包括工作盆、动力锯、机械合成器及鼓风烟熏／熔炼。
- KubeJS友好：使用 `node(id, KubeRecipe)` 将原生配方作为节点，或用 `nativeNode()` 提供配方 JSON。
- 相较于图装配，更喜欢原版装配？：当然可以，将已适配工序注册为真正的 Create 原生序列装配（如果是设计问题让你的图装配编辑之路困难重重，请告诉我:-(）

注意：
Create 附属的自定义机器或者更多其他模组来源的一般不会默认获得图配方执行支持，也许可以由你来完成这个兼容？欢迎PR，完整范围见[兼容性参考](docs/reference/compatibility.zh_cn.md)。

## 使用方法

客户端和服务端均需安装本模组、Create 及其依赖。版本要求：Minecraft 1.21.1、NeoForge 21.1.234 或更高的 21.1 版本、Create 6.0.10。JEI 19.39.0.368–19.70.0.469 与 KubeJS NeoForge 2101.7.2-build.379 为可选依赖。

## 文档和指引

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
