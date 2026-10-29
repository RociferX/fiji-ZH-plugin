# 安装与使用

本项目是 Windows x64 Fiji 中文显示包。它通过 JVM 启动时加载的 Java agent 绘制中文，Fiji 插件提供运行时切换入口；外部 TSV 词典提供译文。

## 环境

- Windows x64 桌面环境。
- 已验证 Fiji：2026-10-23 的 Windows x64 Latest 包，ImageJ 1.54p99，附带 Java 21.0.7。
- 发布包运行不需要另外安装 Java；源码构建使用 Fiji 附带的 JDK。
- 自动下载工具需要 Python 3 和 Windows 自带的 `curl.exe`。

Fiji 的 Latest 下载链接会随上游更新。其他版本可能具有不同的启动协议、依赖或 AWT 内部结构，应在更换版本后验证。

## 使用预编译中文包

1. 从本仓库 Releases 下载 `fiji-zh-plugin-0.11-windows-x64.zip` 并解压到新目录。
2. 从 Fiji 官方网站下载带 JDK 的 Windows x64 版本并解压。把包含 `jars`、`plugins`、`java`、`config` 和 `fiji-windows-x64.exe` 的应用目录放到中文包根目录下，命名为 `Fiji`。ZIP 的外层文件夹名可能不同，以内部文件为准。
3. 将中文包 `plugin/fiji-zh-plugin.jar` 复制到 `Fiji/plugins/Fiji_Chinese_Display.jar`。
4. 双击根目录的 `Fiji-Chinese.cmd`。英文启动入口是 `Fiji-English.cmd`。

目录应为：

```text
fiji-zh-plugin/
  Fiji-Chinese.cmd
  Fiji-English.cmd
  Start-Fiji.ps1
  build/
    fiji-zh-display-0.11.jar
  plugin/
    fiji-zh-plugin.jar
  locales/
    menus.zh_CN.tsv
    dialogs.zh_CN.tsv
    plugins.zh_CN.tsv
    toolbar-hints.tsv
    fontconfig.zh.properties
  Fiji/
    fiji-windows-x64.exe
    config/jaunch/jaunch-windows-x64.exe
    java/...
    jars/...
    plugins/Fiji_Chinese_Display.jar
```

包内保留 `build.ps1` 和源码，供后续构建。正常运行已有的 agent JAR 时不需要重新构建。

## 从源码构建

```powershell
git clone https://github.com/RociferX/fiji-ZH-plugin.git
cd fiji-ZH-plugin
python tools/download_fiji.py
```

下载工具只下载，不自动解压。将 `downloads/fiji-latest-win64-jdk.zip` 按上述布局解压到 `Fiji/`，然后运行：

```powershell
.\build.ps1
.\Fiji-Chinese.cmd
```

构建生成 agent 和切换插件，并将切换插件复制到 `Fiji/plugins/`。构建同一版本前先关闭使用该版本语言包的 Fiji，避免 JAR 被占用。

构建依赖为 Fiji 自带的 ImageJ JAR、JNA 5.14.0、JNA Platform 5.14.0 和 ASM 9.8。源代码以 Java 11 字节码编译，实际验证运行环境为上述 Java 21。

维护者生成分发 ZIP：

```powershell
.\package.ps1
```

输出到 `dist/`，同时生成 SHA-256 校验文件；包不包含 Fiji 本体、Java、测试数据和本地日志。

## 切换语言

运行中使用 `插件 → 中文界面 → 使用中文 / 使用英文`。英文模式下相应菜单为 `Plugins → Chinese Interface → Use Chinese / Use English`。已打开且受支持的窗口随语言一起切换。

直接运行 `Fiji/fiji-windows-x64.exe` 会沿用原始启动方式，不加载 agent。此时切换插件会提示使用中文启动器。

词典保存在 `locales/`，以 UTF-8 编码、制表符分隔。保存修改后会自动重新加载；未收录的文字显示英文。算法专名、命令、宏、用户名称和结果字段保留原值。

## 常见问题

### 只有英文界面，或提示使用中文启动器

确认使用根目录的 `Fiji-Chinese.cmd`；单独安装切换插件不加载 agent。启动日志位于 `build/fiji-stdout.log` 和 `build/fiji-stderr.log`。

### 找不到 Fiji 启动器或依赖

核对上述目录布局。不要在 `Fiji/` 下再套一层应用目录。缺少指定版本依赖通常意味着当前 Fiji 版本与已验证版本不同。

### Windows 阻止 PowerShell 脚本

先查看本地执行策略及下载文件属性，按所在计算机的策略允许本项目脚本运行，再重试启动器。

### 某些内容仍然是英文

完整覆盖范围见 README。当前覆盖五个指定第三方插件的主要界面；其他插件内部界面、部分长帮助和罕见错误尚未收录。

## 卸载

关闭中文显示版 Fiji。删除 `Fiji/plugins/Fiji_Chinese_Display.jar`，之后使用 Fiji 原始启动器启动。若保留 Fiji 应用目录，可删除中文包的启动脚本、`build/`、`plugin/` 和 `locales/`；用户数据按原位置保留。

## 上游软件

Fiji、ImageJ、JDK、JNA、ASM 和第三方插件由各自上游项目提供，使用各自许可证；本仓库的发布包不打包这些二进制文件。`tools/` 下本地使用过的三份 OpenJDK 字体参考源码不参与构建，也不随仓库或发布包分发。
