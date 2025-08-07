package agent.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Utilitário para conversão de objetos para JSON.
 */
public class JsonUtil {
    // Instância do Gson para ser reutilizada.
    // Pretty printing é útil para logs, mas pode ser desativado para o envio final.
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Converte um objeto para sua representação em string JSON.
     * @param object O objeto a ser convertido.
     * @return A string JSON.
     */
    public static String toJson(Object object) {
        return gson.toJson(object);
    }
}