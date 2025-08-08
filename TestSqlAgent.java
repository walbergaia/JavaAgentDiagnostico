import java.sql.*;

/**
 * Simple test application to verify SQL agent instrumentation
 */
public class TestSqlAgent {
    
    public static void main(String[] args) {
        System.out.println("=== Test SQL Agent Instrumentation ===");
        
        try {
            // Load H2 driver
            Class.forName("org.h2.Driver");
            
            System.out.println("1. Testing PreparedStatement instrumentation...");
            testPreparedStatement();
            
            System.out.println("\n2. Testing Statement instrumentation...");
            testStatement();
            
            System.out.println("\n3. Testing exception handling...");
            testExceptionHandling();
            
            System.out.println("\nTest completed. Check console for SQL instrumentation logs.");
            
            // Sleep to allow agent to process any remaining data
            Thread.sleep(2000);
            
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testPreparedStatement() throws Exception {
        String url = "jdbc:h2:mem:testdb1;DB_CLOSE_DELAY=-1";
        
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            // Setup test data
            try (Statement setup = conn.createStatement()) {
                setup.execute("CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100))");
                setup.execute("INSERT INTO users VALUES (1, 'John Doe', 'john@example.com'), (2, 'Jane Smith', 'jane@example.com')");
            }
            
            // Test PreparedStatement SELECT
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                ps.setInt(1, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("  Found user: " + rs.getString("name") + " (" + rs.getString("email") + ")");
                    }
                }
            }
            
            // Test PreparedStatement UPDATE
            try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET email = ? WHERE id = ?")) {
                ps.setString(1, "john.doe@newdomain.com");
                ps.setInt(2, 1);
                int rows = ps.executeUpdate();
                System.out.println("  Updated " + rows + " rows");
            }
            
            // Test PreparedStatement INSERT
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO users (id, name, email) VALUES (?, ?, ?)")) {
                ps.setInt(1, 3);
                ps.setString(2, "Bob Wilson");
                ps.setString(3, "bob@example.com");
                int rows = ps.executeUpdate();
                System.out.println("  Inserted " + rows + " rows");
            }
        }
    }
    
    private static void testStatement() throws Exception {
        String url = "jdbc:h2:mem:testdb2;DB_CLOSE_DELAY=-1";
        
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (Statement stmt = conn.createStatement()) {
                // Create table using Statement
                stmt.execute("CREATE TABLE products (id INT PRIMARY KEY, name VARCHAR(100), price DECIMAL(10,2))");
                
                // Test Statement INSERT
                int rows = stmt.executeUpdate("INSERT INTO products VALUES (1, 'Laptop', 999.99), (2, 'Mouse', 29.99)");
                System.out.println("  Inserted " + rows + " rows using Statement");
                
                // Test Statement SELECT
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as total FROM products")) {
                    while (rs.next()) {
                        System.out.println("  Total products: " + rs.getInt("total"));
                    }
                }
                
                // Test complex query
                try (ResultSet rs = stmt.executeQuery("SELECT name, price FROM products WHERE price > 50 ORDER BY price DESC")) {
                    while (rs.next()) {
                        System.out.println("  Expensive product: " + rs.getString("name") + " - $" + rs.getBigDecimal("price"));
                    }
                }
            }
        }
    }
    
    private static void testExceptionHandling() throws Exception {
        String url = "jdbc:h2:mem:testdb3;DB_CLOSE_DELAY=-1";
        
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement stmt = conn.createStatement()) {
            
            // Test syntax error (should be captured by instrumentation)
            try {
                stmt.executeQuery("SELECT * FROM non_existent_table");
            } catch (SQLException e) {
                System.out.println("  Caught expected SQL exception: " + e.getMessage());
            }
            
            // Test PreparedStatement with invalid query
            try (PreparedStatement ps = conn.prepareStatement("SELECT invalid_column FROM another_non_existent_table WHERE id = ?")) {
                ps.setInt(1, 1);
                ps.executeQuery();
            } catch (SQLException e) {
                System.out.println("  Caught expected PreparedStatement exception: " + e.getMessage());
            }
        }
    }
}