#!/bin/bash

# Script para testar a implementação Enhanced SQL

echo "============================================================"
echo "  Testando Enhanced SQL Implementation"
echo "============================================================"

# Compila o projeto
echo "1. Compilando projeto..."
if ./gradlew clean shadowJar; then
    echo "✅ Compilação bem-sucedida"
else
    echo "❌ Falha na compilação"
    exit 1
fi

# Verifica se o JAR foi criado
JAR_FILE="build/libs/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR não encontrado: $JAR_FILE"
    exit 1
fi

# Cria aplicação de teste simples
cat > TestEnhancedSQL.java << 'EOF'
import java.sql.*;
import java.util.Properties;

public class TestEnhancedSQL {
    public static void main(String[] args) throws Exception {
        System.out.println("🔍 Testando Enhanced SQL Monitoring...");
        
        // Simula conexões com diferentes pools
        testH2Database();
        testSlowQueries();
        testComplexQueries();
        testRepeatedQueries();
        
        // Aguarda coleta de métricas
        System.out.println("⏳ Aguardando coleta de métricas...");
        Thread.sleep(35000); // Aguarda > sampling.interval.ms
        
        System.out.println("✅ Teste concluído!");
    }
    
    static void testH2Database() throws Exception {
        System.out.println("\n📊 Teste 1: Conexão H2 em memória");
        
        try (Connection conn = DriverManager.getConnection(
            "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "")) {
            
            // Cria tabela de teste
            try (PreparedStatement stmt = conn.prepareStatement(
                "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(255), email VARCHAR(255))")) {
                stmt.execute();
            }
            
            // INSERT queries
            for (int i = 1; i <= 5; i++) {
                try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO users VALUES (?, ?, ?)")) {
                    stmt.setInt(1, i);
                    stmt.setString(2, "User" + i);
                    stmt.setString(3, "user" + i + "@test.com");
                    stmt.executeUpdate();
                }
            }
        }
    }
    
    static void testSlowQueries() throws Exception {
        System.out.println("\n🐌 Teste 2: Query lenta simulada");
        
        try (Connection conn = DriverManager.getConnection(
            "jdbc:h2:mem:testdb", "sa", "")) {
            
            // Simula query lenta com DELAY
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT *, DELAY('2000') FROM users WHERE id > ?")) {
                stmt.setInt(1, 0);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    // Processa resultado
                }
            }
        }
    }
    
    static void testComplexQueries() throws Exception {
        System.out.println("\n🔧 Teste 3: Queries complexas");
        
        try (Connection conn = DriverManager.getConnection(
            "jdbc:h2:mem:testdb", "sa", "")) {
            
            // Query com JOIN simulado (subconsulta)
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT u1.name, u2.email FROM users u1, users u2 " +
                "WHERE u1.id < u2.id AND u1.name LIKE ? ORDER BY u1.id")) {
                stmt.setString(1, "%User%");
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    // Processa resultado
                }
            }
            
            // Query com GROUP BY
            try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*), LENGTH(name) FROM users GROUP BY LENGTH(name)")) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    // Processa resultado
                }
            }
        }
    }
    
    static void testRepeatedQueries() throws Exception {
        System.out.println("\n🔄 Teste 4: Queries repetidas (para agrupamento)");
        
        try (Connection conn = DriverManager.getConnection(
            "jdbc:h2:mem:testdb", "sa", "")) {
            
            // Executa a mesma query múltiplas vezes com parâmetros diferentes
            for (int i = 1; i <= 10; i++) {
                try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM users WHERE id = ?")) {
                    stmt.setInt(1, i % 5 + 1); // Reutiliza IDs 1-5
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        // Processa resultado
                    }
                }
            }
        }
    }
}
EOF

# Compila aplicação de teste
echo "2. Compilando aplicação de teste..."
if javac -cp "$JAR_FILE:." TestEnhancedSQL.java; then
    echo "✅ Aplicação de teste compilada"
else
    echo "❌ Falha na compilação da aplicação de teste"
    exit 1
fi

# Executa teste com o agent
echo "3. Executando teste com Enhanced SQL agent..."
echo "   Parâmetros: -javaagent:$JAR_FILE"

java -javaagent:"$JAR_FILE" -cp ".:$JAR_FILE" TestEnhancedSQL

echo ""
echo "============================================================"
echo "  Teste Enhanced SQL Concluído"
echo "============================================================"
echo ""
echo "🔍 Verifique os logs acima para:"
echo "  • Queries SQL capturadas e categorizadas"
echo "  • Detecção de queries lentas (>1000ms)"
echo "  • Normalização e agrupamento de queries"
echo "  • Métricas de connection pool (se disponível)"
echo "  • Performance insights automáticos"
echo ""
echo "📊 Para análise detalhada, verifique:"
echo "  • Endpoint REST configurado recebe dados completos"
echo "  • Arquivo de storage local (se habilitado)"
echo "  • Logs do agente com insights de performance"

# Limpeza
rm -f TestEnhancedSQL.java TestEnhancedSQL.class