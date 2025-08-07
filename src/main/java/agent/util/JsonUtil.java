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

    /**
     * Converte uma string JSON para um objeto do tipo especificado.
     * @param json A string JSON.
     * @param classOfT A classe do tipo de retorno.
     * @param <T> O tipo do objeto retornado.
     * @return O objeto deserializado.
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        return gson.fromJson(json, classOfT);
    }
}