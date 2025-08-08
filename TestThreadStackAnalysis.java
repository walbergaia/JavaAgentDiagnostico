import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public class TestThreadStackAnalysis {
    
    private static final Object lock1 = new Object();
    private static final Object lock2 = new Object();
    private static final ReentrantLock reentrantLock = new ReentrantLock();
    private static volatile boolean running = true;
    private static ExecutorService executor = Executors.newFixedThreadPool(10);
    
    public static void main(String[] args) {
        System.out.println("=== Teste Thread Stack Analysis ===");
        
        try {
            // Cria threads em diferentes estados para testar captura
            System.out.println("🧵 Criando threads em diferentes estados...");
            
            // 1. Threads RUNNABLE (fazendo CPU work)
            createRunnableThreads();
            
            // 2. Threads BLOCKED (esperando locks)
            createBlockedThreads();
            
            // 3. Threads WAITING (wait/notify)
            createWaitingThreads();
            
            // 4. Threads TIMED_WAITING (sleep)
            createTimedWaitingThreads();
            
            System.out.println("Aplicação rodando por 30 segundos...");
            System.out.println("Coletando análise de thread stacks...");
            
            Thread.sleep(30000);
            
        } catch (Exception e) {
            System.err.println("Erro na aplicação principal: " + e.getMessage());
        } finally {
            running = false;
            executor.shutdownNow();
        }
        
        System.out.println("=== Teste Finalizado ===");
    }
    
    private static void createRunnableThreads() {
        System.out.println("  📈 Criando threads RUNNABLE...");
        
        // 3 threads fazendo trabalho CPU-intensivo
        for (int i = 0; i < 3; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                Thread.currentThread().setName("CPU-Worker-" + threadNum);
                while (running) {
                    // Simula trabalho CPU-intensivo
                    for (int j = 0; j < 100000; j++) {
                        Math.sqrt(j * Math.PI);
                    }
                    try {
                        Thread.sleep(100); // Pequena pausa
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
        }
    }
    
    private static void createBlockedThreads() {
        System.out.println("  🔒 Criando threads BLOCKED...");
        
        // Thread que segura o lock
        executor.submit(() -> {
            Thread.currentThread().setName("Lock-Holder");
            synchronized (lock1) {
                while (running) {
                    try {
                        Thread.sleep(5000); // Segura lock por muito tempo
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        });
        
        // Threads que ficam bloqueadas tentando adquirir o lock
        for (int i = 0; i < 2; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                Thread.currentThread().setName("Blocked-Thread-" + threadNum);
                try {
                    Thread.sleep(1000); // Aguarda lock-holder começar
                    synchronized (lock1) {
                        System.out.println("Thread " + threadNum + " conseguiu o lock");
                    }
                } catch (InterruptedException e) {
                    // OK
                }
            });
        }
    }
    
    private static void createWaitingThreads() {
        System.out.println("  ⏸️ Criando threads WAITING...");
        
        // Thread que vai fazer wait()
        executor.submit(() -> {
            Thread.currentThread().setName("Waiting-Thread");
            synchronized (lock2) {
                try {
                    while (running) {
                        lock2.wait(); // Fica em WAITING
                    }
                } catch (InterruptedException e) {
                    // OK
                }
            }
        });
        
        // Thread usando ReentrantLock condition
        executor.submit(() -> {
            Thread.currentThread().setName("Condition-Waiting-Thread");
            reentrantLock.lock();
            try {
                Condition condition = reentrantLock.newCondition();
                while (running) {
                    try {
                        condition.await(); // Fica em WAITING
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            } finally {
                reentrantLock.unlock();
            }
        });
    }
    
    private static void createTimedWaitingThreads() {
        System.out.println("  ⏱️ Criando threads TIMED_WAITING...");
        
        // Threads fazendo sleep
        for (int i = 0; i < 2; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                Thread.currentThread().setName("Sleeping-Thread-" + threadNum);
                while (running) {
                    try {
                        Thread.sleep(3000); // TIMED_WAITING
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
        }
        
        // Thread usando wait com timeout
        executor.submit(() -> {
            Thread.currentThread().setName("Timed-Wait-Thread");
            synchronized (lock2) {
                try {
                    while (running) {
                        lock2.wait(2000); // TIMED_WAITING
                    }
                } catch (InterruptedException e) {
                    // OK
                }
            }
        });
    }
}