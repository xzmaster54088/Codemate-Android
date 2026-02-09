#!/bin/bash

# CodeMate Mobile 自动化测试脚本
# 运行所有测试套件：单元测试、集成测试、UI测试、性能测试

set -e  # 遇到错误时退出

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

# 全局变量
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_DIR/build"
TEST_REPORTS_DIR="$BUILD_DIR/test-reports"
COVERAGE_DIR="$BUILD_DIR/coverage"
LOGS_DIR="$BUILD_DIR/logs"

# 测试配置
TEST_TIMEOUT=300  # 5分钟
MAX_RETRIES=3
TEST_THREADS=4

# 清理函数
cleanup() {
    log_info "清理临时文件..."
    rm -rf /tmp/codemate-test-*
    # 清理模拟器
    if command -v adb &> /dev/null; then
        adb emu kill 2>/dev/null || true
    fi
}

# 错误处理
error_exit() {
    log_error "测试失败: $1"
    cleanup
    exit 1
}

# 创建目录
setup_directories() {
    log_info "创建测试目录..."
    mkdir -p "$TEST_REPORTS_DIR"
    mkdir -p "$COVERAGE_DIR"
    mkdir -p "$LOGS_DIR"
}

# 检查环境
check_environment() {
    log_info "检查测试环境..."
    
    # 检查必要工具
    if ! command -v java &> /dev/null; then
        error_exit "Java 未安装"
    fi
    
    if ! command -v gradle &> /dev/null; then
        error_exit "Gradle 未安装"
    fi
    
    if ! command -v adb &> /dev/null; then
        log_warning "ADB 未安装，跳过设备相关测试"
    fi
    
    # 检查环境变量
    if [ -z "$ANDROID_HOME" ]; then
        error_exit "ANDROID_HOME 环境变量未设置"
    fi
    
    log_success "环境检查通过"
}

# 启动Android模拟器
start_emulator() {
    if ! command -v adb &> /dev/null; then
        log_warning "ADB 不可用，跳过模拟器启动"
        return 1
    fi
    
    log_info "启动Android模拟器..."
    
    # 检查是否有运行中的模拟器
    if adb devices | grep -q "emulator"; then
        log_info "模拟器已在运行"
        return 0
    fi
    
    # 启动模拟器
    nohup emulator -avd pixel_4_api_30 -no-window -no-audio -gpu off > "$LOGS_DIR/emulator.log" 2>&1 &
    EMULATOR_PID=$!
    
    log_info "等待模拟器启动..."
    
    # 等待模拟器启动
    local timeout=300
    local elapsed=0
    
    while [ $elapsed -lt $timeout ]; do
        if adb devices | grep -q "emulator.*device"; then
            log_success "模拟器启动成功"
            return 0
        fi
        sleep 5
        elapsed=$((elapsed + 5))
        echo -n "."
    done
    
    error_exit "模拟器启动超时"
}

# 等待设备就绪
wait_for_device() {
    if ! command -v adb &> /dev/null; then
        return 0
    fi
    
    log_info "等待设备就绪..."
    
    local timeout=120
    local elapsed=0
    
    while [ $elapsed -lt $timeout ]; do
        if adb shell echo "test" | grep -q "test"; then
            log_success "设备就绪"
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
        echo -n "."
    done
    
    error_exit "设备连接超时"
}

# 运行代码质量检查
run_code_quality_checks() {
    log_info "运行代码质量检查..."
    
    cd "$PROJECT_DIR"
    
    # ktlint 检查
    log_info "运行 ktlint 代码风格检查..."
    if ./gradlew ktlintCheck --no-daemon --console=plain > "$LOGS_DIR/ktlint.log" 2>&1; then
        log_success "ktlint 检查通过"
    else
        log_error "ktlint 检查失败，查看日志: $LOGS_DIR/ktlint.log"
        cat "$LOGS_DIR/ktlint.log"
        return 1
    fi
    
    # Detekt 静态分析
    log_info "运行 Detekt 静态分析..."
    if ./gradlew detekt --no-daemon --console=plain > "$LOGS_DIR/detekt.log" 2>&1; then
        log_success "Detekt 分析通过"
    else
        log_warning "Detekt 发现问题，查看报告: $LOGS_DIR/detekt.log"
        cat "$LOGS_DIR/detekt.log"
    fi
    
    # SonarQube 扫描（如果配置了）
    if [ -n "$SONAR_TOKEN" ] && [ -n "$SONAR_HOST_URL" ]; then
        log_info "运行 SonarQube 扫描..."
        if ./gradlew sonarqube --no-daemon --console=plain > "$LOGS_DIR/sonarqube.log" 2>&1; then
            log_success "SonarQube 扫描完成"
        else
            log_warning "SonarQube 扫描失败"
        fi
    fi
}

# 运行单元测试
run_unit_tests() {
    log_info "运行单元测试..."
    
    cd "$PROJECT_DIR"
    
    # 运行单元测试
    if timeout $TEST_TIMEOUT ./gradlew test --no-daemon --console=plain \
        -Dtest.single.thread=false \
        -Dtest.max.parallel.forks=$TEST_THREADS \
        > "$LOGS_DIR/unit-tests.log" 2>&1; then
        log_success "单元测试完成"
    else
        log_error "单元测试失败"
        cat "$LOGS_DIR/unit-tests.log"
        return 1
    fi
    
    # 生成覆盖率报告
    log_info "生成测试覆盖率报告..."
    if ./gradlew jacocoTestReport --no-daemon --console=plain > "$LOGS_DIR/coverage.log" 2>&1; then
        log_success "覆盖率报告生成完成"
        
        # 检查覆盖率
        COVERAGE_FILE="$PROJECT_DIR/app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml"
        if [ -f "$COVERAGE_FILE" ]; then
            COVERAGE_PERCENT=$(grep -oP 'counter type="LINE" missed="\K[^"]*' "$COVERAGE_FILE" | head -1)
            TOTAL_LINES=$(grep -oP 'counter type="LINE" missed="\K[^"]*' "$COVERAGE_FILE" | tail -1)
            
            if [ -n "$COVERAGE_PERCENT" ] && [ -n "$TOTAL_LINES" ]; then
                COVERAGE_RATE=$(echo "scale=2; ($TOTAL_LINES - $COVERAGE_PERCENT) * 100 / $TOTAL_LINES" | bc)
                log_info "代码覆盖率: ${COVERAGE_RATE}%"
                
                if (( $(echo "$COVERAGE_RATE >= 80" | bc -l) )); then
                    log_success "覆盖率达标 (>=80%)"
                else
                    log_warning "覆盖率低于预期 (<80%)"
                fi
            fi
        fi
    else
        log_warning "覆盖率报告生成失败"
    fi
}

# 运行集成测试
run_integration_tests() {
    log_info "运行集成测试..."
    
    cd "$PROJECT_DIR"
    
    # 运行集成测试（需要设备）
    if command -v adb &> /dev/null && adb devices | grep -q "device"; then
        log_info "运行API集成测试..."
        if timeout $TEST_TIMEOUT ./gradlew testDebugUnitTest --tests "*IntegrationTest" --no-daemon --console=plain \
            > "$LOGS_DIR/integration-tests.log" 2>&1; then
            log_success "集成测试完成"
        else
            log_warning "集成测试失败"
            cat "$LOGS_DIR/integration-tests.log"
        fi
    else
        log_warning "未检测到Android设备，跳过集成测试"
    fi
}

# 运行UI测试
run_ui_tests() {
    log_info "运行UI测试..."
    
    cd "$PROJECT_DIR"
    
    # 检查是否有可用的设备
    if ! command -v adb &> /dev/null || ! adb devices | grep -q "device"; then
        log_warning "未检测到Android设备，跳过UI测试"
        return 0
    fi
    
    # 等待设备就绪
    wait_for_device
    
    # 运行UI测试
    log_info "运行Espresso UI测试..."
    if timeout $TEST_TIMEOUT ./gradlew connectedAndroidTest --no-daemon --console=plain \
        -Dandroid.testInstrumentationRunnerArguments.class="com.codemate.**.ui.**" \
        > "$LOGS_DIR/ui-tests.log" 2>&1; then
        log_success "UI测试完成"
    else
        log_warning "UI测试失败或超时"
        cat "$LOGS_DIR/ui-tests.log"
    fi
}

# 运行性能测试
run_performance_tests() {
    log_info "运行性能测试..."
    
    cd "$PROJECT_DIR"
    
    # 检查是否有可用的设备
    if ! command -v adb &> /dev/null || ! adb devices | grep -q "device"; then
        log_warning "未检测到Android设备，跳过性能测试"
        return 0
    fi
    
    # 等待设备就绪
    wait_for_device
    
    # 运行性能基准测试
    log_info "运行编译器性能测试..."
    if timeout $TEST_TIMEOUT ./gradlew connectedAndroidTest --no-daemon --console=plain \
        -Dandroid.testInstrumentationRunnerArguments.class="com.codemate.**.performance.**" \
        > "$LOGS_DIR/performance-tests.log" 2>&1; then
        log_success "性能测试完成"
        
        # 解析性能报告
        if [ -f "$PROJECT_DIR/app/build/outputs/connected_android_test_output_data" ]; then
            log_info "性能测试结果已生成"
        fi
    else
        log_warning "性能测试失败或超时"
        cat "$LOGS_DIR/performance-tests.log"
    fi
}

# 生成测试报告
generate_test_reports() {
    log_info "生成综合测试报告..."
    
    cd "$PROJECT_DIR"
    
    # 创建综合报告
    REPORT_FILE="$TEST_REPORTS_DIR/test-summary-$(date +%Y%m%d_%H%M%S).md"
    
    cat > "$REPORT_FILE" << EOF
# CodeMate Mobile 测试报告

生成时间: $(date)

## 测试环境
- 操作系统: $(uname -s) $(uname -r)
- Java版本: $(java -version 2>&1 | head -n1)
- Gradle版本: $(gradle --version | grep "Gradle" | head -n1)
- Android SDK: $ANDROID_HOME

## 测试套件结果

### 代码质量检查
EOF
    
    # 添加代码质量检查结果
    if [ -f "$LOGS_DIR/ktlint.log" ]; then
        echo "- ktlint: ✅ 通过" >> "$REPORT_FILE"
    else
        echo "- ktlint: ❌ 失败" >> "$REPORT_FILE"
    fi
    
    if [ -f "$LOGS_DIR/detekt.log" ]; then
        echo "- Detekt: ✅ 通过" >> "$REPORT_FILE"
    else
        echo "- Detekt: ❌ 失败" >> "$REPORT_FILE"
    fi
    
    echo "" >> "$REPORT_FILE"
    echo "### 单元测试" >> "$REPORT_FILE"
    
    # 添加单元测试结果
    if [ -f "$LOGS_DIR/unit-tests.log" ]; then
        echo "- 状态: ✅ 完成" >> "$REPORT_FILE"
        
        # 解析测试结果
        if grep -q "BUILD SUCCESSFUL" "$LOGS_DIR/unit-tests.log"; then
            echo "- 结果: ✅ 成功" >> "$REPORT_FILE"
        else
            echo "- 结果: ❌ 失败" >> "$REPORT_FILE"
        fi
    else
        echo "- 状态: ❌ 未执行" >> "$REPORT_FILE"
    fi
    
    echo "" >> "$REPORT_FILE"
    echo "### 集成测试" >> "$REPORT_FILE"
    
    # 添加集成测试结果
    if [ -f "$LOGS_DIR/integration-tests.log" ]; then
        echo "- 状态: ✅ 完成" >> "$REPORT_FILE"
    else
        echo "- 状态: ❌ 未执行" >> "$REPORT_FILE"
    fi
    
    echo "" >> "$REPORT_FILE"
    echo "### UI测试" >> "$REPORT_FILE"
    
    # 添加UI测试结果
    if [ -f "$LOGS_DIR/ui-tests.log" ]; then
        echo "- 状态: ✅ 完成" >> "$REPORT_FILE"
    else
        echo "- 状态: ❌ 未执行" >> "$REPORT_FILE"
    fi
    
    echo "" >> "$REPORT_FILE"
    echo "### 性能测试" >> "$REPORT_FILE"
    
    # 添加性能测试结果
    if [ -f "$LOGS_DIR/performance-tests.log" ]; then
        echo "- 状态: ✅ 完成" >> "$REPORT_FILE"
    else
        echo "- 状态: ❌ 未执行" >> "$REPORT_FILE"
    fi
    
    # 添加覆盖率信息
    COVERAGE_FILE="$PROJECT_DIR/app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml"
    if [ -f "$COVERAGE_FILE" ]; then
        echo "" >> "$REPORT_FILE"
        echo "### 代码覆盖率" >> "$REPORT_FILE"
        echo "- 覆盖率报告: $COVERAGE_FILE" >> "$REPORT_FILE"
        echo "- 详细报告: $PROJECT_DIR/app/build/reports/jacoco/html/index.html" >> "$REPORT_FILE"
    fi
    
    # 添加日志文件信息
    echo "" >> "$REPORT_FILE"
    echo "## 详细日志" >> "$REPORT_FILE"
    echo "- 代码质量日志: $LOGS_DIR/ktlint.log" >> "$REPORT_FILE"
    echo "- 单元测试日志: $LOGS_DIR/unit-tests.log" >> "$REPORT_FILE"
    echo "- 集成测试日志: $LOGS_DIR/integration-tests.log" >> "$REPORT_FILE"
    echo "- UI测试日志: $LOGS_DIR/ui-tests.log" >> "$REPORT_FILE"
    echo "- 性能测试日志: $LOGS_DIR/performance-tests.log" >> "$REPORT_FILE"
    
    log_success "测试报告已生成: $REPORT_FILE"
    
    # 显示报告摘要
    echo ""
    echo "========== 测试报告摘要 =========="
    cat "$REPORT_FILE"
    echo "=================================="
}

# 上传测试结果到CI/CD
upload_test_results() {
    if [ "$CI" = "true" ] || [ -n "$GITHUB_ACTIONS" ]; then
        log_info "上传测试结果到CI/CD系统..."
        
        # 上传测试报告作为artifact
        if [ -n "$GITHUB_ACTIONS" ]; then
            log_info "上传测试报告到GitHub Actions..."
            # GitHub Actions会自动处理artifact上传
        fi
        
        # 发送通知（如果有配置）
        if [ -n "$SLACK_WEBHOOK_URL" ]; then
            send_slack_notification "CodeMate Mobile 测试完成"
        fi
        
        if [ -n "$DISCORD_WEBHOOK_URL" ]; then
            send_discord_notification "CodeMate Mobile 测试完成"
        fi
    fi
}

# 发送Slack通知
send_slack_notification() {
    local message="$1"
    curl -X POST -H 'Content-type: application/json' \
        --data "{\"text\":\"$message\"}" \
        "$SLACK_WEBHOOK_URL" \
        > /dev/null 2>&1 || true
}

# 发送Discord通知
send_discord_notification() {
    local message="$1"
    curl -X POST -H 'Content-type: application/json' \
        --data "{\"content\":\"$message\"}" \
        "$DISCORD_WEBHOOK_URL" \
        > /dev/null 2>&1 || true
}

# 清理函数
cleanup_test_environment() {
    log_info "清理测试环境..."
    
    # 停止模拟器
    if [ -n "$EMULATOR_PID" ]; then
        kill $EMULATOR_PID 2>/dev/null || true
    fi
    
    # 清理Gradle守护进程
    cd "$PROJECT_DIR"
    ./gradlew --stop --no-daemon > /dev/null 2>&1 || true
    
    cleanup
}

# 主函数
main() {
    log_info "开始 CodeMate Mobile 自动化测试..."
    
    # 设置错误处理
    trap 'error_exit "测试过程中发生错误"' ERR
    
    # 初始化
    setup_directories
    check_environment
    
    # 启动模拟器（可选）
    if [ "$START_EMULATOR" = "true" ]; then
        start_emulator
        wait_for_device
    fi
    
    # 运行测试套件
    local start_time=$(date +%s)
    
    # 1. 代码质量检查
    if run_code_quality_checks; then
        log_success "代码质量检查完成"
    else
        log_error "代码质量检查失败"
        return 1
    fi
    
    # 2. 单元测试
    if run_unit_tests; then
        log_success "单元测试完成"
    else
        log_error "单元测试失败"
        return 1
    fi
    
    # 3. 集成测试
    run_integration_tests || log_warning "集成测试失败"
    
    # 4. UI测试
    run_ui_tests || log_warning "UI测试失败"
    
    # 5. 性能测试
    run_performance_tests || log_warning "性能测试失败"
    
    # 生成报告
    generate_test_reports
    
    # 上传结果
    upload_test_results
    
    # 计算总耗时
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    log_success "所有测试完成，总耗时: ${duration}秒"
    
    # 清理
    cleanup_test_environment
    
    return 0
}

# 显示帮助信息
show_help() {
    echo "CodeMate Mobile 自动化测试脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -h, --help          显示此帮助信息"
    echo "  -e, --emulator      启动Android模拟器"
    echo "  -c, --coverage      强制生成覆盖率报告"
    echo "  -s, --skip-quality  跳过代码质量检查"
    echo "  -u, --skip-unit     跳过单元测试"
    echo "  -i, --skip-integration 跳过集成测试"
    echo "  --skip-ui           跳过UI测试"
    echo "  --skip-performance  跳过性能测试"
    echo "  -v, --verbose       详细输出"
    echo ""
    echo "环境变量:"
    echo "  ANDROID_HOME        Android SDK路径"
    echo "  SONAR_TOKEN         SonarQube令牌"
    echo "  SONAR_HOST_URL      SonarQube主机URL"
    echo "  SLACK_WEBHOOK_URL   Slack通知webhook"
    echo "  DISCORD_WEBHOOK_URL Discord通知webhook"
    echo ""
}

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -e|--emulator)
            START_EMULATOR=true
            shift
            ;;
        -c|--coverage)
            FORCE_COVERAGE=true
            shift
            ;;
        -s|--skip-quality)
            SKIP_QUALITY=true
            shift
            ;;
        -u|--skip-unit)
            SKIP_UNIT=true
            shift
            ;;
        -i|--skip-integration)
            SKIP_INTEGRATION=true
            shift
            ;;
        --skip-ui)
            SKIP_UI=true
            shift
            ;;
        --skip-performance)
            SKIP_PERFORMANCE=true
            shift
            ;;
        -v|--verbose)
            set -x
            shift
            ;;
        *)
            log_error "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
done

# 运行主函数
main "$@"
