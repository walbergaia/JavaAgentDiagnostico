public class SimpleThreadTest {
    public static void main(String[] args) {
        System.out.println("=== Simple Thread Test ===");
        
        // Create a simple blocked thread scenario
        Object lock = new Object();
        
        // Thread that holds the lock
        Thread holder = new Thread(() -> {
            Thread.currentThread().setName("Lock-Holder");
            synchronized (lock) {
                try {
                    Thread.sleep(15000); // Hold lock for 15 seconds
                } catch (InterruptedException e) {
                    System.out.println("Holder interrupted");
                }
            }
        });
        
        // Thread that tries to acquire the lock (will be BLOCKED)
        Thread waiter = new Thread(() -> {
            Thread.currentThread().setName("Lock-Waiter");
            try {
                Thread.sleep(1000); // Let holder start first
                synchronized (lock) {
                    System.out.println("Waiter got the lock!");
                }
            } catch (InterruptedException e) {
                System.out.println("Waiter interrupted");
            }
        });
        
        // Thread that sleeps (TIMED_WAITING)
        Thread sleeper = new Thread(() -> {
            Thread.currentThread().setName("Sleeper");
            try {
                Thread.sleep(15000);
                System.out.println("Sleeper woke up!");
            } catch (InterruptedException e) {
                System.out.println("Sleeper interrupted");
            }
        });
        
        holder.start();
        waiter.start();
        sleeper.start();
        
        try {
            System.out.println("Running for 20 seconds to collect thread stacks...");
            Thread.sleep(20000);
            
            holder.interrupt();
            waiter.interrupt();
            sleeper.interrupt();
            
            holder.join(1000);
            waiter.join(1000);
            sleeper.join(1000);
            
        } catch (InterruptedException e) {
            System.out.println("Main interrupted");
        }
        
        System.out.println("=== Test Finished ===");
    }
}