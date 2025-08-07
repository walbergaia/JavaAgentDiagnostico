package test;

/**
 * Simple test application to demonstrate the JavaAgentDiagnostico with dynamic configuration server.
 */
public class TestApp {
    
    public static void main(String[] args) {
        System.out.println("=== TestApp Started ===");
        System.out.println("This app can be monitored by JavaAgentDiagnostico");
        System.out.println("Try the following to test the configuration server:");
        System.out.println("  curl http://localhost:8090/health");
        System.out.println("  curl http://localhost:8090/config");
        System.out.println("  curl -X POST http://localhost:8090/config -H 'Content-Type: application/json' -d '{\"enabled\": \"false\"}'");
        System.out.println("=== Starting test loop ===");
        
        // Simple test loop
        for (int i = 0; i < 60; i++) {
            try {
                Thread.sleep(5000); // Sleep for 5 seconds
                System.out.println("TestApp iteration " + (i + 1) + " - Configuration server should be available at http://localhost:8090");
                
                // Simulate some work
                doSomeWork();
                
            } catch (InterruptedException e) {
                System.out.println("TestApp interrupted");
                break;
            }
        }
        
        System.out.println("=== TestApp Finished ===");
    }
    
    private static void doSomeWork() {
        // Simulate some CPU work
        long sum = 0;
        for (int i = 0; i < 1000000; i++) {
            sum += i;
        }
        
        // Create some objects for GC activity
        for (int i = 0; i < 100; i++) {
            String data = "Test data " + i + " - " + System.currentTimeMillis();
            data.length(); // Use the string to prevent optimization
        }
    }
}