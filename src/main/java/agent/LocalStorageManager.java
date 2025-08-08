package agent;

import agent.models.AgentMetrics;
import agent.util.JsonUtil;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia o armazenamento local de dados quando o servidor estiver indisponível.
 * Armazena dados em arquivos JSON na pasta temporária configurada.
 */
public class LocalStorageManager {
    
    private final ConfigLoader config;
    private final Path storagePath;
    private final DateTimeFormatter fileNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");
    private final Map<String, Long> fileCreationTimes = new ConcurrentHashMap<>();
    
    public LocalStorageManager(ConfigLoader config) {
        this.config = config;
        String configPath = config.getLocalStoragePath();
        
        // Se o path está vazio ou é null, usa o diretório temporário
        if (configPath == null || configPath.trim().isEmpty()) {
            configPath = System.getProperty("java.io.tmpdir") + "/javaagent-diagnostico";
        }
        
        this.storagePath = Paths.get(configPath);
        
        try {
            Files.createDirectories(storagePath);
            System.out.println("LocalStorageManager inicializado. Pasta: " + storagePath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("ERRO: Falha ao criar diretório de armazenamento: " + storagePath);
            e.printStackTrace();
        }
    }
    
    /**
     * Armazena dados localmente quando o envio HTTP falhar.
     * @param metrics Dados a serem armazenados
     * @return true se armazenado com sucesso, false caso contrário
     */
    public boolean store(AgentMetrics metrics) {
        if (!config.isLocalStorageEnabled()) {
            System.out.println("AVISO: Armazenamento local está desabilitado. Dados não foram salvos localmente.");
            return false;
        }
        
        try {
            String timestamp = LocalDateTime.now().format(fileNameFormatter);
            String fileName = "metrics_" + timestamp + ".json";
            Path filePath = storagePath.resolve(fileName);
            
            String jsonData = JsonUtil.toJson(metrics);
            Files.write(filePath, jsonData.getBytes("UTF-8"));
            
            fileCreationTimes.put(fileName, System.currentTimeMillis());
            
            System.out.println("Dados armazenados localmente: " + fileName);
            
            // Limpeza automática após armazenar
            cleanupOldFiles();
            
            return true;
        } catch (IOException e) {
            System.err.println("ERRO: Falha ao armazenar dados localmente: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Retorna lista de arquivos pendentes ordenados por data de criação.
     * @return Lista de caminhos para arquivos pendentes
     */
    public List<Path> getPendingFiles() {
        List<Path> pendingFiles = new ArrayList<>();
        
        try {
            if (!Files.exists(storagePath)) {
                return pendingFiles;
            }
            
            Files.list(storagePath)
                .filter(path -> path.toString().endsWith(".json"))
                .filter(path -> path.getFileName().toString().startsWith("metrics_"))
                .sorted((p1, p2) -> {
                    try {
                        long time1 = Files.getLastModifiedTime(p1).toMillis();
                        long time2 = Files.getLastModifiedTime(p2).toMillis();
                        return Long.compare(time1, time2);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .forEach(pendingFiles::add);
                
        } catch (IOException e) {
            System.err.println("ERRO: Falha ao listar arquivos pendentes: " + e.getMessage());
        }
        
        return pendingFiles;
    }
    
    /**
     * Carrega dados de um arquivo específico.
     * @param filePath Caminho para o arquivo
     * @return AgentMetrics desserializado ou null se falhar
     */
    public AgentMetrics loadFromFile(Path filePath) {
        try {
            byte[] data = Files.readAllBytes(filePath);
            String jsonData = new String(data, "UTF-8");
            return JsonUtil.fromJson(jsonData, AgentMetrics.class);
        } catch (IOException e) {
            System.err.println("ERRO: Falha ao carregar arquivo: " + filePath + " - " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Remove um arquivo após envio bem-sucedido.
     * @param filePath Caminho para o arquivo a ser removido
     * @return true se removido com sucesso
     */
    public boolean removeFile(Path filePath) {
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                fileCreationTimes.remove(filePath.getFileName().toString());
                System.out.println("Arquivo removido após envio: " + filePath.getFileName());
            }
            return deleted;
        } catch (IOException e) {
            System.err.println("ERRO: Falha ao remover arquivo: " + filePath + " - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Limpeza automática de arquivos antigos baseada em configuração.
     */
    public void cleanupOldFiles() {
        if (!config.isLocalStorageEnabled()) {
            return;
        }
        
        try {
            List<Path> allFiles = getPendingFiles();
            long maxFiles = config.getLocalStorageMaxFiles();
            long cleanupHours = config.getLocalStorageCleanupHours();
            long cutoffTime = System.currentTimeMillis() - (cleanupHours * 60 * 60 * 1000);
            
            int removed = 0;
            
            // Remove arquivos por idade
            for (Path file : allFiles) {
                try {
                    long lastModified = Files.getLastModifiedTime(file).toMillis();
                    if (lastModified < cutoffTime) {
                        if (removeFile(file)) {
                            removed++;
                        }
                    }
                } catch (IOException e) {
                    // Ignora erros individuais na limpeza
                }
            }
            
            // Remove arquivos excedentes (mantém apenas os mais recentes)
            allFiles = getPendingFiles(); // Recarrega após limpeza por idade
            if (allFiles.size() > maxFiles) {
                // Remove os mais antigos
                for (int i = 0; i < allFiles.size() - maxFiles; i++) {
                    if (removeFile(allFiles.get(i))) {
                        removed++;
                    }
                }
            }
            
            if (removed > 0) {
                System.out.println("Limpeza concluída: " + removed + " arquivos removidos");
            }
            
        } catch (Exception e) {
            System.err.println("ERRO: Falha na limpeza automática: " + e.getMessage());
        }
    }
    
    /**
     * Retorna estatísticas do armazenamento local.
     */
    public String getStorageStats() {
        try {
            List<Path> files = getPendingFiles();
            long totalSize = 0;
            
            for (Path file : files) {
                try {
                    totalSize += Files.size(file);
                } catch (IOException e) {
                    // Ignora arquivos com problema
                }
            }
            
            return String.format("Arquivos pendentes: %d, Tamanho total: %.2f KB, Pasta: %s", 
                files.size(), totalSize / 1024.0, storagePath.toAbsolutePath());
        } catch (Exception e) {
            return "Erro ao obter estatísticas: " + e.getMessage();
        }
    }
}