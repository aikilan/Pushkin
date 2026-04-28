#!/bin/bash

# 设置脚本在遇到错误时立即退出
set -e

echo "======================================"
echo "   Commit AI 插件一键构建脚本"
echo "======================================"

# 1. 环境检查与配置 (针对 macOS 优化)
if [[ "$OSTYPE" == "darwin"* ]]; then
    if [ -f /usr/libexec/java_home ]; then
        # 尝试查找 JDK 21
        JDK_PATH=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
        if [ -n "$JDK_PATH" ]; then
            export JAVA_HOME="$JDK_PATH"
            export PATH="$JAVA_HOME/bin:$PATH"
            echo "[INFO] 已自动设置 JAVA_HOME 为 JDK 21: $JAVA_HOME"
        fi
    fi
    if [ -z "$JAVA_HOME" ] && [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
        export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
        export PATH="$JAVA_HOME/bin:$PATH"
        echo "[INFO] 已使用 Homebrew JDK 21: $JAVA_HOME"
    fi
fi

# 2. 验证 Java 是否可用
if ! java -version &> /dev/null; then
    echo "[ERROR] 未找到有效的 Java 运行环境，请先安装 JDK 21。"
    echo "参考 README.md 中的 '环境要求' 部分进行安装。"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1)
echo "[INFO] 当前使用 Java 版本: $JAVA_VERSION"

# 3. 确保 gradlew 有执行权限
if [ -f "gradlew" ]; then
    chmod +x gradlew
else
    echo "[ERROR] 未找到 gradlew 文件，请确保在项目根目录下运行此脚本。"
    exit 1
fi

# 4. 执行插件打包，生成可安装 ZIP
echo "[INFO] 开始打包插件..."
./gradlew buildPlugin

echo ""
echo "======================================"
echo "   构建成功！"
echo "   安装包路径: build/distributions/"
echo "======================================"
