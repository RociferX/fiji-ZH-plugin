# 安装与使用（0.14）

## 安装

1. 从仓库 Releases 下载 `Fiji_Chinese_Display.jar`。也可以下载 Windows ZIP，解压取出同名 JAR。
2. 关闭 Fiji，将 JAR 复制到 `Fiji/plugins/Fiji_Chinese_Display.jar`。
3. 使用 Fiji 原来的启动器正常启动。
4. 点击 `Plugins → Chinese Interface → Use Chinese`，等待几秒后界面切换为中文。

不需要单独中文启动器，不需要设置 `-javaagent`，也不需要启用 JVM 自附加参数。点击插件后，后台辅助进程加载适配层，完成后自动退出。

首次安装默认英文。切换语言后会立即保存选择，下次正常启动 Fiji 自动沿用：选中文就自动加载中文，选英文就保持英文。中文菜单中的切换位置为 `插件 → 中文界面 → 使用中文 / 使用英文`。首次启用前已经打开的通用参数窗口和帮助窗口，请关闭后重开；主菜单、ROI 管理器等已识别窗口可直接更新。

## 环境

已验证 Windows x64，2026-10-23 Fiji Latest 包，ImageJ 1.54p99，附带 Java 21.0.7。插件使用该 JDK 的 `javaw.exe` 和 Attach API，显示适配依赖 Windows AWT 内部结构。

其他 Fiji/JDK 版本尚未验证。若 JVM 禁用了 Attach 或动态 agent 加载，插件会提示失败并给出日志路径；需要先在该 JVM 的运行配置中允许动态加载，再重启 Fiji。普通的上述 Java 21 安装无需额外配置。

## 升级

从 0.13 升级：关闭 Fiji，替换同名 JAR 后正常启动，继续沿用已保存的语言。自定义词典需将修改合并到新版本的词典目录。

0.11 / 0.12 没有记录语言选择，升级后请先选择一次所需语言。

关闭所有 Fiji 窗口，替换 `plugins/Fiji_Chinese_Display.jar`，使用原来的 Fiji 启动器重新启动并点击启用中文。删除重复安装的其他同名或旧版语言切换 JAR，避免重复菜单。0.11 的外部启动脚本和工作区目录不再是普通安装的必要组成。

## 词典与日志

首次启用后，在 JAR 旁生成：

```text
Fiji/plugins/
  Fiji_Chinese_Display.jar
  Fiji_Chinese_Display/
    settings.properties
    0.14/
      locales/...
      build/attach.log
      build/menu-inventory.tsv
      build/dialog-inventory.tsv
```

`settings.properties` 保存语言选择，位于版本目录之外，后续升级可以继续沿用。删除这个文件可恢复首次安装的英文默认值。使用开发启动脚本显式指定语言时，以指定参数为准。

首次使用时该位置需要可写。内置词典按需提取；同版本再次启用保留已有词典。编辑 UTF-8 TSV 后自动加载，不需要重新打包。不同版本各自保存词典。

若启用失败，先检查提示中的 `attach.log`。显示适配发生异常时另检查 `build/agent-error.log`。修正环境问题后重启 Fiji 再启用插件。

## 从源码构建

克隆仓库，将 Fiji 应用目录放在仓库根目录的 `Fiji/` 下，确保内部直接包含 `jars`、`plugins`、`java`、`config` 和启动器。执行：

```powershell
.\build.ps1
.\package.ps1 -SkipBuild
```

构建输出 `build/fiji-zh-plugin.jar`，同时安装到本地 `Fiji/plugins/Fiji_Chinese_Display.jar`。构建或升级前应关闭使用旧 JAR 的 Fiji。发布文件位于 `dist/`；Fiji 本体由上游单独提供。

若需自动下载 Fiji，可运行 `python tools/download_fiji.py`，再手动解压下载的 ZIP。上游 Latest 链接会更新，应核对下载后的 Fiji/JDK 版本。

## 校验下载

发布页提供 `.sha256` 文件。可在 PowerShell 中运行：

```powershell
Get-FileHash .\Fiji_Chinese_Display.jar -Algorithm SHA256
```

将结果与校验文件中的对应行比较。

## 卸载

关闭 Fiji，删除 `plugins/Fiji_Chinese_Display.jar` 和旁边的 `plugins/Fiji_Chinese_Display/` 词典及日志目录，之后正常启动 Fiji。用户图像、宏和测量数据保存在原位置。

## 上游组件

本插件依赖 Fiji 提供的 ImageJ、JDK、JNA 和 ASM，未将这些组件打包进插件或发布 ZIP。它们适用各自上游许可证。本仓库本地字体调查使用的 OpenJDK 参考源码不参与构建或分发。
