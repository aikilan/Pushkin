# Commit AI (OpenAI Compatible)

这是一个为 JetBrains IDE 系列开发的插件，旨在利用 AI 技术自动生成 Git 提交消息。

### 功能介绍

- **AI 自动生成**：在提交窗口（Commit Window）中，点击图标即可根据当前选中的更改自动生成提交消息。
- **OpenAI 兼容**：支持配置任何兼容 OpenAI 协议的 API 端点（如 OpenAI 官方、DeepSeek、自建服务等）。
- **交互友好**：
    - 生成过程中提供 Loading 动画。
    - 生成完成后自动填充到提交消息输入框。
- **配置灵活**：可在 IDE 设置中配置 API Key、Base URL 及模型名称。
- **国际化**：支持中英文界面。

### 构建与运行

该项目使用 Gradle 构建系统。

#### 环境要求

- **JDK 21**: 插件使用 JDK 21 进行编译。请确保已安装并配置了 `JAVA_HOME` 环境变量。
  - 检查命令：`java -version`
  - 如果未安装，推荐从 [Adoptium (Temurin)](https://adoptium.net/) 或使用 `brew install openjdk@21` (macOS) 安装。

#### 构建命令

- **一键构建（推荐）**：
  我们提供了一个脚本来自动处理环境检查和构建：
  ```bash
  ./build.sh
  ```
  该脚本会自动尝试在 macOS 上寻找 JDK 21，并执行清理与打包。

- **手动生成插件安装包**：
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" ./gradlew build
  ```
  构建完成后，生成的安装包（ZIP 文件）位于 `build/distributions/` 目录下。

- **在开发沙盒中运行 IDE**：
  ```bash
  ./gradlew runIde
  ```
  此命令会启动一个预装了该插件的 IDE 实例，方便进行调试和功能验证。

- **清理构建产物**：
  ```bash
  ./gradlew clean
  ```

### 常见问题 (FAQ)

#### 1. 报错 "Unable to locate a Java Runtime"
如果在执行 `./gradlew` 命令时遇到此报错，说明系统找不到 Java 环境。
**解决方法：**
- **macOS/Linux**:
  1. 确认已安装 JDK 21。
  2. 在终端执行 `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` (仅 macOS)。
  3. 或者在 `~/.zshrc` 或 `~/.bash_profile` 中添加：
     ```bash
     export JAVA_HOME=/path/to/your/jdk-21
     export PATH=$JAVA_HOME/bin:$PATH
     ```
  4. 重新打开终端。
- **Windows**:
  1. 确认已安装 JDK 21。
  2. 在“系统环境变量”中设置 `JAVA_HOME` 指向 JDK 安装目录。
  3. 将 `%JAVA_HOME%\bin` 添加到 `Path` 变量中。
