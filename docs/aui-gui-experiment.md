# AUI（晴雪 UI）酶工厂 GUI 实验记录

> 本分支 `experiment/aui-gui` 为存档，已废弃，不再合入 main。原 GUI 方案（vanilla
> `MachineMenu` + `MachineScreen` + `ContainerData`）保留在 main 分支。

## 结论

尝试用晴雪 UI（`com.sighs:ApricityUI-neoforge-1.21.1:1.2.1`）的 HTML/CSS 引擎替换酶工厂
GUI，经三轮实测后判定**当前不适用**，回退 vanilla 方案。

## 做了什么

- 接入 AUI 依赖（`compileOnly` + `mods.toml` 硬前置 + 官方 Maven 仓库），`libs/` 放 AUI jar 供 dev 运行
- `MachineBlock` 酶工厂改经 `ApricityScreenNetworkHandler.openScreen` 打开 HTML 容器 GUI
- `EnzymeFactoryBlockEntity` 新增 `IItemHandler` capability（物种锁定 `isItemValid`）+ 查看者推送
- 新增 `ClientboundEnzymeGuiPacket`（温度/通量/浓度/历史）替代 `ContainerData`
- 客户端自定义 AUI 元素（`client/aui/`）：浓度条 / 进度条 / 平衡条 / v-t 折线图 + 静态 DOM 动态注入
- 删除 `MachineMenu` / `MachineScreen` / `ENZYME_FACTORY_MENU`

## 为什么废弃

1. **布局引擎不成熟**：自研 CSS 布局对 `flex: 1` + `min-height: 0` + 自定义元素（无固有尺寸）组合
   会死循环，渲染线程冻结（"未响应"）；`mode=fixed` 视口渲染于屏幕左上角不居中。
2. **动态 DOM 样式不稳定**：`document.createElement` + 类选择器匹配的样式时序不可靠，
   物种卡片背景/进度条丢失。
3. **迭代成本高**：纯 Java 自研引擎的 CSS 覆盖度、字体、对齐与浏览器标准差距大，
   与"用 HTML/CSS 快速做美观 UI"的预期不符，修复轮次多、性价比低。

## 关键踩坑记录（若未来重拾可参考）

- 视口：`mode=fixed` 在 `ApricityContainerScreen` 中 `drawLinkedDocument` 无居中 translate，
  内容渲染于屏幕 (0,0)；`mode=gui` 才是 1:1 填满 GUI 视口
- 布局死循环：自定义 AUI 元素（无固有尺寸）不能给 `flex: 1` / `width: 100%`，必须固定像素尺寸；
  普通 div 的 flex 布局是安全的（参照 AUI 自带 `tests/container-slot-recipe-test.html`）
- capability 注册：NeoForge 1.21.1 不再覆写 `BlockEntity.getCapability`，改用
  `RegisterCapabilitiesEvent.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ...)`
- `IItemHandler` 无 `setStackInSlot`（那是 `IItemHandlerModifiable` 的方法）
- 绘制合批：自定义元素 drawPhase 里画矩形必须 `Graph.beginLayeredBatch()`/`endBatch()`，
  否则每矩形一次即时模式提交会冻结渲染
- 资源路径：模组内 AUI 资源放 `assets/apricityui/apricity/`（跨命名空间，AUI 设计如此）
- `ApricityUI.menu()` 返回 `AuiPendingMenu.bind(Consumer<Object>)`，Java 侧直接用
  `ApricityScreenNetworkHandler.openScreen(player, path, declarations, argsById)` 更类型安全
