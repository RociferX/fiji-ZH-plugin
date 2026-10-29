# Fiji ZH Plugin — Fiji 中文显示插件

**当前版本：0.12。将一个 JAR 复制到 Fiji 的 `plugins` 目录，正常启动 Fiji，再点击 `Plugins → Chinese Interface → Use Chinese`，即可启用中文。** 不需要单独启动器，也不需要修改启动参数。

- [下载最新版](https://github.com/RociferX/fiji-ZH-plugin/releases/latest)
- [安装、升级、卸载与故障排查](docs/INSTALL.md)
- [版本记录](CHANGELOG.md)

## 安装

1. 下载 Release 中的 `Fiji_Chinese_Display.jar`。
2. 关闭 Fiji，将 JAR 放入 `Fiji/plugins/`。升级时替换同名旧文件，避免安装多个副本。
3. 正常双击 Fiji 原来的启动器。
4. 点击 `Plugins → Chinese Interface → Use Chinese`。首次启用会提取内置词典并加载显示适配层，需要等待几秒。

中文状态下通过 `插件 → 中文界面 → 使用英文 / 使用中文` 切换。本版每次启动 Fiji 后手动启用中文；首次启用前已经打开的通用参数窗口、帮助窗口请关闭后重新打开。

已验证环境：Windows x64，2026-10-23 Fiji Latest 下载包，ImageJ 1.54p99、Java 21.0.7。运行时加载使用 Fiji 附带的 JDK；其他 Fiji/JDK 版本、macOS 和 Linux 尚未验证。

## 中文覆盖

| 范围 | 当前内容 |
| --- | --- |
| 主菜单 | 8 个顶层菜单及全部子菜单，1,199 个固定条目；菜单词典 1,167 条 |
| 工具栏 | 20 种内置工具变体的中文名称、用途与操作悬浮提示 |
| 核心窗口 | 常用图像创建、导入导出、阈值、显示范围、测量、粒子分析、滤波、二值化、FFT、卷积、比例尺与几何操作 |
| ROI、通道与堆栈 | ROI 管理器及附属操作、叠加层、通道工具、合并通道、颜色阈值、子堆栈、超堆栈、投影、拼版、重新切片等 |
| 设置与编辑器 | 18 项 ImageJ 首选项、6 个官方扩展设置页，以及宏录制器、ImageJ 编辑器和 Fiji 脚本编辑器的常用菜单与按钮 |
| 帮助与消息 | 部分内置帮助、常见确认框、错误消息、状态栏提示与进度 |

第三方插件词典有 486 条，覆盖以下主要界面：

| 插件 | 已验证版本 | 中文范围 |
| --- | --- | --- |
| Bio-Formats | 8.5.0 | 导入选项、上下文帮助、配置页与导出选项 |
| TrackMate | 8.1.6 | 主向导、检测、追踪、筛选、显示设置和 TrackScheme 常用菜单与提示 |
| Trainable Weka Segmentation | 4.0.0 | 训练、类别、分割、特征设置，以及默认 FastRandomForest 分类器常用属性和简介 |
| Coloc 2 | Colocalisation Analysis 3.1.0 | 通道、ROI、统计、随机化、显示保存选项、结果按钮和报告标题 |
| Stitching | 3.1.10 | 网格类型、方向、配准、融合、内存、输出和双图拼接参数 |

命令、快捷键、宏、结果字段、用户图像及类别名称、文件路径、输入内容、算法和模型标识保留原值。其他第三方插件内部界面、部分长篇算法帮助、其他 Weka 分类器专属属性和罕见错误仍显示原文。未收录的文字回退到英文。

## 工作方式

插件包含显示适配层、加载辅助程序及四份词典及字体配置的默认副本。点击启用后，短暂启动 Fiji 自带的 `javaw.exe` 辅助进程，通过 JDK Attach API 向当前 Fiji 加载 agent。辅助进程完成加载后退出；Fiji 无需重启。

Agent 在运行时开放自身所需的 AWT 模块访问，并重新转换已经加载的 `GenericDialog`、`HTMLDialog` 和 `MultiLineLabel`。中文菜单和控件使用显示副本、绘制视图及专用 AWT 中文字体；原始控件文字、事件命令、下拉模型和英文文档保留原值。磁盘上的 Fiji 核心 JAR 和第三方插件 JAR 不改写。

首次启用后，词典与诊断输出位于插件 JAR 旁的 `Fiji_Chinese_Display/0.12/`：

- `locales/menus.zh_CN.tsv`：菜单译文。
- `locales/dialogs.zh_CN.tsv`：核心窗口、帮助、消息与状态译文。
- `locales/plugins.zh_CN.tsv`：五个受支持插件的译文。
- `locales/toolbar-hints.tsv`：工具栏提示。
- `build/`：菜单与窗口清单、加载日志及错误诊断。

词典使用 UTF-8 和制表符，修改后自动重新加载；同一版本再次启用不会覆盖用户修改。新版本使用独立目录。`fontconfig.zh.properties` 为可选的启动时加载方式保留；普通插件模式使用私有字体副本，不重建 JVM 全局字体配置，也不改变默认 UTF-8 编码。

## 构建与打包

将受支持的 Fiji 应用目录放在本项目的 `Fiji/` 下，内部应包含 `jars`、`plugins`、`java`、`config` 和 `fiji-windows-x64.exe`。

```powershell
.\build.ps1
.\package.ps1 -SkipBuild
```

构建使用 Fiji 附带的 JDK、ImageJ、JNA 5.14.0、JNA Platform 5.14.0 和 ASM 9.8。输出 `build/fiji-zh-plugin.jar`，并安装到 `Fiji/plugins/Fiji_Chinese_Display.jar`。分发包在 `dist/`，包含可直接安装的 JAR、安装文档和 SHA-256 校验文件，不含 Fiji、Java 或第三方插件。

`python tools/download_fiji.py` 可下载上游 Windows ZIP，但不自动解压；Latest 链接随上游更新，版本信息记录在 `downloads/fiji-download.json`。源码以 Java 11 字节码编译，实际运行验证环境为上述 Java 21。

仓库保留 `Fiji-Chinese.cmd`、`Fiji-English.cmd` 和 `Start-Fiji.ps1`，供需要启动时加载的开发及兼容检查使用；普通插件安装不需要这些文件。

## 验证

默认增量验证。0.12 重点验证原始启动器启动、无 `-javaagent` 参数、运行时加载、已加载类重新转换、菜单与既有 ROI 管理器、核心参数窗口、原始事件和像素、用户自建窗口排除，以及重复中英文切换。

```powershell
.\verify-runtime.ps1
.\verify-plugin-robot.ps1 -Runtime -Modes config,weka-editor,stitch,coloc-report,scheme-tip,export
```

第三方插件检查通过原始 Fiji 启动器和运行时加载路径执行，Java Robot 使用当前控件屏幕坐标发送真实鼠标和键盘事件。报告为 `build/runtime-result.txt` 与 `build/robot-*-result.txt`，截图为对应 PNG；这些本地产物不随源码提交。

`verify.ps1` 是显式的历史完整检查入口。`verify-core.ps1`、`verify-expanded.ps1`、`verify-messages.ps1` 和 `verify-plugins.ps1` 提供相关专项检查。未变化的处理算法和词典覆盖复用已有通过结果；实现变化或发现回归时再扩大范围。

菜单覆盖检查为 `python tools/menu_coverage.py`，需要先生成清单。插件模式清单位于 `Fiji/plugins/Fiji_Chinese_Display/0.12/build/`；可把该清单路径作为工具的第一个参数；默认读取源码启动方式生成的根目录 `build/menu-inventory.tsv`。
