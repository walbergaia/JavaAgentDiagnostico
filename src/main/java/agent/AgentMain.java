package agent;

import agent.config.ConfigurationServer;

import java.lang.instrument.Instrumentation;

public class AgentMain {

    private static ConfigurationServer configServer;

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
            System.out.println("Servidor de configuração dinâmica está desabilitado.");
        }

        // Próximos passos:
        // 1. Iniciar o agendador de métricas (MetricsScheduler)
        // 2. Aplicar instrumentação SQL (SqlInstrumentation)
        // 3. Configurar o handler de exceções (ExceptionHandler)

        System.out.println("Agente inicializado com sucesso.");
    }

    /**
     * Retorna a instância do servidor de configuração (para testes).
     * @return ConfigurationServer ou null se não estiver inicializado
     */
    public static ConfigurationServer getConfigServer() {
        return configServer;
    }
}