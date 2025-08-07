package agent;

import agent.models.AgentMetrics;
import agent.util.JsonUtil;

/**
 * Responsável por enviar os dados coletados para a API REST.
 * (Implementação do envio HTTP será feita posteriormente)
 */
public class RestClient {

    private final ConfigLoader config;

    public RestClient(ConfigLoader config) {
        this.config = config;
    }

    /**
     * Envia as métricas para o endpoint configurado.
     * @param metrics O objeto contendo todas as métricas coletadas.
     */
    public void send(AgentMetrics metrics) {
        if (!config.isRestSendEnabled()) {
            return; // Envio desabilitado
        }

        String jsonPayload = JsonUtil.toJson(metrics);

        // --- LÓGICA DE ENVIO HTTP VIRÁ AQUI ---
        // Por enquanto, vamos apenas imprimir no console para validação.
        System.out.println("-----> Enviando métricas para: " + config.getRestEndpointUrl());
        System.out.println(jsonPayload);
        System.out.println("-----> (Simulação de envio concluída)");
    }
}