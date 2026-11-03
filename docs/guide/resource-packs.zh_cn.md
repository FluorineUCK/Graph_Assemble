# PNG 图集替换指南

[English](resource-packs.md) | 简体中文

## 日常美术交付

直接用 Aseprite、Krita、Photoshop 等打开并编辑：

1. `src/main/resources/assets/graph_assembly/textures/gui/graph_gui.png`
2. `src/main/resources/assets/graph_assembly/textures/item/graph_filter.png`
3. `src/main/resources/assets/graph_assembly/textures/gui/canvas_background.png`（图画布的独立平铺背景）

GUI 原图是 **1024×512 RGBA**。编辑 PNG 时保持各区域的位置、尺寸和透明留白；UV 坐标见[素材清单](../reference/art/atlas.zh_cn.md)。若移动图集区域，须同步修改 Java UV。

- 面板、按钮底板和图标分区。图标透明，不把文字画进 PNG。
- 按钮底板与正常／悬停／按下／禁用等交互状态由 Create 原生控件绘制；本图集仅提供相应透明图标，不提供独立四态按钮或选中标识区域。
- 九宫格由 Java 绘制；保留图集边缘，只有中间区域伸缩。
- 可交付整张 **2048×1024**、3072×1536 等整数倍高清图；所有内容和 UV 区域等比例放大，Java 不变。错误比例或非整数倍率会回退。
- 保持 UV 区域和控件位置／命中不变。

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

筛选器物品模型使用 Minecraft 标准配置 `graph_filter_appearance.json`，其 `layer0` 指向 `graph_assembly:item/graph_filter`；模型配置负责引用，物品图案由 PNG 提供。

筛选器右侧展示正在编辑的真实物品栈，通过 Create 原生 `GuiGameElement` 渲染。因此替换物品 PNG 后，手持、背包和右侧预览会一起更新；需要三维外观时可用 Minecraft 标准物品模型引用，不能另画一个固定预览 PNG。

在资源包界面将美术包排在 **Mod Resources 上方**；GUI 图集为整体覆盖，保留不修改的区域。

## 重载与检查

### 可移动画布背景

`canvas_background.png` 是一张独立的 **16×16 PNG**。可以直接替换为 32×32、48×48 等整数倍正方形；其显示周期仍为 16 个逻辑像素，随画布缩放变化。保持四边可无缝拼接。

平面模式中，背景与节点共用平移、缩放原点；外框、按钮、页脚不移动。球面模式只缩放背景，不把球体旋转假装成平移。结算说明位于 JEI 原生 Info 页，不存在独立结算画布。背景失踪或尺寸不合规时保留原面板底色。

背景平铺和像素中心采样由 Java 注册的 shader 完成；动态覆盖透明度集中在 `GuiColors` 的 `canvas.background`。

背景参考来源和许可记录见[素材来源](../reference/art/sources.zh_cn.md)。当前背景 PNG 是基于该参考的修改稿。

回到世界后按 F3+T 重载资源，再重新打开界面查看效果。

检查 GUI scale 2/3、窗口变化、正常／悬停／按下／禁用、图标透明边缘和物品外观。静态外观由 PNG 决定；动态节点、连线和文字默认颜色集中在 `GuiColors`，本版不提供外部配色 JSON。

Create 原生动画与 JEI 材料槽仍由游戏绘制。
