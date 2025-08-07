package agent;

import java.lang.instrument.Instrumentation;

public class AgentMain {

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

        // Próximos passos:
        // 1. Iniciar o agendador de métricas (MetricsScheduler)
        // 2. Aplicar instrumentação SQL (SqlInstrumentation)
        // 3. Configurar o handler de exceções (ExceptionHandler)

        System.out.println("Agente inicializado com sucesso.");
    }
}