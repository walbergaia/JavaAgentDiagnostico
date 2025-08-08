#!/bin/bash

echo "=========================================="
echo "Teste do Armazenamento Local do Agente"
echo "=========================================="

# Verifica se existe o JAR do agente
AGENT_JAR="build/libs/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar"

if [ ! -f "$AGENT_JAR" ]; then
    echo "ERRO: JAR do agente não encontrado em $AGENT_JAR"
    echo "Execute './build.sh' ou './gradlew build' primeiro"
    exit 1
fi

# Cria configuração de teste com servidor inválido
echo "Criando arquivo de configuração para teste..."
cat > test-agent.properties << 'EOF'
# Configuração de teste - servidor inválido para forçar fallback local
enabled=true
agent.name=test-agent
application.name=TestApp
sampling.interval.ms=5000

# REST com URL inválida para simular falha
send.rest.enabled=true
rest.endpoint.url=http://localhost:9999/invalid-endpoint
rest.timeout.ms=2000
rest.retry.max.attempts=1

# Armazenamento local HABILITADO
local.storage.enabled=true
local.storage.path=./test-storage
local.storage.max.files=100

# Fila pequena para testar overflow
data.queue.max.size=3

# Outros módulos
enable.exception.capture=true
enable.sql.capture=false
enable.gc.metrics=true
enable.system.cpu.mem=true

# Configuração do servidor (desabilitado para este teste)
config.server.enabled=false
EOF

# Limpa diretório de teste anterior
echo "Limpando diretório de teste anterior..."
rm -rf ./test-storage
mkdir -p ./test-storage

# Cria uma aplicação de teste simples
echo "Criando aplicação de teste..."
cat > TestLocalStorage.java << 'EOF'
public class TestLocalStorage {
    public static void main(String[] args) {
        System.out.println("=== Aplicação de Teste Iniciada ===");
        System.out.println("Aguardando coleta de métricas...");
        
        try {
            // Aguarda 20 segundos para permitir algumas coletas
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            // Ignorar
        }
        
        System.out.println("=== Finalizando Aplicação de Teste ===");
    }
}
EOF

# Compila aplicação de teste
echo "Compilando aplicação de teste..."
javac TestLocalStorage.java

# Executa com o agente
echo "Executando aplicação com o agente (servidor inválido)..."
echo "IMPORTANTE: O agente deve falhar no envio HTTP e gerar arquivos locais"
echo ""

# Copia o agent.properties de teste para o classpath
cp test-agent.properties src/main/resources/agent.properties

# Rebuilda para incluir a nova configuração
echo "Reconstruindo JAR com configuração de teste..."
./gradlew shadowJar -q

# Executa teste
java -javaagent:$AGENT_JAR -cp . TestLocalStorage

echo ""
echo "=========================================="
echo "Verificando resultados..."
echo "=========================================="

# Verifica se arquivos foram gerados
if [ -d "./test-storage" ]; then
    FILE_COUNT=$(ls -1 ./test-storage/metrics_*.json 2>/dev/null | wc -l)
    echo "✅ Diretório de storage criado: ./test-storage"
    echo "📁 Arquivos JSON encontrados: $FILE_COUNT"
    
    if [ $FILE_COUNT -gt 0 ]; then
        echo ""
        echo "📋 Lista de arquivos gerados:"
        ls -la ./test-storage/metrics_*.json
        
        echo ""
        echo "📄 Conteúdo do primeiro arquivo (primeiros 10 linhas):"
        FIRST_FILE=$(ls ./test-storage/metrics_*.json | head -n 1)
        head -n 10 "$FIRST_FILE" | jq . 2>/dev/null || head -n 10 "$FIRST_FILE"
        
        echo ""
        echo "✅ SUCESSO: Arquivos locais foram gerados corretamente!"
        echo "   O fallback para armazenamento local está funcionando."
        
    else
        echo "❌ FALHA: Nenhum arquivo JSON foi gerado no diretório local"
        echo "   Possível problema na implementação do fallback"
    fi
else
    echo "❌ FALHA: Diretório de storage não foi criado"
    echo "   O LocalStorageManager não foi inicializado corretamente"
fi

# Limpa arquivos temporários
echo ""
echo "Limpando arquivos temporários..."
rm -f TestLocalStorage.class TestLocalStorage.java test-agent.properties

# Restaura configuração original
git checkout src/main/resources/agent.properties 2>/dev/null || echo "Configuração original não restaurada (não encontrada no git)"

echo "Teste concluído!"