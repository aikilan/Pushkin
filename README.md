# Pushkin (OpenAI Compatible)

Pushkin is a JetBrains IDE plugin that generates Git commit messages with an OpenAI-compatible AI service.

## Features

- **AI-generated commit messages**: Generate a commit message from the selected changes in the Commit window.
- **OpenAI-compatible API support**: Configure any OpenAI-compatible endpoint, including OpenAI, DeepSeek, or self-hosted services.
- **Prompt configuration**:
  - Use a global prompt template by default.
  - Configure a project-specific prompt template for the current project.
  - When the project prompt is empty, Pushkin falls back to the global prompt.
- **Friendly interaction**:
  - Shows a loading animation while generating.
  - Fills the generated result into the commit message field automatically.
- **Internationalized UI**: Supports English and Chinese.

## Build and Run

This project uses Gradle.

### Requirements

- **JDK 21**: The plugin is compiled with JDK 21. Make sure `JAVA_HOME` is configured.
  - Check with:
    ```bash
    java -version
    ```
  - If JDK 21 is not installed, use [Adoptium Temurin](https://adoptium.net/) or install it with Homebrew on macOS:
    ```bash
    brew install openjdk@21
    ```

### Commands

- **Build the plugin package**:
  ```bash
  ./build.sh
  ```

- **Build manually**:
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" ./gradlew build
  ```

- **Run in a development IDE sandbox**:
  ```bash
  ./gradlew runIde
  ```

- **Clean build outputs**:
  ```bash
  ./gradlew clean
  ```

The plugin ZIP package is generated under `build/distributions/`.

## FAQ

### Unable to locate a Java Runtime

This means Gradle cannot find a valid Java runtime.

On macOS or Linux:

1. Install JDK 21.
2. Configure `JAVA_HOME`, for example:
   ```bash
   export JAVA_HOME=/path/to/your/jdk-21
   export PATH=$JAVA_HOME/bin:$PATH
   ```
3. Reopen the terminal and run the build again.

On Windows:

1. Install JDK 21.
2. Set `JAVA_HOME` to the JDK installation directory.
3. Add `%JAVA_HOME%\bin` to `Path`.

---

# Pushkin (OpenAI 兼容)

Pushkin 是一个 JetBrains IDE 插件，用于通过 OpenAI 兼容的 AI 服务自动生成 Git 提交消息。

## 功能介绍

- **AI 自动生成提交消息**：在提交窗口中，根据当前选中的变更生成提交消息。
- **OpenAI 兼容接口**：支持配置 OpenAI、DeepSeek、自建服务等 OpenAI 兼容端点。
- **提示词配置**：
  - 默认使用全局提示词模板。
  - 可为当前项目配置专属提示词模板。
  - 当前项目提示词为空时，自动回退到全局提示词。
- **交互友好**：
  - 生成过程中展示 Loading 动画。
  - 生成完成后自动填充到提交消息输入框。
- **国际化界面**：支持英文和中文。

## 构建与运行

该项目使用 Gradle 构建。

### 环境要求

- **JDK 21**：插件使用 JDK 21 编译，请确保已配置 `JAVA_HOME`。
  - 检查命令：
    ```bash
    java -version
    ```
  - 如果未安装，可使用 [Adoptium Temurin](https://adoptium.net/) 或在 macOS 上通过 Homebrew 安装：
    ```bash
    brew install openjdk@21
    ```

### 常用命令

- **构建插件安装包**：
  ```bash
  ./build.sh
  ```

- **手动构建**：
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" ./gradlew build
  ```

- **在开发沙盒中运行 IDE**：
  ```bash
  ./gradlew runIde
  ```

- **清理构建产物**：
  ```bash
  ./gradlew clean
  ```

插件 ZIP 安装包会生成在 `build/distributions/` 目录下。

## 常见问题

### Unable to locate a Java Runtime

这表示 Gradle 找不到可用的 Java 运行环境。

macOS 或 Linux：

1. 安装 JDK 21。
2. 配置 `JAVA_HOME`，例如：
   ```bash
   export JAVA_HOME=/path/to/your/jdk-21
   export PATH=$JAVA_HOME/bin:$PATH
   ```
3. 重新打开终端后再次构建。

Windows：

1. 安装 JDK 21。
2. 将 `JAVA_HOME` 设置为 JDK 安装目录。
3. 将 `%JAVA_HOME%\bin` 添加到 `Path`。
