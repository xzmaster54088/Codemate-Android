#!/bin/bash

# CodeMate Mobile 本地构建脚本
# 使用方法: ./build-local.sh [build-type] [options]
# build-type: debug, release, test, lint, clean

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查环境
check_environment() {
    log_info "检查构建环境..."
    
    # 检查Java
    if ! command -v java &> /dev/null; then
        log_error "Java未安装，请安装JDK 17+"
        exit 1
    fi
    
    java_version=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1-2)
    if [[ $(echo "$java_version >= 17.0" | bc -l) -eq 0 ]]; then
        log_error "Java版本过低，需要JDK 17+，当前版本: $java_version"
        exit 1
    fi
    
    # 检查Android SDK
    if [ -z "$ANDROID_HOME" ]; then
        log_error "ANDROID_HOME未设置，请设置Android SDK路径"
        exit 1
    fi
    
    if [ ! -d "$ANDROID_HOME" ]; then
        log_error "Android SDK目录不存在: $ANDROID_HOME"
        exit 1
    fi
    
    # 检查Gradle Wrapper
    if [ ! -f "./gradlew" ]; then
        log_error "gradlew文件不存在，请在项目根目录运行此脚本"
        exit 1
    fi
    
    chmod +x ./gradlew
    
    log_success "环境检查通过"
}

# 显示帮助信息
show_help() {
    echo "CodeMate Mobile 本地构建脚本"
    echo ""
    echo "使用方法:"
    echo "  $0 [build-type] [options]"
    echo ""
    echo "构建类型:"
    echo "  debug        构建Debug APK (默认)"
    echo "  release      构建Release APK"
    echo "  test         运行单元测试"
    echo "  lint         运行代码质量检查"
    echo "  clean        清理构建文件"
    echo "  all          执行完整的构建流程"
    echo "  install      安装Debug APK到连接的设备"
    echo "  help         显示此帮助信息"
    echo ""
    echo "选项:"
    echo "  --no-cache    不使用构建缓存"
    echo "  --verbose     显示详细输出"
    echo "  --parallel    启用并行构建"
    echo "  --report      生成详细报告"
    echo ""
    echo "示例:"
    echo "  $0 debug                    # 构建Debug APK"
    echo "  $0 release --report        # 构建Release APK并生成报告"
    echo "  $0 test --verbose           # 运行测试并显示详细输出"
    echo "  $0 all --no-cache --parallel # 完整构建，无缓存并行构建"
}

# 清理构建文件
clean_build() {
    log_info "清理构建文件..."
    ./gradlew clean
    rm -rf build/
    rm -rf app/build/
    rm -rf .gradle/
    log_success "清理完成"
}

# 代码质量检查
run_lint() {
    log_info "运行代码质量检查..."
    
    # Detekt静态分析
    ./gradlew detekt
    
    # ktlint代码风格检查
    ./gradlew ktlint
    
    log_success "代码质量检查通过"
}

# 运行测试
run_tests() {
    log_info "运行单元测试..."
    
    ./gradlew test
    
    # 生成测试覆盖率报告
    ./gradlew jacocoTestReport
    
    log_success "测试完成，报告生成在 app/build/reports/jacoco/"
}

# 构建Debug APK
build_debug() {
    log_info "构建Debug APK..."
    
    ./gradlew assembleDebug
    
    apk_path="app/build/outputs/apk/debug/app-debug.apk"
    if [ -f "$apk_path" ]; then
        log_success "Debug APK构建成功: $apk_path"
        log_info "APK大小: $(du -h $apk_path | cut -f1)"
    else
        log_error "Debug APK构建失败"
        exit 1
    fi
}

# 构建Release APK
build_release() {
    log_info "构建Release APK..."
    
    # 检查签名配置
    if [ ! -f "app/signing/release.jks" ]; then
        log_warning "发布签名文件不存在，将使用debug签名"
        log_warning "请配置正式签名以发布到应用商店"
    fi
    
    ./gradlew assembleRelease
    
    apk_path="app/build/outputs/apk/release/app-release.apk"
    if [ -f "$apk_path" ]; then
        log_success "Release APK构建成功: $apk_path"
        log_info "APK大小: $(du -h $apk_path | cut -f1)"
    else
        log_error "Release APK构建失败"
        exit 1
    fi
}

# 安装APK到设备
install_apk() {
    log_info "安装Debug APK到设备..."
    
    apk_path="app/build/outputs/apk/debug/app-debug.apk"
    if [ ! -f "$apk_path" ]; then
        log_error "Debug APK不存在，请先运行: $0 debug"
        exit 1
    fi
    
    # 检查设备连接
    if ! adb devices | grep -q "device$"; then
        log_error "没有检测到Android设备"
        log_info "请确保:"
        echo "  1. 设备已连接并启用USB调试"
        echo "  2. 设备授权了此电脑"
        exit 1
    fi
    
    # 安装APK
    adb install -r "$apk_path"
    
    log_success "APK安装成功"
    log_info "应用包名: com.codemate"
}

# 生成构建报告
generate_report() {
    log_info "生成构建报告..."
    
    report_file="build-report-$(date +%Y%m%d-%H%M%S).md"
    
    cat > "$report_file" << EOF
# CodeMate Mobile 构建报告

## 构建信息
- **构建时间**: $(date)
- **构建用户**: $(whoami)
- **构建主机**: $(hostname)
- **Git分支**: $(git branch --show-current 2>/dev/null || echo "未知")
- **Git提交**: $(git rev-parse --short HEAD 2>/dev/null || echo "未知")

## 环境信息
- **Java版本**: $(java -version 2>&1 | head -n1)
- **Android SDK**: ${ANDROID_HOME:-未设置}
- **Gradle版本**: $(./gradlew --version --quiet | grep Gradle | head -n1)

## 构建文件
EOF

    # 添加APK信息
    if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
        echo "- **Debug APK**: app/build/outputs/apk/debug/app-debug.apk ($(du -h app/build/outputs/apk/debug/app-debug.apk | cut -f1))" >> "$report_file"
    fi
    
    if [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
        echo "- **Release APK**: app/build/outputs/apk/release/app-release.apk ($(du -h app/build/outputs/apk/release/app-release.apk | cut -f1))" >> "$report_file"
    fi
    
    # 添加测试覆盖率信息
    if [ -f "app/build/reports/jacoco/test/jacocoTestReport.html" ]; then
        echo "- **测试覆盖率报告**: app/build/reports/jacoco/test/jacocoTestReport.html" >> "$report_file"
    fi
    
    # 添加静态分析报告
    if [ -f "build/reports/detekt/detekt.html" ]; then
        echo "- **Detekt报告**: build/reports/detekt/detekt.html" >> "$report_file"
    fi
    
    cat >> "$report_file" << EOF

## 下一步操作
1. 测试APK功能
2. 如需发布到应用商店，请配置正式签名
3. 查看测试覆盖率报告优化代码质量
4. 查看静态分析报告修复问题

## 技术支持
- 项目文档: README.md
- 构建指南: docs/BUILD_GUIDE.md
- 问题报告: https://github.com/your-org/codemate-mobile/issues
EOF

    log_success "构建报告生成: $report_file"
}

# 主函数
main() {
    # 默认参数
    build_type="debug"
    use_cache=true
    verbose=false
    parallel=false
    generate_report_flag=false
    
    # 解析命令行参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            debug|release|test|lint|clean|all|install|help)
                build_type="$1"
                shift
                ;;
            --no-cache)
                use_cache=false
                shift
                ;;
            --verbose)
                verbose=true
                shift
                ;;
            --parallel)
                parallel=true
                shift
                ;;
            --report)
                generate_report_flag=true
                shift
                ;;
            *)
                log_error "未知参数: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 显示帮助
    if [ "$build_type" = "help" ]; then
        show_help
        exit 0
    fi
    
    # 检查环境
    check_environment
    
    # 构建Gradle参数
    gradle_args=""
    if [ "$use_cache" = false ]; then
        gradle_args="$gradle_args --no-daemon --no-build-cache"
    fi
    
    if [ "$verbose" = true ]; then
        gradle_args="$gradle_args --info --stacktrace"
    fi
    
    if [ "$parallel" = true ]; then
        gradle_args="$gradle_args --parallel"
    fi
    
    # 根据构建类型执行相应操作
    case $build_type in
        clean)
            clean_build
            ;;
        lint)
            run_lint
            ;;
        test)
            run_tests
            ;;
        debug)
            build_debug
            if [ "$generate_report_flag" = true ]; then
                generate_report
            fi
            ;;
        release)
            run_lint
            run_tests
            build_release
            generate_report
            ;;
        install)
            build_debug
            install_apk
            ;;
        all)
            run_lint
            run_tests
            build_debug
            build_release
            generate_report
            ;;
        *)
            log_error "未知的构建类型: $build_type"
            show_help
            exit 1
            ;;
    esac
    
    log_success "构建完成！"
}

# 脚本入口
main "$@"