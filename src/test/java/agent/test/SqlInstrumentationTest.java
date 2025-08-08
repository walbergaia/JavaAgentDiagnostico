package agent.test;

import java.sql.*;

/**
 * Simple test class to verify SQL instrumentation functionality
 */
public class SqlInstrumentationTest {
    
    public static void main(String[] args) {
        System.out.println("Starting SQL Instrumentation Test...");
        
        try {
            // Test 1: H2 in-memory database test with PreparedStatement
            testPreparedStatement();
            
            // Test 2: Test with regular Statement
            testStatement();
            
            System.out.println("SQL Instrumentation Test completed successfully!");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void testPreparedStatement() throws Exception {
        System.out.println("\n=== Testing PreparedStatement ===");
        
        // Using H2 database for testing (should be available in classpath)
        String url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
        
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            // Create test table
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE test_users (id INT PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("INSERT INTO test_users VALUES (1, 'Alice'), (2, 'Bob')");
            }
            
            // Test PreparedStatement - this should be captured by SqlTimingAdvice
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM test_users WHERE id = ?")) {
                ps.setInt(1, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("PreparedStatement result: " + rs.getInt("id") + " - " + rs.getString("name"));
                    }
                }
            }
            
            // Test PreparedStatement update
            try (PreparedStatement ps = conn.prepareStatement("UPDATE test_users SET name = ? WHERE id = ?")) {
                ps.setString(1, "Alice Updated");
                ps.setInt(2, 1);
                int rowsUpdated = ps.executeUpdate();
                System.out.println("PreparedStatement updated " + rowsUpdated + " rows");
            }
        }
    }
    
    private static void testStatement() throws Exception {
        System.out.println("\n=== Testing Statement ===");
        
        String url = "jdbc:h2:mem:testdb2;DB_CLOSE_DELAY=-1";
        
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (Statement stmt = conn.createStatement()) {
                // Create table
                stmt.execute("CREATE TABLE test_products (id INT PRIMARY KEY, name VARCHAR(100))");
                
                // Test Statement - this should be captured by StatementTimingAdvice
                try (ResultSet rs = stmt.executeQuery("SELECT 1 as test_value")) {
                    while (rs.next()) {
                        System.out.println("Statement result: " + rs.getInt("test_value"));
                    }
                }
                
                // Test Statement update
                int rowsInserted = stmt.executeUpdate("INSERT INTO test_products VALUES (1, 'Product A')");
                System.out.println("Statement inserted " + rowsInserted + " rows");
            }
        }
    }
}