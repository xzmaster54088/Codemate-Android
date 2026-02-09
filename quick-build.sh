#!/bin/bash

# CodeMate Mobile 快速构建脚本
# 简化的本地构建命令

echo "🚀 CodeMate Mobile 快速构建"
echo "=============================="

# 检查必要工具
check_tools() {
    echo "🔧 检查构建环境..."
    
    # 检查Java
    if ! command -v java &> /dev/null; then
        echo "❌ Java未安装"
        echo "请安装JDK 17+: https://adoptium.net/"
        exit 1
    fi
    
    # 检查Android SDK
    if [ -z "$ANDROID_HOME" ]; then
        echo "❌ ANDROID_HOME未设置"
        echo "请设置Android SDK路径"
        echo "export ANDROID_HOME=/path/to/android-sdk"
        exit 1
    fi
    
    echo "✅ 环境检查通过"
}

# 快速构建Debug
quick_debug() {
    echo "🔨 构建Debug版本..."
    ./gradlew assembleDebug --no-daemon
    echo "✅ Debug APK: app/build/outputs/apk/debug/app-debug.apk"
}

# 快速构建Release
quick_release() {
    echo "🔨 构建Release版本..."
    ./gradlew assembleRelease --no-daemon
    echo "✅ Release APK: app/build/outputs/apk/release/app-release.apk"
}

# 运行测试
quick_test() {
    echo "🧪 运行测试..."
    ./gradlew test --no-daemon
    echo "✅ 测试完成"
}

# 显示菜单
show_menu() {
    echo ""
    echo "请选择操作:"
    echo "1) 构建Debug APK"
    echo "2) 构建Release APK"
    echo "3) 运行测试"
    echo "4) 清理构建文件"
    echo "5) 查看帮助"
    echo "0) 退出"
    echo ""
}

# 主循环
main() {
    check_tools
    
    if [ $# -eq 0 ]; then
        # 交互模式
        while true; do
            show_menu
            read -p "请输入选择 [0-5]: " choice
            echo ""
            
            case $choice in
                1) quick_debug ;;
                2) quick_release ;;
                3) quick_test ;;
                4) ./gradlew clean ;;
                5) echo "详细文档请查看 build-local.sh --help" ;;
                0) echo "👋 再见！" ; break ;;
                *) echo "❌ 无效选择，请输入 0-5" ;;
            esac
            
            echo ""
            read -p "按Enter键继续..."
        done
    else
        # 命令行模式
        case $1 in
            debug) quick_debug ;;
            release) quick_release ;;
            test) quick_test ;;
            clean) ./gradlew clean ;;
            help|--help|-h) echo "使用方法: $0 [debug|release|test|clean|help]" ;;
            *) echo "未知参数: $1" ; echo "使用方法: $0 [debug|release|test|clean|help]" ;;
        esac
    fi
}

main "$@"