package agent;

import agent.models.AgentMetrics;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fila thread-safe para gerenciamento de dados a serem enviados.
 * Controla priorização entre dados novos e dados de backup.
 */
public class DataQueue {
    
    /**
     * Wrapper para AgentMetrics com prioridade e metadata.
     */
    public static class QueueItem implements Comparable<QueueItem> {
        public final AgentMetrics metrics;
        public final long timestamp;
        public final Priority priority;
        public final String source;
        
        public enum Priority {
            HIGH(1),    // Dados de backup/retry
            NORMAL(2),  // Dados novos normais
            LOW(3);     // Dados antigos ou de baixa prioridade
            
            public final int value;
            Priority(int value) { this.value = value; }
        }
        
        public QueueItem(AgentMetrics metrics, Priority priority, String source) {
            this.metrics = metrics;
            this.timestamp = System.currentTimeMillis();
            this.priority = priority;
            this.source = source;
        }
        
        @Override
        public int compareTo(QueueItem other) {
            // Primeiro por prioridade, depois por timestamp (mais antigo primeiro)
            int priorityCompare = Integer.compare(this.priority.value, other.priority.value);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Long.compare(this.timestamp, other.timestamp);
        }
    }
    
    private final ConfigLoader config;
    private final PriorityBlockingQueue<QueueItem> queue;
    private final AtomicInteger currentSize = new AtomicInteger(0);
    private final AtomicLong totalEnqueued = new AtomicLong(0);
    private final AtomicLong totalDequeued = new AtomicLong(0);
    private final AtomicLong totalDropped = new AtomicLong(0);
    
    public DataQueue(ConfigLoader config) {
        this.config = config;
        // Usa PriorityBlockingQueue para ordenação automática por prioridade
        this.queue = new PriorityBlockingQueue<>(config.getDataQueueMaxSize());
    }
    
    /**
     * Adiciona dados novos à fila com prioridade normal.
     * @param metrics Dados a serem enfileirados
     * @return true se adicionado com sucesso
     */
    public boolean enqueue(AgentMetrics metrics) {
        return enqueue(metrics, QueueItem.Priority.NORMAL, "new");
    }
    
    /**
     * Adiciona dados de backup à fila com prioridade alta.
     * @param metrics Dados de backup a serem reenviados
     * @return true se adicionado com sucesso
     */
    public boolean enqueueBackup(AgentMetrics metrics) {
        return enqueue(metrics, QueueItem.Priority.HIGH, "backup");
    }
    
    /**
     * Adiciona item à fila com prioridade especificada.
     * @param metrics Dados a serem enfileirados
     * @param priority Prioridade do item
     * @param source Fonte dos dados para logging
     * @return true se adicionado com sucesso
     */
    public boolean enqueue(AgentMetrics metrics, QueueItem.Priority priority, String source) {
        if (metrics == null) {
            return false;
        }
        
        // Verifica limite da fila
        if (currentSize.get() >= config.getDataQueueMaxSize()) {
            totalDropped.incrementAndGet();
            System.out.println("AVISO: Fila cheia, descartando dados " + source + ". Tamanho: " + currentSize.get());
            return false;
        }
        
        QueueItem item = new QueueItem(metrics, priority, source);
        boolean added = queue.offer(item);
        
        if (added) {
            currentSize.incrementAndGet();
            totalEnqueued.incrementAndGet();
            
            if (priority == QueueItem.Priority.HIGH) {
                System.out.println("Dados de backup adicionados à fila. Tamanho: " + currentSize.get());
            }
        } else {
            totalDropped.incrementAndGet();
            System.out.println("ERRO: Falha ao adicionar dados à fila " + source);
        }
        
        return added;
    }
    
    /**
     * Remove o próximo item da fila (baseado em prioridade).
     * @return QueueItem ou null se fila vazia
     */
    public QueueItem dequeue() {
        QueueItem item = queue.poll();
        if (item != null) {
            currentSize.decrementAndGet();
            totalDequeued.incrementAndGet();
        }
        return item;
    }
    
    /**
     * Remove o próximo item da fila, aguardando se necessário.
     * @param timeoutMs Timeout em milissegundos para aguardar
     * @return QueueItem ou null se timeout
     */
    public QueueItem dequeueWithTimeout(long timeoutMs) {
        try {
            QueueItem item = queue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (item != null) {
                currentSize.decrementAndGet();
                totalDequeued.incrementAndGet();
            }
            return item;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
    
    /**
     * Verifica se a fila está vazia.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
    /**
     * Retorna o tamanho atual da fila.
     */
    public int size() {
        return currentSize.get();
    }
    
    /**
     * Verifica se a fila está próxima da capacidade máxima.
     * @param threshold Percentual do limite (0.0 a 1.0)
     * @return true se acima do threshold
     */
    public boolean isNearCapacity(double threshold) {
        return (double) currentSize.get() / config.getDataQueueMaxSize() >= threshold;
    }
    
    /**
     * Limpa completamente a fila.
     * @return Número de itens removidos
     */
    public int clear() {
        int cleared = currentSize.getAndSet(0);
        queue.clear();
        System.out.println("Fila limpa. " + cleared + " itens removidos.");
        return cleared;
    }
    
    /**
     * Retorna estatísticas da fila.
     */
    public String getQueueStats() {
        return String.format(
            "Fila: %d/%d itens, Enfileirados: %d, Desenfileirados: %d, Descartados: %d, " +
            "Capacidade: %.1f%%",
            currentSize.get(),
            config.getDataQueueMaxSize(),
            totalEnqueued.get(),
            totalDequeued.get(),
            totalDropped.get(),
            (100.0 * currentSize.get() / config.getDataQueueMaxSize())
        );
    }
    
    /**
     * Verifica se deve fazer overflow para storage local.
     * @return true se fila está cheia ou próxima do limite
     */
    public boolean shouldOverflowToStorage() {
        return isNearCapacity(0.8); // 80% da capacidade
    }
}