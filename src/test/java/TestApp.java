public class TestApp {
    public static void main(String[] args) {
        System.out.println("Aplicação de teste iniciada com o agente JavaAgentDiagnostico");
        
        // Simular alguma atividade
        try {
            Thread.sleep(2000);
            System.out.println("Aplicação executando...");
            Thread.sleep(2000);
            System.out.println("Aplicação finalizando...");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
