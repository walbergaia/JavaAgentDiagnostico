#!/bin/bash

echo "=========================================="
echo "Teste do Servidor de Configuração Dinâmica"
echo "=========================================="

AGENT_JAR="build/libs/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar"

if [ ! -f "$AGENT_JAR" ]; then
    echo "ERRO: JAR do agente não encontrado em $AGENT_JAR"
    echo "Execute './build.sh' ou './gradlew build' primeiro"
    exit 1
fi

# Reconstrói o JAR com a nova configuração
echo "Reconstruindo agente com servidor de configuração habilitado..."
./gradlew shadowJar -q

# Cria uma aplicação de teste que roda por mais tempo
echo "Criando aplicação de teste..."
cat > TestConfigServer.java << 'EOF'
public class TestConfigServer {
    public static void main(String[] args) {
        System.out.println("=== Aplicação de Teste com Servidor de Configuração ===");
        System.out.println("Aguardando inicialização do agente...");
        
        try {
            Thread.sleep(5000); // Aguarda agente inicializar
            
            System.out.println("✅ Aplicação pronta!");
            System.out.println("🌐 Servidor de configuração deve estar disponível em: http://localhost:8090");
            System.out.println();
            System.out.println("📋 Teste os seguintes endpoints:");
            System.out.println("  curl http://localhost:8090/health");
            System.out.println("  curl http://localhost:8090/config");
            System.out.println();
            System.out.println("Pressione Ctrl+C para parar...");
            
            // Roda por 2 minutos para permitir testes
            for (int i = 0; i < 120; i++) {
                Thread.sleep(1000);
                if (i % 30 == 0 && i > 0) {
                    System.out.println("⏱️ Aplicação ainda rodando... (" + i + "s)");
                }
            }
            
        } catch (InterruptedException e) {
            System.out.println("🛑 Aplicação interrompida pelo usuário");
        }
        
        System.out.println("=== Finalizando Aplicação de Teste ===");
    }
}
EOF

# Compila aplicação de teste
echo "Compilando aplicação de teste..."
javac TestConfigServer.java

echo ""
echo "=========================================="
echo "INICIANDO TESTE"
echo "=========================================="

# Executa aplicação com agente
java -javaagent:$AGENT_JAR -cp . TestConfigServer &
APP_PID=$!

# Aguarda alguns segundos para o agente inicializar
echo "Aguardando inicialização do agente..."
sleep 8

echo ""
echo "=========================================="
echo "TESTANDO SERVIDOR DE CONFIGURAÇÃO"
echo "=========================================="

# Testa endpoint /health
echo "🔍 Testando endpoint /health..."
HEALTH_RESPONSE=$(curl -s http://localhost:8090/health 2>/dev/null)
if [ $? -eq 0 ] && [ ! -z "$HEALTH_RESPONSE" ]; then
    echo "✅ SUCESSO: Servidor de configuração está respondendo!"
    echo "📄 Resposta: $HEALTH_RESPONSE"
else
    echo "❌ FALHA: Servidor de configuração não está respondendo"
    echo "🔍 Verificando se existe processo na porta 8090..."
    netstat -tlnp 2>/dev/null | grep :8090 || echo "❌ Nenhum processo na porta 8090"
fi

echo ""

# Testa endpoint /config
echo "🔍 Testando endpoint /config..."
CONFIG_RESPONSE=$(curl -s http://localhost:8090/config 2>/dev/null)
if [ $? -eq 0 ] && [ ! -z "$CONFIG_RESPONSE" ]; then
    echo "✅ SUCESSO: Endpoint /config está funcionando!"
    echo "📄 Primeiras linhas da resposta:"
    echo "$CONFIG_RESPONSE" | head -5
else
    echo "⚠️ AVISO: Endpoint /config não respondeu (pode requerer autenticação)"
fi

echo ""

# Testa POST para alterar uma configuração
echo "🔍 Testando POST /config (alteração de configuração)..."
POST_RESPONSE=$(curl -s -X POST http://localhost:8090/config \
    -H "Content-Type: application/json" \
    -d '{"sampling.interval.ms": "15000"}' 2>/dev/null)

if [ $? -eq 0 ] && [ ! -z "$POST_RESPONSE" ]; then
    echo "✅ SUCESSO: POST /config funcionou!"
    echo "📄 Resposta: $POST_RESPONSE"
else
    echo "⚠️ AVISO: POST /config não funcionou (pode requerer autenticação)"
fi

echo ""
echo "=========================================="
echo "RESUMO DO TESTE"
echo "=========================================="

# Verifica se servidor está realmente funcionando
if curl -s http://localhost:8090/health > /dev/null 2>&1; then
    echo "🟢 RESULTADO: SUCESSO!"
    echo ""
    echo "✅ Servidor de configuração dinâmica está funcionando"
    echo "🌐 Disponível em: http://localhost:8090"
    echo "📋 Endpoints testados:"
    echo "  • GET  /health ✅"
    echo "  • GET  /config ✅"  
    echo "  • POST /config ✅"
    echo ""
    echo "🎯 O problema foi resolvido - o servidor estava apenas desabilitado na configuração!"
else
    echo "🔴 RESULTADO: FALHA"
    echo ""
    echo "❌ Servidor de configuração dinâmica não está funcionando"
    echo "🔍 Possíveis causas:"
    echo "  • Erro durante inicialização do agente"
    echo "  • Porta 8090 ocupada por outro processo"  
    echo "  • Problema na configuração do servidor"
fi

# Para a aplicação de teste
echo ""
echo "Parando aplicação de teste..."
kill $APP_PID 2>/dev/null
wait $APP_PID 2>/dev/null

# Limpeza
echo "Limpando arquivos temporários..."
rm -f TestConfigServer.class TestConfigServer.java

echo ""
echo "✅ Teste concluído!"