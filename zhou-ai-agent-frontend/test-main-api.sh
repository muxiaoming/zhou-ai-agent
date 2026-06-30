#!/bin/bash
# 主接口测试脚本

echo "=========================================="
echo "   主接口测试"
echo "=========================================="
echo ""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 测试 1：技能列表接口
echo "测试 1：技能列表接口"
echo "GET http://localhost:8182/api/skills"
echo ""
response=$(curl -s http://localhost:8182/api/skills)
if echo "$response" | grep -q "skillCount"; then
    echo -e "${GREEN}✅ 成功${NC}"
    echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response"
else
    echo -e "${RED}❌ 失败${NC}"
    echo "$response"
fi
echo ""
echo "=========================================="
echo ""

# 测试 2：主接口（流式投资决策）
echo "测试 2：主接口 - 流式投资决策"
echo "POST http://localhost:8182/api/investment/decide/stream"
echo ""
echo "请求参数："
echo '{"message": "查询苹果公司的股价"}'
echo ""
echo "响应（SSE 流）："
timeout 10 curl -N -X POST http://localhost:8182/api/investment/decide/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "查询苹果公司的股价"}' 2>/dev/null | head -20
echo ""
echo "=========================================="
echo ""

# 测试 3：同步投资决策接口
echo "测试 3：同步投资决策接口"
echo "POST http://localhost:8182/api/investment/decide"
echo ""
response=$(curl -s -X POST http://localhost:8182/api/investment/decide \
  -H "Content-Type: application/json" \
  -d '{"message": "查询苹果公司的股价"}')
if echo "$response" | grep -q "status"; then
    echo -e "${GREEN}✅ 成功${NC}"
    echo "$response" | python3 -m json.tool 2>/dev/null | head -30
else
    echo -e "${RED}❌ 失败${NC}"
    echo "$response"
fi
echo ""
echo "=========================================="
echo ""

echo "完成！"
echo ""
echo "如果测试成功，说明主接口正常工作。"
echo "请在浏览器中访问: http://localhost:5173/decision-engine"
echo ""
