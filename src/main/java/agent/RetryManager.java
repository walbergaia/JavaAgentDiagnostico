package agent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gerencia estratégias de retry com backoff exponencial e estado de conectividade.
 */
public class RetryManager {
    
    private final ConfigLoader config;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastSuccessTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong nextRetryTime = new AtomicLong(0);
    private final AtomicBoolean serverAvailable = new AtomicBoolean(true);
    
    public RetryManager(ConfigLoader config) {
        this.config = config;
    }
    
    /**
     * Registra uma tentativa de envio bem-sucedida.
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        lastSuccessTime.set(System.currentTimeMillis());
        nextRetryTime.set(0);
        
        boolean wasUnavailable = !serverAvailable.getAndSet(true);
        if (wasUnavailable) {
            System.out.println("Conectividade com servidor restaurada");
        }
    }
    
    /**
     * Registra uma falha de envio e calcula próximo tempo de retry.
     * @param exception Exceção que causou a falha (opcional)
     */
    public void recordFailure(Exception exception) {
        int failures = consecutiveFailures.incrementAndGet();
        serverAvailable.set(false);
        
        // Calcula backoff exponencial
        long backoffMs = calculateBackoff(failures);
        nextRetryTime.set(System.currentTimeMillis() + backoffMs);
        
        System.out.println(String.format("Falha de envio #%d. Próximo retry em %d ms. Erro: %s", 
            failures, backoffMs, 
            exception != null ? exception.getMessage() : "Desconhecido"));
    }
    
    /**
     * Verifica se é possível tentar um novo envio baseado no backoff.
     * @return true se pode tentar enviar
     */
    public boolean canRetry() {
        if (consecutiveFailures.get() >= config.getRestRetryMaxAttempts()) {
            return false;
        }
        
        return System.currentTimeMillis() >= nextRetryTime.get();
    }
    
    /**
     * Verifica se o servidor está disponível baseado em tentativas recentes.
     * @return true se servidor provavelmente disponível
     */
    public boolean isServerAvailable() {
        return serverAvailable.get();
    }
    
    /**
     * Verifica se deve processar dados pendentes (arquivos locais).
     * Processa quando server volta a estar disponível ou após intervalo mínimo.
     * @return true se deve processar dados pendentes
     */
    public boolean shouldProcessPendingData() {
        // Se servidor está disponível, pode processar
        if (isServerAvailable()) {
            return true;
        }
        
        // Se não há falhas consecutivas excessivas, pode tentar
        if (consecutiveFailures.get() < config.getRestRetryMaxAttempts()) {
            return canRetry();
        }
        
        // Se passou tempo suficiente desde última tentativa, pode tentar novamente
        long timeSinceLastTry = System.currentTimeMillis() - (nextRetryTime.get() - calculateBackoff(consecutiveFailures.get()));
        return timeSinceLastTry > (30 * 60 * 1000); // 30 minutos
    }
    
    /**
     * Força uma nova tentativa resetando parcialmente o estado.
     * Útil para cenários onde conectividade pode ter sido restaurada externamente.
     */
    public void forceRetry() {
        if (consecutiveFailures.get() > 0) {
            consecutiveFailures.set(Math.max(0, consecutiveFailures.get() - 1));
            nextRetryTime.set(0);
            System.out.println("Retry forçado. Falhas consecutivas reduzidas para: " + consecutiveFailures.get());
        }
    }
    
    /**
     * Calcula tempo de backoff baseado no número de falhas.
     * Implementa backoff exponencial com jitter.
     */
    private long calculateBackoff(int failureCount) {
        long initialBackoff = config.getRestRetryBackoffInitialMs();
        
        // Backoff exponencial: initial * 2^(failures-1)
        long backoff = initialBackoff * (1L << Math.min(failureCount - 1, 10)); // Cap em 2^10
        
        // Adiciona jitter de ±25% para evitar thundering herd
        double jitter = 0.75 + (Math.random() * 0.5); // 0.75 a 1.25
        backoff = (long) (backoff * jitter);
        
        // Cap máximo de 10 minutos
        return Math.min(backoff, 10 * 60 * 1000);
    }
    
    /**
     * Retorna estatísticas do retry manager.
     */
    public String getRetryStats() {
        long timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessTime.get();
        long timeUntilNextRetry = Math.max(0, nextRetryTime.get() - System.currentTimeMillis());
        
        return String.format(
            "Servidor disponível: %s, Falhas consecutivas: %d/%d, " +
            "Último sucesso: %d ms atrás, Próximo retry: %d ms",
            isServerAvailable() ? "SIM" : "NÃO",
            consecutiveFailures.get(),
            config.getRestRetryMaxAttempts(),
            timeSinceLastSuccess,
            timeUntilNextRetry
        );
    }
    
    /**
     * Reset completo do estado (útil para testes ou reconfigurações).
     */
    public void reset() {
        consecutiveFailures.set(0);
        lastSuccessTime.set(System.currentTimeMillis());
        nextRetryTime.set(0);
        serverAvailable.set(true);
        System.out.println("RetryManager resetado");
    }
}