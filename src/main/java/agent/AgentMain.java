package agent;

import agent.config.ConfigurationServer;
import agent.tracing.TracingManager;
import agent.instrumentation.HttpServletInstrumentation;
import agent.instrumentation.HttpClientInstrumentation;
import agent.profiling.CpuSamplingProfiler;
import agent.instrumentation.FileIoInstrumentation;
import agent.instrumentation.SocketIoInstrumentation;

import java.lang.instrument.Instrumentation;

public class AgentMain {

    private static ConfigurationServer configServer;
    private static ExceptionHandler exceptionHandler;
    private static MetricsScheduler metricsScheduler;
    private static CpuSamplingProfiler cpuProfiler;

    /**
     * Ponto de entrada do agente Java, chamado antes do método main da aplicação.
     * @param agentArgs Argumentos passados para o agente na linha de comando.
     * @param inst Objeto de instrumentação fornecido pela JVM.
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("============================================================");
        System.out.println("  Iniciando JavaAgentDiagnóstico...");
        System.out.println("============================================================");

        // Carrega a configuração do arquivo agent.properties
        ConfigLoader config = ConfigLoader.getInstance();
    config.logSummary();
    config.logConfigSources();

        // Verifica se o agente está habilitado antes de prosseguir
        if (!config.isAgentEnabled()) {
            System.out.println("JavaAgentDiagnóstico está desabilitado via configuração. Encerrando.");
            return;
        }

        System.out.println("Agente '" + config.getAgentName() + "' monitorando a aplicação '" + config.getApplicationName() + "'");

        // Inicializa o servidor de configuração dinâmica se habilitado
    if (config.isConfigServerEnabled()) {
            try {
                configServer = new ConfigurationServer(config);
                configServer.start();
                
                // Adiciona shutdown hook para parar o servidor graciosamente
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (configServer != null) {
                        configServer.stop();
                    }
                }));
                
            } catch (Exception e) {
                System.err.println("ERRO: Falha ao inicializar servidor de configuração: " + e.getMessage());
                System.err.println("Continuando sem servidor de configuração dinâmica...");
            }
        } else {
            System.out.println("Servidor de configuração dinâmica desabilitado (config.server.enabled=false)");
        }

        // Inicializar componentes principais do agente
        try {
            // 1. Inicializar coletor de métricas
            MetricsCollector collector = new MetricsCollector(config);
            
            // 2. Inicializar cliente REST (com fallback para armazenamento local)
            RestClient restClient = new RestClient(config);
            
            // 3. Inicializar e iniciar o agendador de métricas
            metricsScheduler = new MetricsScheduler(config, collector, restClient);
            metricsScheduler.start();
            
            // 4. Aplicar instrumentação SQL se habilitada
            if (config.isSqlCaptureEnabled()) {
                SqlInstrumentation.setup(inst, config);
            }

            // 4b. Inicializar e aplicar instrumentação de tracing/HTTP
            if (config.isTracingEnabled()) {
                TracingManager.get().init(config);
                if (config.isHttpTracingEnabled()) {
                    HttpServletInstrumentation.setup(inst, config);
                }
                // Instrumentação cliente HTTP
                HttpClientInstrumentation.setup(inst, config);
            }

            // 4c. CPU sampling profiler
            if (config.isCpuProfilingEnabled()) {
                cpuProfiler = new CpuSamplingProfiler(config);
                cpuProfiler.start();
            }

            // 4d. Instrumentação de File/Socket I/O (controlada por enable.io.metrics + sub flags)
            if (config.isIoMetricsEnabled()) {
                if (config.isFileIoEnabled()) {
                    FileIoInstrumentation.setup(inst, config);
                }
                if (config.isSocketIoEnabled()) {
                    SocketIoInstrumentation.setup(inst, config);
                }
            }
            
            // 5. Configurar handler de exceções se habilitado
            if (config.isExceptionCaptureEnabled()) {
                exceptionHandler = new ExceptionHandler(config);
                exceptionHandler.setup();
            }
            
            // Adiciona shutdown hook para parar o agendador graciosamente
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Parando JavaAgentDiagnóstico...");
                if (metricsScheduler != null) {
                    metricsScheduler.stop();
                }
                if (cpuProfiler != null) {
                    cpuProfiler.stop();
                }
                if (configServer != null) {
                    configServer.stop();
                }
            }));
            
            System.out.println("Agente inicializado com sucesso - Todos os componentes ativos.");
            
        } catch (Exception e) {
            System.err.println("ERRO: Falha ao inicializar componentes do agente: " + e.getMessage());
            e.printStackTrace();
            System.err.println("Agente será executado com funcionalidade limitada.");
        }
    }

    /**
     * Retorna a instância do servidor de configuração (para testes).
     * @return ConfigurationServer ou null se não estiver inicializado
     */
    public static ConfigurationServer getConfigServer() {
        return configServer;
    }

    /**
     * Retorna a instância do handler de exceções.
     * @return ExceptionHandler ou null se não estiver inicializado
     */
    public static ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    /**
     * Retorna a instância do agendador de métricas.
     * @return MetricsScheduler ou null se não estiver inicializado
     */
    public static MetricsScheduler getMetricsScheduler() {
        return metricsScheduler;
    }

    public static CpuSamplingProfiler getCpuProfiler() { return cpuProfiler; }
}