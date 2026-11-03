# PNG 图集素材清单

[English](atlas.md) | 简体中文

当前 GUI UV 合同由 `GraphGuiAssets.java` 定义，坐标为左上原点、1×逻辑像素。图集为 1024×512 RGBA，支持整数倍等比高清图。

## 现行图集区域

| 区域 | X,Y | 尺寸 | 九宫格边距 |
|---|---:|---:|---:|
| `GRAPH_PANEL` | 0,0 | 320×224 | 11 |
| `FILTER_PANEL` header | 336,0 | 232×95 | — |
| `FILTER_PANEL_UPPER` | 336,95 | 232×20 | — |
| `FILTER_PANEL_MIDDLE` | 336,116 | 232×20 | — |
| `FILTER_PANEL_LOWER` | 336,137 | 232×19 | — |
| `FILTER_PANEL_FOOTER` | 336,156 | 241×30 | — |
| `ADD` | 592,16 | 16×16 | — |
| `REMOVE` / `CLEAR` | 608,16 | 16×16 | — |
| `CONFIRM` | 624,16 | 16×16 | — |
| `SPHERE` | 640,16 | 16×16 | — |
| `PLANE` | 656,16 | 16×16 | — |
| `FILTER_CHOICE` | 592,47 | 115×18 | 8 |
| `FILTER_ICON_SLOT` | 351,33 | 18×18 | — |

`GraphGuiLayout` 维护画布、模式控件和详情区域；`FilterScreenLayout` 维护 241×226 筛选器面板、预览与控件几何。筛选器中的 + / !+ 位于 `(179,63)` / `(197,63)`；三模式按钮在 Y=202，清空 `(179,202)`，确认 `(208,202)`。这些是屏幕布局坐标，不是图集 UV。

## 物品资源

| 资源 | 用途 / 模型引用 |
|---|---|
| `textures/item/graph_filter.png` | 图装配过滤器；`models/item/graph_filter_appearance.json` 引用 `graph_assembly:item/graph_filter` |
| `textures/item/processed_filter.png` | `models/item/incomplete_graph_filter.json` 引用；未完成的图过滤器（主体工件） |
| `textures/item/processed_sus.png` | `models/item/infested_filter_insert.json` 引用；可疑的粉末（辅助工件） |
| `textures/item/junk.png` | 保留的用户素材；未发现生产代码或模型引用 |

## 使用边界

GUI 绘制直接 blit `textures/gui/graph_gui.png`；无独立 Settlement 画布或 Settlement 按钮。机器动画、JEI 槽、文字和 tooltip 由游戏/Java 绘制，不烘焙进图集。无需美术 JSON、GUI `.mcmeta` 或运行时 GUI sprite atlas。资源包替换方式见[资源包指南](../../guide/resource-packs.zh_cn.md)；背景素材来源见[来源记录](sources.zh_cn.md)。
