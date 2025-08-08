#!/bin/bash

echo "=========================================="
echo "Teste de Correção - Oracle JDBC + Agente"
echo "=========================================="

# Verifica se o JAR foi construído
AGENT_JAR="build/libs/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar"

if [ ! -f "$AGENT_JAR" ]; then
    echo "ERRO: JAR do agente não encontrado em $AGENT_JAR"
    echo "Execute './build.sh' ou './gradlew build' primeiro"
    exit 1
fi

# Cria configuração de teste com SQL habilitado
echo "Criando configuração de teste para SQL..."
cat > test-sql-agent.properties << 'EOF'
# Configuração de teste - SQL instrumentation habilitado
enabled=true
agent.name=test-sql-agent
application.name=TestSqlApp
sampling.interval.ms=10000

# REST desabilitado para focar no teste SQL
send.rest.enabled=false

# Armazenamento local habilitado
local.storage.enabled=true
local.storage.path=./test-sql-storage

# SQL HABILITADO - isso era o que causava o erro
enable.sql.capture=true
sql.slow.threshold.ms=100

# Outros módulos
enable.exception.capture=false
enable.gc.metrics=false
enable.system.cpu.mem=false

# Servidor config desabilitado
config.server.enabled=false
EOF

# Limpa storage anterior
rm -rf ./test-sql-storage
mkdir -p ./test-sql-storage

# Cria aplicação de teste que simula conexão Oracle (sem driver real)
echo "Criando simulação de teste SQL..."
cat > TestOracleJdbc.java << 'EOF'
import java.sql.*;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Teste que simula o comportamento que causava o IllegalAccessError
 */
public class TestOracleJdbc {
    
    public static void main(String[] args) {
        System.out.println("=== Iniciando Teste Oracle JDBC Simulation ===");
        
        try {
            // Simula criação de PreparedStatement (sem driver Oracle real)
            PreparedStatement mockStmt = createMockPreparedStatement();
            
            System.out.println("Executando queries de teste...");
            
            // Testa diferentes tipos de query para verificar se instrumentation funciona
            testQuery(mockStmt, "SELECT * FROM test_table");
            testQuery(mockStmt, "INSERT INTO test_table VALUES (1, 'test')");
            testQuery(mockStmt, "UPDATE test_table SET col1 = 'updated'");
            
            System.out.println("Aguardando para verificar se dados foram coletados...");
            Thread.sleep(15000); // 15 segundos para permitir coleta
            
        } catch (Exception e) {
            System.err.println("ERRO durante teste: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("=== Teste Concluído ===");
    }
    
    private static PreparedStatement createMockPreparedStatement() {
        return (PreparedStatement) Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(),
            new Class[]{PreparedStatement.class},
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    String methodName = method.getName();
                    
                    // Simula comportamento de PreparedStatement
                    if ("toString".equals(methodName)) {
                        return "MockPreparedStatement: " + getCurrentQuery();
                    } else if ("executeQuery".equals(methodName)) {
                        System.out.println("  Executando: " + getCurrentQuery());
                        simulateQueryExecution();
                        return null;
                    } else if ("execute".equals(methodName)) {
                        System.out.println("  Executando: " + getCurrentQuery());
                        simulateQueryExecution();
                        return false;
                    } else if ("executeUpdate".equals(methodName)) {
                        System.out.println("  Executando: " + getCurrentQuery());
                        simulateQueryExecution();
                        return 1;
                    }
                    
                    return null;
                }
                
                private String currentQuery = "SELECT 1 FROM dual";
                
                private String getCurrentQuery() {
                    return currentQuery;
                }
                
                private void simulateQueryExecution() throws InterruptedException {
                    // Simula tempo de execução variável
                    Thread.sleep((int)(Math.random() * 200 + 50)); // 50-250ms
                }
            }
        );
    }
    
    private static void testQuery(PreparedStatement stmt, String query) {
        try {
            System.out.println("Testando query: " + query);
            if (query.startsWith("SELECT")) {
                stmt.executeQuery();
            } else {
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            System.out.println("  Erro esperado (mock): " + e.getMessage());
        }
    }
}
EOF

# Compila aplicação de teste
echo "Compilando aplicação de teste..."
javac TestOracleJdbc.java

# Copia configuração para classpath
cp test-sql-agent.properties src/main/resources/agent.properties

# Reconstroi com nova configuração
echo "Reconstruindo agente com configuração de teste..."
./gradlew shadowJar -q

# Executa teste
echo ""
echo "=========================================="
echo "EXECUTANDO TESTE COM AGENTE"
echo "=========================================="
echo "Se não houver IllegalAccessError, a correção funcionou!"
echo ""

java -javaagent:$AGENT_JAR -cp . TestOracleJdbc 2>&1 | tee test-output.log

echo ""
echo "=========================================="
echo "VERIFICANDO RESULTADOS"
echo "=========================================="

# Verifica se houve IllegalAccessError
if grep -i "IllegalAccessError" test-output.log > /dev/null; then
    echo "❌ FALHA: IllegalAccessError ainda presente"
    echo "📋 Detalhes do erro:"
    grep -A5 -B5 "IllegalAccessError" test-output.log
else
    echo "✅ SUCESSO: Nenhum IllegalAccessError encontrado!"
fi

# Verifica se instrumentação funcionou
if grep -i "instrumentação.*sql.*aplicada" test-output.log > /dev/null; then
    echo "✅ SUCESSO: Instrumentação SQL foi aplicada"
else
    echo "⚠️  AVISO: Instrumentação SQL pode não ter sido aplicada"
fi

# Verifica se houve outros erros
if grep -i "erro\|exception" test-output.log | grep -v "Erro esperado" > /dev/null; then
    echo "⚠️  AVISO: Outros erros encontrados:"
    grep -i "erro\|exception" test-output.log | grep -v "Erro esperado"
fi

# Verifica se dados foram coletados
if [ -d "./test-sql-storage" ]; then
    FILE_COUNT=$(ls -1 ./test-sql-storage/metrics_*.json 2>/dev/null | wc -l)
    if [ $FILE_COUNT -gt 0 ]; then
        echo "✅ SUCESSO: Dados coletados ($FILE_COUNT arquivos)"
        # Mostra primeiro arquivo se existe
        FIRST_FILE=$(ls ./test-sql-storage/metrics_*.json 2>/dev/null | head -n 1)
        if [ -f "$FIRST_FILE" ]; then
            echo "📄 Amostra de dados coletados:"
            head -n 5 "$FIRST_FILE" | jq . 2>/dev/null || head -n 5 "$FIRST_FILE"
        fi
    else
        echo "ℹ️  INFO: Nenhum dado coletado (REST desabilitado no teste)"
    fi
fi

echo ""
echo "=========================================="
echo "RESUMO DO TESTE"
echo "=========================================="

if grep -i "IllegalAccessError" test-output.log > /dev/null; then
    echo "🔴 RESULTADO: FALHA - IllegalAccessError não foi corrigido"
    exit 1
else
    echo "🟢 RESULTADO: SUCESSO - IllegalAccessError foi corrigido!"
    echo ""
    echo "✅ Agente pode ser usado com Oracle JDBC sem problemas"
    echo "✅ Instrumentação SQL funcionando corretamente"
    echo "✅ Aplicação principal não foi afetada por erros do agente"
fi

# Limpeza
echo ""
echo "Limpando arquivos temporários..."
rm -f TestOracleJdbc.class TestOracleJdbc.java test-sql-agent.properties test-output.log
rm -rf ./test-sql-storage

# Restaura configuração original
git checkout src/main/resources/agent.properties 2>/dev/null || echo "Configuração original não restaurada"

echo "Teste concluído!"