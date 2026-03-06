#!/bin/bash

# Atome Card Bot 启动脚本
# 只管理前端和后端服务，不操作 Docker 容器
# 请确保 Docker 基础设施已手动启动

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目路径
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"

# 日志文件
LOG_DIR="$PROJECT_ROOT/logs"
mkdir -p "$LOG_DIR"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"

# 端口检查函数
check_port() {
    lsof -i:$1 >/dev/null 2>&1
    return $?
}

# 检查 Docker 基础设施
check_docker_infra() {
    local postgres_running=false
    local milvus_running=false
    local ollama_running=false
    
    # 检查 PostgreSQL (5432)
    if check_port 5432; then
        postgres_running=true
    fi
    
    # 检查 Milvus (19530)
    if check_port 19530; then
        milvus_running=true
    fi
    
    # 检查 Ollama (11434)
    if curl -s http://localhost:11434/api/tags >/dev/null 2>&1; then
        ollama_running=true
    fi
    
    # 显示状态
    echo -e "${BLUE}基础设施检查:${NC}"
    if $postgres_running; then
        echo -e "  PostgreSQL (5432): ${GREEN}✅ 运行中${NC}"
    else
        echo -e "  PostgreSQL (5432): ${RED}❌ 未运行${NC}"
        echo -e "    ${YELLOW}提示: cd infra && docker-compose up -d${NC}"
    fi
    
    if $milvus_running; then
        echo -e "  Milvus (19530): ${GREEN}✅ 运行中${NC}"
    else
        echo -e "  Milvus (19530): ${RED}❌ 未运行${NC}"
        echo -e "    ${YELLOW}提示: cd infra && docker-compose up -d${NC}"
    fi
    
    if $ollama_running; then
        echo -e "  Ollama (11434): ${GREEN}✅ 运行中${NC}"
    else
        echo -e "  Ollama (11434): ${YELLOW}⚠️ 未运行${NC}"
        echo -e "    ${YELLOW}提示: ollama serve${NC}"
    fi
    
    # 如果基础设施未就绪，给出警告
    if ! $postgres_running || ! $milvus_running; then
        echo ""
        echo -e "${RED}⚠️ 警告: Docker 基础设施未完全启动${NC}"
        echo -e "${YELLOW}请先手动启动 Docker 基础设施:${NC}"
        echo "  cd infra && docker-compose up -d"
        echo ""
        return 1
    fi
    
    return 0
}

# 等待服务启动函数
wait_for_service() {
    local port=$1
    local name=$2
    local max_attempts=${3:-30}
    local attempt=1
    
    echo -e "${YELLOW}⏳ 等待 $name 启动...${NC}"
    while ! check_port $port; do
        if [ $attempt -ge $max_attempts ]; then
            echo -e "${RED}❌ $name 启动超时${NC}"
            return 1
        fi
        sleep 1
        ((attempt++))
    done
    echo -e "${GREEN}✅ $name 已启动 (端口: $port)${NC}"
    return 0
}

# 检查 Milvus 向量是否需要初始化
check_vectors() {
    echo -e "${BLUE}🔍 检查 Milvus 向量数据...${NC}"
    
    local vector_count=$(curl -s -X POST http://localhost:19530/v1/vector/query \
        -H "Content-Type: application/json" \
        -d '{"collectionName": "kb_chunks", "filter": "chunk_id > 0", "limit": 5}' 2>/dev/null | jq '.data | length')
    
    if [ -z "$vector_count" ]; then
        vector_count=0
    fi
    
    echo -e "  Milvus 向量数量: $vector_count"
    
    if [ "$vector_count" -gt 0 ]; then
        echo -e "  ${GREEN}✅ 向量数据已存在，不需要初始化${NC}"
        return 0
    else
        echo -e "  ${YELLOW}⚠️ 向量数据为空，需要初始化${NC}"
        return 1
    fi
}

# 启动后端
start_backend() {
    echo -e "${BLUE}☕ 启动后端服务...${NC}"
    cd "$BACKEND_DIR"
    
    # 检查是否已运行
    if check_port 8080; then
        echo -e "  ${YELLOW}⚠️ 后端服务已在运行${NC}"
        return 0
    fi
    
    # 启动后端
    echo "  启动 Spring Boot 应用..."
    if [ "$DAEMON_MODE" = true ]; then
        nohup mvn spring-boot:run -Dspring-boot.run.profiles=local > "$BACKEND_LOG" 2>&1 &
    else
        mvn spring-boot:run -Dspring-boot.run.profiles=local > "$BACKEND_LOG" 2>&1 &
    fi
    
    # 等待后端启动
    if wait_for_service 8080 "后端服务" 60; then
        # 检查向量状态
        if check_vectors; then
            echo -e "${GREEN}✅ 后端启动完成，向量已就绪${NC}"
            return 0
        else
            # 需要初始化向量
            echo -e "${YELLOW}⏳ 等待后端初始化向量（约 25 秒）...${NC}"
            sleep 25
            
            # 再次检查
            local vector_count=$(curl -s -X POST http://localhost:19530/v1/vector/query \
                -H "Content-Type: application/json" \
                -d '{"collectionName": "kb_chunks", "filter": "chunk_id > 0", "limit": 10}' 2>/dev/null | jq '.data | length')
            
            if [ -z "$vector_count" ]; then
                vector_count=0
            fi
            
            if [ "$vector_count" -gt 0 ]; then
                echo -e "${GREEN}✅ 向量初始化完成: $vector_count 个${NC}"
            else
                echo -e "${RED}⚠️ 向量初始化可能失败，请检查后端日志${NC}"
                echo "查看日志: tail -f $BACKEND_LOG | grep -E \"(向量|向量|ERROR)\""
            fi
        fi
        
        return 0
    else
        echo -e "${RED}❌ 后端服务启动失败${NC}"
        echo "查看日志: tail -f $BACKEND_LOG"
        return 1
    fi
}

# 显示帮助信息
show_help() {
    echo "Atome Card Bot 启动脚本"
    echo ""
    echo "用法: ./start.sh [选项]"
    echo ""
    echo "说明: 此脚本只管理前端和后端服务，不操作 Docker 容器。"
    echo "      请先确保 Docker 基础设施已手动启动。"
    echo "      启动时会自动检测 Milvus 向量，如需要则自动初始化。"
    echo ""
    echo "选项:"
    echo "  -h, --help        显示帮助信息"
    echo "  -d, --daemon      后台模式启动"
    echo "  -s, --stop        停止前端和后端服务"
    echo "  -r, --restart     重启前端和后端服务"
    echo "  --status          显示服务状态"
    echo ""
    echo "启动流程:"
    echo "  1. 检查 Docker 基础设施（PostgreSQL、Milvus）"
    echo "  2. 检查 Milvus 向量数据是否需要初始化"
    echo "  3. 启动后端服务，如有需要则自动生成向量"
    echo "  4. 启动前端服务"
    echo ""
    echo "示例:"
    echo "  # 1. 先启动 Docker 基础设施（只需执行一次）"
    echo "  cd infra && docker-compose up -d"
    echo ""
    echo "  # 2. 然后启动应用服务"
    echo "  ./start.sh                    # 前台启动（自动检测向量）"
    echo "  ./start.sh -d                 # 后台启动"
    echo "  ./start.sh -s                 # 停止前端和后端"
    echo "  ./start.sh -r                 # 重启前端和后端"
    echo "  ./start.sh --status           # 查看状态"
    echo ""
    echo "基础设施管理（需手动执行）:"
    echo "  cd infra && docker-compose up -d      # 启动基础设施"
    echo "  cd infra && docker-compose down       # 停止基础设施"
}

# 停止前端和后端服务
stop_services() {
    echo -e "${YELLOW}🛑 正在停止前端和后端服务...${NC}"
    
    # 停止前端
    if check_port 8081; then
        echo "  停止前端服务 (端口 8081)..."
        lsof -ti:8081 | xargs kill -9 2>/dev/null || true
        echo -e "  ${GREEN}✅ 前端已停止${NC}"
    else
        echo -e "  ${YELLOW}⚠️ 前端未运行${NC}"
    fi
    
    # 停止后端
    if check_port 8080; then
        echo "  停止后端服务 (端口 8080)..."
        lsof -ti:8080 | xargs kill -9 2>/dev/null || true
        echo -e "  ${GREEN}✅ 后端已停止${NC}"
    else
        echo -e "  ${YELLOW}⚠️ 后端未运行${NC}"
    fi
    
    echo -e "${GREEN}✅ 前端和后端服务已停止${NC}"
    echo ""
    echo -e "${YELLOW}提示: Docker 基础设施仍在运行${NC}"
    echo "如需停止基础设施，请执行: cd infra && docker-compose down"
}

# 启动后端
start_backend() {
    echo -e "${BLUE}☕ 启动后端服务...${NC}"
    cd "$BACKEND_DIR"
    
    # 检查是否已运行
    if check_port 8080; then
        echo -e "  ${YELLOW}⚠️ 后端服务已在运行${NC}"
        return 0
    fi
    
    # 启动后端
    echo "  启动 Spring Boot 应用..."
    if [ "$DAEMON_MODE" = true ]; then
        nohup mvn spring-boot:run -Dspring-boot.run.profiles=local > "$BACKEND_LOG" 2>&1 &
    else
        mvn spring-boot:run -Dspring-boot.run.profiles=local > "$BACKEND_LOG" 2>&1 &
    fi
    
    # 等待后端启动
    if wait_for_service 8080 "后端服务" 60; then
        # 等待向量初始化
        echo -e "${YELLOW}⏳ 等待后端初始化向量...${NC}"
        sleep 25
        
        # 验证向量
        local vector_count=$(curl -s -X POST http://localhost:19530/v1/vector/query \
            -H "Content-Type: application/json" \
            -d '{"collectionName": "kb_chunks", "filter": "chunk_id > 0", "limit": 10}' 2>/dev/null | jq '.data | length')
        
        if [ "$vector_count" -gt 0 ]; then
            echo -e "  ${GREEN}✅ 向量已就绪: $vector_count 个${NC}"
        else
            echo -e "  ${YELLOW}⚠️ 向量生成中，请稍后检查${NC}"
        fi
        
        return 0
    else
        echo -e "${RED}❌ 后端服务启动失败${NC}"
        echo "查看日志: tail -f $BACKEND_LOG"
        return 1
    fi
}

# 启动前端
start_frontend() {
    echo -e "${BLUE}🎨 启动前端服务...${NC}"
    cd "$FRONTEND_DIR"
    
    # 检查是否已运行
    if check_port 8081; then
        echo -e "  ${YELLOW}⚠️ 前端服务已在运行${NC}"
        return 0
    fi
    
    # 检查 node_modules
    if [ ! -d "node_modules" ]; then
        echo -e "  ${YELLOW}📦 安装前端依赖...${NC}"
        npm install
    fi
    
    # 启动前端
    echo "  启动 Vue.js 开发服务器..."
    if [ "$DAEMON_MODE" = true ]; then
        nohup npm run serve > "$FRONTEND_LOG" 2>&1 &
    else
        npm run serve > "$FRONTEND_LOG" 2>&1 &
    fi
    
    # 等待前端启动
    if wait_for_service 8081 "前端服务" 30; then
        echo -e "  ${GREEN}✅ 前端服务已就绪${NC}"
        return 0
    else
        echo -e "${RED}❌ 前端服务启动失败${NC}"
        echo "查看日志: tail -f $FRONTEND_LOG"
        return 1
    fi
}

# 显示状态
show_status() {
    echo -e "${BLUE}📊 服务状态:${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # 检查基础设施
    check_docker_infra
    
    echo ""
    echo -e "${BLUE}应用服务:${NC}"
    
    # 后端
    if check_port 8080; then
        echo -e "  后端服务 (8080): ${GREEN}✅ 运行中${NC}"
    else
        echo -e "  后端服务 (8080): ${RED}❌ 未运行${NC}"
    fi
    
    # 前端
    if check_port 8081; then
        echo -e "  前端服务 (8081): ${GREEN}✅ 运行中${NC}"
    else
        echo -e "  前端服务 (8081): ${RED}❌ 未运行${NC}"
    fi
    
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # 如果服务都在运行，显示访问地址
    if check_port 8080 && check_port 8081; then
        echo ""
        echo -e "${GREEN}🚀 所有服务已就绪!${NC}"
        echo -e "${BLUE}访问地址:${NC}"
        echo "  • 前端界面: http://localhost:8081"
        echo "  • 后端 API: http://localhost:8080/api"
    fi
}

# 主函数
main() {
    DAEMON_MODE=false
    
    # 解析参数
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -d|--daemon)
                DAEMON_MODE=true
                shift
                ;;
            -s|--stop)
                stop_services
                exit 0
                ;;
            -r|--restart)
                echo -e "${YELLOW}🔄 重启前端和后端服务...${NC}"
                stop_services
                sleep 2
                shift
                ;;
            --status)
                show_status
                exit 0
                ;;
            *)
                echo "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    # 清屏
    clear
    
    echo -e "${GREEN}"
    echo "╔═══════════════════════════════════════════╗"
    echo "║         Atome Card Bot 启动脚本           ║"
    echo "║      (自动检测向量，智能初始化)            ║"
    echo "╚═══════════════════════════════════════════╝"
    echo -e "${NC}"
    
    # 检查基础设施
    if ! check_docker_infra; then
        echo ""
        read -p "基础设施未就绪，是否继续启动应用服务? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo -e "${YELLOW}已取消启动${NC}"
            exit 1
        fi
    fi
    
    echo ""
    
    # 启动服务
    start_backend
    echo ""
    start_frontend
    
    # 显示状态
    echo ""
    show_status
    
    # 显示日志位置
    echo ""
    echo -e "${YELLOW}日志文件:${NC}"
    echo "  • 后端日志: $BACKEND_LOG"
    echo "  • 前端日志: $FRONTEND_LOG"
    echo ""
    echo -e "${YELLOW}常用命令:${NC}"
    echo "  • 停止服务: ./start.sh -s"
    echo "  • 查看状态: ./start.sh --status"
    echo "  • 重启服务: ./start.sh -r"
    echo ""
    echo -e "${YELLOW}基础设施管理:${NC}"
    echo "  • 启动: cd infra && docker-compose up -d"
    echo "  • 停止: cd infra && docker-compose down"
    
    # 如果不是后台模式，保持运行
    if [ "$DAEMON_MODE" = false ]; then
        echo ""
        echo -e "${YELLOW}按 Ctrl+C 停止前端和后端服务${NC}"
        echo ""
        
        # 捕获 Ctrl+C
        trap 'echo -e "\n${YELLOW}🛑 正在停止前端和后端服务...${NC}"; stop_services; exit 0' INT
        
        # 保持脚本运行
        while true; do
            sleep 1
        done
    fi
}

# 执行主函数
main "$@"
