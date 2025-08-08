import java.sql.*;

public class TestApp {
    public static void main(String[] args) {
        System.out.println("Aplicação de teste iniciada com o agente JavaAgentDiagnostico");
        
        // Test SQL instrumentation if H2 is available
        testSqlInstrumentation();
        
        // Simular alguma atividade
        try {
            Thread.sleep(2000);
            System.out.println("Aplicação executando...");
            Thread.sleep(2000);
            System.out.println("Aplicação finalizando...");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private static void testSqlInstrumentation() {
        try {
            System.out.println("--- Testando instrumentação SQL ---");
            
            // Try to create in-memory H2 database
            String url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
            Connection conn = DriverManager.getConnection(url, "sa", "");
            System.out.println("Conexão H2 estabelecida");
            
            // Test Statement
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE test (id INT, name VARCHAR(50))");
            System.out.println("Tabela criada via Statement");
            
            // Test PreparedStatement
            PreparedStatement ps = conn.prepareStatement("INSERT INTO test VALUES (?, ?)");
            ps.setInt(1, 1);
            ps.setString(2, "Test User");
            ps.executeUpdate();
            System.out.println("Dados inseridos via PreparedStatement");
            
            // Test query
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test");
            if (rs.next()) {
                System.out.println("Contagem de registros: " + rs.getInt(1));
            }
            
            conn.close();
            System.out.println("Teste SQL concluído");
            
        } catch (Exception e) {
            System.out.println("SQL test skipped (H2 not available): " + e.getMessage());
        }
    }
}
