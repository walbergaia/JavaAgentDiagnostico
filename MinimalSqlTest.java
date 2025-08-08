import java.sql.*;

public class MinimalSqlTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting minimal SQL test...");
        
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");
        
        // Test Statement
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE test (id INT)");
        System.out.println("Table created");
        
        // Test PreparedStatement  
        PreparedStatement ps = conn.prepareStatement("INSERT INTO test VALUES (?)");
        ps.setInt(1, 1);
        ps.executeUpdate();
        System.out.println("Data inserted");
        
        // Test query
        ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM test");
        if (rs.next()) {
            System.out.println("Count: " + rs.getInt(1));
        }
        
        conn.close();
        System.out.println("Test completed");
        
        // Give time for agent to process
        Thread.sleep(1000);
    }
}