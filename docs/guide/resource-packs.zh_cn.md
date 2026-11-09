# PNG 图集替换指南

[English](resource-packs.md) | 简体中文

## 日常美术交付

直接用 Aseprite、Krita、Photoshop 等打开并编辑：

1. `src/main/resources/assets/graph_assembly/textures/gui/graph_gui.png`
2. `src/main/resources/assets/graph_assembly/textures/item/graph_filter.png`
3. `src/main/resources/assets/graph_assembly/textures/gui/canvas_background.png`（图画布的独立平铺背景）

GUI 原图是 **1024×512 RGBA**。编辑 PNG 时保持各区域的位置、尺寸和透明留白；UV 坐标见[素材清单](../reference/art/atlas.zh_cn.md)。若移动图集区域，须同步修改 Java UV。

- 图集按面板、按钮底板和图标划分区域。图标背景保持透明，不要把文字画进 PNG。
- 按钮底板与正常／悬停／按下／禁用等交互状态由 Create 原生控件绘制；本图集仅提供相应透明图标，不提供独立四态按钮或选中标识区域。
- 九宫格由 Java 绘制；保留图集边缘，只有中间区域伸缩。
- 可交付整张 **2048×1024**、3072×1536 等整数倍高清图；所有内容和 UV 区域等比例放大，Java 不变。错误比例或非整数倍率会回退。
- 保持 UV 区域、控件位置和点击区域不变。

## 外部资源包：只交付替换 PNG

```text
my-art-pack/
  pack.mcmeta
  assets/graph_assembly/textures/gui/graph_gui.png
  assets/graph_assembly/textures/gui/canvas_background.png
  assets/graph_assembly/textures/item/graph_filter.png
```

根 `pack.mcmeta` 是 Minecraft 资源包描述：

```json
{"pack":{"pack_format":34,"description":"Graph Assembly PNG atlas"}}
```

过滤器物品模型使用 Minecraft 标准配置 `graph_filter_appearance.json`。其中的 `layer0` 引用 `graph_assembly:item/graph_filter`：模型文件指定纹理，PNG 决定物品图案。

过滤器界面右侧显示正在编辑的物品栈，由 Create 原生 `GuiGameElement` 渲染。替换物品 PNG 后，手持外观、背包图标和右侧预览都会更新。需要三维外观时应引用 Minecraft 标准物品模型，不要另画一张固定的预览 PNG。

在资源包界面将美术包排在 **Mod Resources 上方**；GUI 图集为整体覆盖，保留不修改的区域。

## 重载与检查

### 可移动画布背景

`canvas_background.png` 是一张独立的 **16×16 PNG**。可以直接替换为 32×32、48×48 等整数倍正方形；其显示周期仍为 16 个逻辑像素，随画布缩放变化。保持四边可无缝拼接。

平面模式中，背景与节点共用平移和缩放原点，外框、按钮及页脚保持不动。球面模式中，背景随缩放变化，但不会随球体旋转而平移。结算说明在 JEI 原生 Info 页查看，没有独立结算画布。背景缺失或尺寸不符合要求时，保留原面板底色。

背景平铺和像素中心采样由 Java 注册的 shader 处理。动态覆盖层的透明度配置在 `GuiColors` 的 `canvas.background` 中。

背景参考来源和许可记录见[素材来源](../reference/art/sources.zh_cn.md)。当前背景 PNG 是基于该参考的修改稿。

回到世界后按 F3+T 重载资源，再重新打开界面查看效果。

检查 GUI 缩放为 2 和 3 时的效果，以及调整窗口大小后的显示。分别查看正常、悬停、按下和禁用状态，检查图标透明边缘及物品外观。PNG 决定静态外观；动态节点、连线和文字的默认颜色配置在 `GuiColors` 中，本版不提供外部配色 JSON。

Create 原生动画与 JEI 材料槽仍由游戏绘制。
