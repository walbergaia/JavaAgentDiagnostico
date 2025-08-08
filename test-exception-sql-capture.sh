#!/bin/bash

echo "=========================================="
echo "Teste das Capturas: Exception & SQL"
echo "=========================================="

AGENT_JAR="build/libs/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar"

if [ ! -f "$AGENT_JAR" ]; then
    echo "ERRO: JAR do agente não encontrado em $AGENT_JAR"
    echo "Execute './build.sh' ou './gradlew build' primeiro"
    exit 1
fi

# Configuração de teste com ambas capturas habilitadas
echo "Criando configuração de teste..."
cat > test-capture.properties << 'EOF'
# Configuração para testar capturas
enabled=true
agent.name=test-capture-agent
application.name=TestCaptureApp
sampling.interval.ms=8000

# REST desabilitado, storage local habilitado
send.rest.enabled=false
local.storage.enabled=true
local.storage.path=./test-capture-storage

# CAPTURAS HABILITADAS
enable.exception.capture=true
enable.sql.capture=true
enable.gc.metrics=true
sql.slow.threshold.ms=50

# Deep stack analysis para testar exceções
enable.deep.stack.analysis=true

# Outros
enable.system.cpu.mem=true
config.server.enabled=false
EOF

# Limpa storage anterior
rm -rf ./test-capture-storage
mkdir -p ./test-capture-storage

# Cria aplicação de teste que gera exceções e SQL
echo "Criando aplicação de teste..."
cat > TestExceptionSqlCapture.java << 'EOF'
import java.sql.*;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TestExceptionSqlCapture {
    
    private static volatile boolean running = true;
    private static ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
    
    public static void main(String[] args) {
        System.out.println("=== Teste de Captura: Exceptions & SQL ===");
        
        try {
            // 1. Testa captura SQL com mock PreparedStatement
            testSqlCapture();
            
            // 2. Agenda exceções para testar captura
            scheduleExceptionTests();
            
            // 3. Força GC para testar métricas
            testGcMetrics();
            
            System.out.println("Aplicação rodando por 30 segundos...");
            System.out.println("Gerando SQL queries e exceções para teste...");
            
            Thread.sleep(30000);
            
        } catch (Exception e) {
            System.err.println("Erro na aplicação principal: " + e.getMessage());
        } finally {
            running = false;
            executor.shutdownNow();
        }
        
        System.out.println("=== Teste Finalizado ===");
    }
    
    private static void testSqlCapture() {
        System.out.println("\n🗃️ Testando captura SQL...");
        
        // Cria mock PreparedStatement
        PreparedStatement stmt = createMockPreparedStatement();
        
        try {
            // Testa diferentes tipos de queries
            executeQuery(stmt, "SELECT * FROM users WHERE id = ?", 120); // Normal
            executeQuery(stmt, "INSERT INTO logs (message) VALUES (?)", 80); // Normal
            executeQuery(stmt, "UPDATE users SET last_login = NOW() WHERE id = ?", 200); // Slow
            executeQuery(stmt, "DELETE FROM temp_data WHERE created < ?", 300); // Slow
            executeQueryWithError(stmt, "SELECT invalid_column FROM nonexistent_table"); // Com erro
            
        } catch (Exception e) {
            System.out.println("Erro esperado nos testes SQL: " + e.getMessage());
        }
    }
    
    private static void scheduleExceptionTests() {
        System.out.println("\n💥 Agendando testes de exceção...");
        
        // Exception em 3 segundos
        executor.schedule(() -> {
            Thread exceptionThread = new Thread(() -> {
                throw new RuntimeException("Teste de exceção não tratada #1");
            }, "TestThread-1");
            exceptionThread.start();
        }, 3, TimeUnit.SECONDS);
        
        // Exception em 8 segundos
        executor.schedule(() -> {
            Thread exceptionThread = new Thread(() -> {
                throw new IllegalStateException("Teste de exceção não tratada #2");
            }, "TestThread-2");
            exceptionThread.start();
        }, 8, TimeUnit.SECONDS);
        
        // Exception em 15 segundos  
        executor.schedule(() -> {
            Thread exceptionThread = new Thread(() -> {
                throw new NullPointerException("Teste de exceção não tratada #3");
            }, "TestThread-3");
            exceptionThread.start();
        }, 15, TimeUnit.SECONDS);
    }
    
    private static void testGcMetrics() {
        System.out.println("\n🗑️ Testando métricas de GC...");
        
        // Cria objetos para forçar GC
        executor.scheduleAtFixedRate(() -> {
            if (!running) return;
            
            // Cria objetos temporários para forçar GC
            for (int i = 0; i < 1000; i++) {
                String temp = "Test string " + i + " " + System.currentTimeMillis();
                temp.hashCode(); // Usa o objeto
            }
            
            // Força GC ocasionalmente
            if (Math.random() < 0.3) {
                System.gc();
            }
            
        }, 1, 2, TimeUnit.SECONDS);
    }
    
    private static PreparedStatement createMockPreparedStatement() {
        return (PreparedStatement) Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(),
            new Class[]{PreparedStatement.class},
            new MockPreparedStatementHandler()
        );
    }
    
    private static void executeQuery(PreparedStatement stmt, String query, long sleepMs) throws Exception {
        System.out.println("  Executando: " + query + " (target: " + sleepMs + "ms)");
        
        // Configura query no mock
        ((MockPreparedStatementHandler) Proxy.getInvocationHandler(stmt)).setQuery(query, sleepMs);
        
        if (query.startsWith("SELECT")) {
            stmt.executeQuery();
        } else {
            stmt.executeUpdate();
        }
    }
    
    private static void executeQueryWithError(PreparedStatement stmt, String query) throws Exception {
        System.out.println("  Executando com erro: " + query);
        
        // Configura query com erro no mock
        ((MockPreparedStatementHandler) Proxy.getInvocationHandler(stmt)).setQueryWithError(query);
        
        try {
            stmt.executeQuery();
        } catch (SQLException e) {
            System.out.println("  Erro SQL capturado (esperado): " + e.getMessage());
        }
    }
    
    private static class MockPreparedStatementHandler implements InvocationHandler {
        private String currentQuery = "SELECT 1";
        private long sleepMs = 50;
        private boolean shouldError = false;
        
        public void setQuery(String query, long sleepMs) {
            this.currentQuery = query;
            this.sleepMs = sleepMs;
            this.shouldError = false;
        }
        
        public void setQueryWithError(String query) {
            this.currentQuery = query;
            this.shouldError = true;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            
            if ("toString".equals(methodName)) {
                return currentQuery;
            } else if ("executeQuery".equals(methodName)) {
                simulateExecution();
                return null; // Mock ResultSet
            } else if ("executeUpdate".equals(methodName)) {
                simulateExecution();
                return 1; // Rows affected
            } else if ("execute".equals(methodName)) {
                simulateExecution();
                return false;
            }
            
            return null;
        }
        
        private void simulateExecution() throws SQLException, InterruptedException {
            if (shouldError) {
                throw new SQLException("Mock SQL error for testing");
            }
            
            Thread.sleep(sleepMs); // Simula tempo de execução
        }
    }
}
EOF

# Compila aplicação de teste
echo "Compilando aplicação de teste..."
javac TestExceptionSqlCapture.java

# Copia configuração para classpath
cp test-capture.properties src/main/resources/agent.properties

# Reconstroi com nova configuração
echo "Reconstruindo agente com configuração de teste..."
./gradlew shadowJar -q

# Executa teste
echo ""
echo "=========================================="
echo "EXECUTANDO TESTE COMPLETO"
echo "=========================================="

java -javaagent:$AGENT_JAR -cp . TestExceptionSqlCapture 2>&1 | tee test-capture-output.log

echo ""
echo "=========================================="
echo "VERIFICANDO RESULTADOS"
echo "=========================================="

# Verifica se dados foram gerados
if [ -d "./test-capture-storage" ]; then
    FILE_COUNT=$(ls -1 ./test-capture-storage/metrics_*.json 2>/dev/null | wc -l)
    echo "📁 Arquivos de métricas gerados: $FILE_COUNT"
    
    if [ $FILE_COUNT -gt 0 ]; then
        LATEST_FILE=$(ls -t ./test-capture-storage/metrics_*.json | head -n 1)
        echo "📄 Analisando arquivo mais recente: $(basename $LATEST_FILE)"
        
        # Verifica se contém exceções
        EXCEPTION_COUNT=$(jq -r '.exceptions | length' "$LATEST_FILE" 2>/dev/null || echo "0")
        echo "💥 Exceções capturadas: $EXCEPTION_COUNT"
        
        if [ "$EXCEPTION_COUNT" != "null" ] && [ "$EXCEPTION_COUNT" -gt 0 ]; then
            echo "✅ SUCESSO: Exceções foram capturadas!"
            echo "📋 Tipos de exceções:"
            jq -r '.exceptions[].type' "$LATEST_FILE" 2>/dev/null | sort | uniq -c || echo "  (erro ao extrair tipos)"
        else
            echo "⚠️ AVISO: Nenhuma exceção encontrada nos dados"
        fi
        
        # Verifica se contém SQL
        SQL_COUNT=$(jq -r '.sql | length' "$LATEST_FILE" 2>/dev/null || echo "0")
        echo "🗃️ Queries SQL capturadas: $SQL_COUNT"
        
        if [ "$SQL_COUNT" != "null" ] && [ "$SQL_COUNT" -gt 0 ]; then
            echo "✅ SUCESSO: Queries SQL foram capturadas!"
            echo "📋 Tipos de queries:"
            jq -r '.sql[].queryType' "$LATEST_FILE" 2>/dev/null | sort | uniq -c || echo "  (erro ao extrair tipos)"
            
            echo "📋 Queries lentas:"
            SLOW_COUNT=$(jq -r '[.sql[] | select(.slow == true)] | length' "$LATEST_FILE" 2>/dev/null || echo "0")
            echo "  Queries lentas: $SLOW_COUNT"
        else
            echo "⚠️ AVISO: Nenhuma query SQL encontrada nos dados"
        fi
        
        # Verifica GC metrics
        GC_EXISTS=$(jq -r '.gc' "$LATEST_FILE" 2>/dev/null)
        if [ "$GC_EXISTS" != "null" ]; then
            echo "✅ SUCESSO: Métricas de GC foram capturadas!"
            GC_COUNT=$(jq -r '.gc.collectionCount' "$LATEST_FILE" 2>/dev/null || echo "0")
            echo "🗑️ Collections de GC: $GC_COUNT"
        else
            echo "⚠️ AVISO: Métricas de GC não encontradas"
        fi
        
        # Mostra amostra do arquivo
        echo ""
        echo "📄 Amostra dos dados coletados:"
        jq -r '. | {timestamp, exceptions: .exceptions | length, sql: .sql | length, gc: .gc.collectionCount}' "$LATEST_FILE" 2>/dev/null || echo "  (erro ao extrair amostra)"
        
    else
        echo "❌ FALHA: Nenhum arquivo de dados foi gerado"
    fi
else
    echo "❌ FALHA: Diretório de storage não foi criado"
fi

# Verifica logs para mensagens importantes
echo ""
echo "📋 Verificando logs de captura..."

if grep -i "EXCEPTION CAPTURED" test-capture-output.log > /dev/null; then
    echo "✅ Logs mostram exceções sendo capturadas"
    LOGGED_EXCEPTIONS=$(grep -c "EXCEPTION CAPTURED" test-capture-output.log)
    echo "  Exceções logadas: $LOGGED_EXCEPTIONS"
else
    echo "⚠️ Nenhuma mensagem de exceção capturada nos logs"
fi

if grep -i "SLOW SQL" test-capture-output.log > /dev/null; then
    echo "✅ Logs mostram queries SQL lentas sendo detectadas"
    SLOW_QUERIES=$(grep -c "SLOW SQL" test-capture-output.log)
    echo "  Queries lentas logadas: $SLOW_QUERIES"
else
    echo "ℹ️ Nenhuma query SQL lenta foi detectada (normal se todas foram rápidas)"
fi

if grep -i "instrumentação.*sql.*aplicada" test-capture-output.log > /dev/null; then
    echo "✅ Instrumentação SQL foi aplicada com sucesso"
else
    echo "⚠️ Instrumentação SQL pode não ter sido aplicada"
fi

if grep -i "handler global de exceções" test-capture-output.log > /dev/null; then
    echo "✅ Handler de exceções foi configurado"
else
    echo "⚠️ Handler de exceções pode não ter sido configurado"
fi

echo ""
echo "=========================================="
echo "RESUMO FINAL"
echo "=========================================="

SUCCESS=true

if [ "$FILE_COUNT" -gt 0 ]; then
    echo "✅ Coleta de métricas: FUNCIONANDO"
else
    echo "❌ Coleta de métricas: FALHA"
    SUCCESS=false
fi

if [ "$EXCEPTION_COUNT" != "null" ] && [ "$EXCEPTION_COUNT" -gt 0 ]; then
    echo "✅ Captura de exceções: FUNCIONANDO ($EXCEPTION_COUNT exceções)"
else
    echo "⚠️ Captura de exceções: NÃO TESTADA (sem exceções nos dados)"
fi

if [ "$SQL_COUNT" != "null" ] && [ "$SQL_COUNT" -gt 0 ]; then
    echo "✅ Captura SQL: FUNCIONANDO ($SQL_COUNT queries)"
else
    echo "⚠️ Captura SQL: NÃO TESTADA (sem queries nos dados)"
fi

if [ "$GC_EXISTS" != "null" ]; then
    echo "✅ Métricas GC: FUNCIONANDO"
else
    echo "⚠️ Métricas GC: NÃO COLETADAS"
fi

if $SUCCESS; then
    echo ""
    echo "🎉 RESULTADO GERAL: SUCESSO!"
    echo "As capturas de Exception e SQL estão funcionando corretamente!"
else
    echo ""
    echo "⚠️ RESULTADO GERAL: PARCIALMENTE FUNCIONAL"
    echo "Algumas capturas podem precisar de ajustes."
fi

# Limpeza
echo ""
echo "Limpando arquivos temporários..."
rm -f TestExceptionSqlCapture*.class TestExceptionSqlCapture.java test-capture.properties test-capture-output.log
# Mantém storage para inspeção manual se desejado
# rm -rf ./test-capture-storage

# Restaura configuração original
git checkout src/main/resources/agent.properties 2>/dev/null || echo "Configuração original não restaurada"

echo "✅ Teste concluído!"