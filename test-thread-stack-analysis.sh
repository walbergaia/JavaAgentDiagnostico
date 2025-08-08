#!/bin/bash

echo "=========================================="
echo "Teste: Thread Stack Trace Analysis"
echo "=========================================="

AGENT_JAR="build/libs/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar"

if [ ! -f "$AGENT_JAR" ]; then
    echo "ERRO: JAR do agente não encontrado em $AGENT_JAR"
    echo "Execute './build.sh' ou './gradlew build' primeiro"
    exit 1
fi

# Configuração de teste com thread stack analysis habilitado
echo "Criando configuração de teste..."
cat > test-thread-stack.properties << 'EOF'
# Configuração para testar análise de thread stacks
enabled=true
agent.name=test-thread-stack-agent
application.name=TestThreadStackApp
sampling.interval.ms=8000

# REST desabilitado, storage local habilitado
send.rest.enabled=false
local.storage.enabled=true
local.storage.path=./test-thread-stack-storage

# THREAD STACK ANALYSIS HABILITADO
enable.thread.stack.analysis=true
thread.stack.max.depth=15
thread.stack.sample.size=5

# Outros módulos básicos
enable.exception.capture=false
enable.sql.capture=false
enable.gc.metrics=true
enable.system.cpu.mem=true

# Servidor config desabilitado
config.server.enabled=false
EOF

# Limpa storage anterior
rm -rf ./test-thread-stack-storage
mkdir -p ./test-thread-stack-storage

# Cria aplicação de teste que gera threads em diferentes estados
echo "Criando aplicação de teste..."
cat > TestThreadStackAnalysis.java << 'EOF'
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

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
EOF

# Compila aplicação de teste
echo "Compilando aplicação de teste..."
javac TestThreadStackAnalysis.java

# Copia configuração para classpath
cp test-thread-stack.properties src/main/resources/agent.properties

# Reconstroi com nova configuração
echo "Reconstruindo agente com configuração de teste..."
./gradlew shadowJar -q

# Executa teste
echo ""
echo "=========================================="
echo "EXECUTANDO TESTE"
echo "=========================================="

java -javaagent:$AGENT_JAR -cp . TestThreadStackAnalysis 2>&1 | tee test-thread-stack-output.log

echo ""
echo "=========================================="
echo "VERIFICANDO RESULTADOS"
echo "=========================================="

# Verifica se dados foram gerados
if [ -d "./test-thread-stack-storage" ]; then
    FILE_COUNT=$(ls -1 ./test-thread-stack-storage/metrics_*.json 2>/dev/null | wc -l)
    echo "📁 Arquivos de métricas gerados: $FILE_COUNT"
    
    if [ $FILE_COUNT -gt 0 ]; then
        LATEST_FILE=$(ls -t ./test-thread-stack-storage/metrics_*.json | head -n 1)
        echo "📄 Analisando arquivo mais recente: $(basename $LATEST_FILE)"
        
        # Verifica se contém dados de thread
        echo ""
        echo "🧵 Análise das threads capturadas:"
        
        # Thread totals
        TOTAL_THREADS=$(jq -r '.threads.total' "$LATEST_FILE" 2>/dev/null || echo "0")
        RUNNABLE_COUNT=$(jq -r '.threads.runnable' "$LATEST_FILE" 2>/dev/null || echo "0")
        BLOCKED_COUNT=$(jq -r '.threads.blocked' "$LATEST_FILE" 2>/dev/null || echo "0")
        WAITING_COUNT=$(jq -r '.threads.waiting' "$LATEST_FILE" 2>/dev/null || echo "0")
        TIMED_WAITING_COUNT=$(jq -r '.threads.timed_waiting' "$LATEST_FILE" 2>/dev/null || echo "0")
        
        echo "  Total de threads: $TOTAL_THREADS"
        echo "  RUNNABLE: $RUNNABLE_COUNT"
        echo "  BLOCKED: $BLOCKED_COUNT"
        echo "  WAITING: $WAITING_COUNT"
        echo "  TIMED_WAITING: $TIMED_WAITING_COUNT"
        
        # Verifica detalhes de stack traces
        echo ""
        echo "🔍 Detalhes de Stack Traces capturados:"
        
        RUNNABLE_DETAILS=$(jq -r '.threads.runnableDetails | length' "$LATEST_FILE" 2>/dev/null || echo "0")
        BLOCKED_DETAILS=$(jq -r '.threads.blockedDetails | length' "$LATEST_FILE" 2>/dev/null || echo "0")
        WAITING_DETAILS=$(jq -r '.threads.waitingDetails | length' "$LATEST_FILE" 2>/dev/null || echo "0")
        TIMED_WAITING_DETAILS=$(jq -r '.threads.timedWaitingDetails | length' "$LATEST_FILE" 2>/dev/null || echo "0")
        
        echo "  RUNNABLE stack traces: $RUNNABLE_DETAILS"
        echo "  BLOCKED stack traces: $BLOCKED_DETAILS"  
        echo "  WAITING stack traces: $WAITING_DETAILS"
        echo "  TIMED_WAITING stack traces: $TIMED_WAITING_DETAILS"
        
        # Mostra exemplos de threads capturadas
        if [ "$RUNNABLE_DETAILS" != "null" ] && [ "$RUNNABLE_DETAILS" -gt 0 ]; then
            echo ""
            echo "✅ SUCESSO: Stack traces RUNNABLE capturados!"
            echo "📋 Exemplo de thread RUNNABLE:"
            jq -r '.threads.runnableDetails[0] | "  Nome: \(.threadName)\n  Estado: \(.threadState)\n  Stack frames: \(.stackTrace | length)"' "$LATEST_FILE" 2>/dev/null || echo "  (erro ao extrair exemplo)"
        fi
        
        if [ "$BLOCKED_DETAILS" != "null" ] && [ "$BLOCKED_DETAILS" -gt 0 ]; then
            echo ""
            echo "✅ SUCESSO: Stack traces BLOCKED capturados!"
            echo "📋 Exemplo de thread BLOCKED:"
            jq -r '.threads.blockedDetails[0] | "  Nome: \(.threadName)\n  Estado: \(.threadState)\n  Lock: \(.lockName // "N/A")\n  Stack frames: \(.stackTrace | length)"' "$LATEST_FILE" 2>/dev/null || echo "  (erro ao extrair exemplo)"
        fi
        
        if [ "$WAITING_DETAILS" != "null" ] && [ "$WAITING_DETAILS" -gt 0 ]; then
            echo ""
            echo "✅ SUCESSO: Stack traces WAITING capturados!"
            echo "📋 Exemplo de thread WAITING:"
            jq -r '.threads.waitingDetails[0] | "  Nome: \(.threadName)\n  Estado: \(.threadState)\n  Stack frames: \(.stackTrace | length)"' "$LATEST_FILE" 2>/dev/null || echo "  (erro ao extrair exemplo)"
        fi
        
        if [ "$TIMED_WAITING_DETAILS" != "null" ] && [ "$TIMED_WAITING_DETAILS" -gt 0 ]; then
            echo ""
            echo "✅ SUCESSO: Stack traces TIMED_WAITING capturados!"
            echo "📋 Exemplo de thread TIMED_WAITING:"
            jq -r '.threads.timedWaitingDetails[0] | "  Nome: \(.threadName)\n  Estado: \(.threadState)\n  Stack frames: \(.stackTrace | length)"' "$LATEST_FILE" 2>/dev/null || echo "  (erro ao extrair exemplo)"
        fi
        
        # Mostra amostra de stack trace
        if [ "$RUNNABLE_DETAILS" != "null" ] && [ "$RUNNABLE_DETAILS" -gt 0 ]; then
            echo ""
            echo "📄 Amostra de stack trace (primeiros 3 frames):"
            jq -r '.threads.runnableDetails[0].stackTrace[0:3][] | "  " + .' "$LATEST_FILE" 2>/dev/null || echo "  (erro ao extrair stack trace)"
        fi
        
    else
        echo "❌ FALHA: Nenhum arquivo de dados foi gerado"
    fi
else
    echo "❌ FALHA: Diretório de storage não foi criado"
fi

# Verifica logs
echo ""
echo "📋 Verificando logs..."

if grep -i "thread stack analysis" test-thread-stack-output.log > /dev/null; then
    echo "✅ Thread stack analysis foi habilitado"
else
    echo "ℹ️ Não encontrou confirmação de habilitação nos logs"
fi

echo ""
echo "=========================================="
echo "RESUMO FINAL"
echo "=========================================="

SUCCESS=true

if [ "$FILE_COUNT" -gt 0 ]; then
    echo "✅ Coleta de métricas: FUNCIONANDO"
else
    echo "❌ Coleta de métricas: FALHA"
    SUCCESS=false
fi

TOTAL_STACK_DETAILS=$(($RUNNABLE_DETAILS + $BLOCKED_DETAILS + $WAITING_DETAILS + $TIMED_WAITING_DETAILS))

if [ "$TOTAL_STACK_DETAILS" -gt 0 ]; then
    echo "✅ Thread Stack Analysis: FUNCIONANDO ($TOTAL_STACK_DETAILS stack traces coletados)"
    echo "  🧵 Thread states detectados:"
    [ "$RUNNABLE_DETAILS" -gt 0 ] && echo "    • RUNNABLE: $RUNNABLE_DETAILS threads"
    [ "$BLOCKED_DETAILS" -gt 0 ] && echo "    • BLOCKED: $BLOCKED_DETAILS threads" 
    [ "$WAITING_DETAILS" -gt 0 ] && echo "    • WAITING: $WAITING_DETAILS threads"
    [ "$TIMED_WAITING_DETAILS" -gt 0 ] && echo "    • TIMED_WAITING: $TIMED_WAITING_DETAILS threads"
else
    echo "⚠️ Thread Stack Analysis: NÃO COLETOU stack traces detalhados"
    echo "  Possíveis causas:"
    echo "  • Feature desabilitada"
    echo "  • Erro na coleta" 
    echo "  • Threads não permaneceram tempo suficiente nos estados esperados"
    SUCCESS=false
fi

if $SUCCESS; then
    echo ""
    echo "🎉 RESULTADO GERAL: SUCESSO!"
    echo "A análise de thread stack traces está funcionando corretamente!"
    echo ""
    echo "📊 Configurações utilizadas:"
    echo "  • enable.thread.stack.analysis=true"
    echo "  • thread.stack.max.depth=15"
    echo "  • thread.stack.sample.size=5"
else
    echo ""
    echo "⚠️ RESULTADO GERAL: FALHA OU INCOMPLETO"
    echo "A funcionalidade pode precisar de ajustes."
fi

# Limpeza
echo ""
echo "Limpando arquivos temporários..."
rm -f TestThreadStackAnalysis*.class TestThreadStackAnalysis.java test-thread-stack.properties test-thread-stack-output.log
# Mantém storage para inspeção manual se desejado

# Restaura configuração original
git checkout src/main/resources/agent.properties 2>/dev/null || echo "Configuração original não restaurada"

echo "✅ Teste concluído!"