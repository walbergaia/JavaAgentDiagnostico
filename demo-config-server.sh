#!/bin/bash

# Demonstração do Servidor de Configuração Dinâmica do JavaAgentDiagnostico
# Este script demonstra como usar a nova funcionalidade de configuração dinâmica

echo "=========================================="
echo "Demonstração do Servidor de Configuração"
echo "=========================================="

# Para este exemplo, você precisa:
# 1. Compilar o agente: mvn clean package
# 2. Ativar o servidor de configuração no agent.properties:
#    config.server.enabled=true
#    config.server.port=8090
# 3. Executar sua aplicação com: java -javaagent:target/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar -jar sua-app.jar

echo ""
echo "1. Verificando o status do servidor de configuração:"
curl -s http://localhost:8090/health | jq .

echo ""
echo "2. Consultando configurações atuais:"
curl -s http://localhost:8090/config | jq . | head -10

echo ""
echo "3. Atualizando configuração (desabilitando captura SQL):"
curl -s -X POST http://localhost:8090/config \
  -H "Content-Type: application/json" \
  -d '{"enable.sql.capture": "false"}' | jq .

echo ""
echo "4. Verificando se a configuração foi atualizada:"
curl -s http://localhost:8090/config | jq '.["enable.sql.capture"]'

echo ""
echo "5. Atualizando múltiplas configurações:"
curl -s -X POST http://localhost:8090/config \
  -H "Content-Type: application/json" \
  -d '{"sampling.interval.ms": "5000", "enable.gc.metrics": "false"}' | jq .

echo ""
echo "6. Recarregando configurações do arquivo:"
curl -s -X POST http://localhost:8090/config/reload | jq .

echo ""
echo "7. Verificando se as configurações foram restauradas:"
curl -s http://localhost:8090/config | jq '.["enable.sql.capture", "sampling.interval.ms", "enable.gc.metrics"]'

echo ""
echo "=========================================="
echo "Demonstração concluída!"
echo ""
echo "Para usar com autenticação, adicione o header:"
echo "  -H \"Authorization: Bearer SEU_TOKEN\""
echo ""
echo "Exemplo com token:"
echo "  curl -H \"Authorization: Bearer meu-token\" http://localhost:8090/config"
echo "=========================================="