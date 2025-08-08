package agent.sql;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Normaliza queries SQL para pattern recognition e agrupamento.
 */
public class SqlQueryNormalizer {
    
    // Patterns para normalização de queries
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+\\b");
    private static final Pattern STRING_PATTERN = Pattern.compile("'[^']*'");
    private static final Pattern IN_CLAUSE_PATTERN = Pattern.compile("\\bIN\\s*\\([^)]+\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/|--.*?$", Pattern.MULTILINE | Pattern.DOTALL);
    
    /**
     * Normaliza uma query SQL removendo valores específicos e mantendo apenas a estrutura.
     * 
     * @param sql Query SQL original
     * @return Query normalizada
     */
    public static String normalize(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "EMPTY_QUERY";
        }
        
        String normalized = sql;
        
        // Remove comentários
        normalized = COMMENT_PATTERN.matcher(normalized).replaceAll("");
        
        // Substitui números por placeholder
        normalized = NUMBER_PATTERN.matcher(normalized).replaceAll("?");
        
        // Substitui strings por placeholder
        normalized = STRING_PATTERN.matcher(normalized).replaceAll("'?'");
        
        // Substitui clausulas IN com múltiplos valores
        normalized = IN_CLAUSE_PATTERN.matcher(normalized).replaceAll("IN (?)");
        
        // Normaliza espaços em branco
        normalized = WHITESPACE_PATTERN.matcher(normalized.trim()).replaceAll(" ");
        
        // Converte para uppercase para consistência
        normalized = normalized.toUpperCase();
        
        return normalized;
    }
    
    /**
     * Gera hash MD5 de uma query normalizada para agrupamento eficiente.
     * 
     * @param normalizedQuery Query já normalizada
     * @return Hash MD5 da query
     */
    public static String generateHash(String normalizedQuery) {
        if (normalizedQuery == null) {
            return "null_query";
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(normalizedQuery.getBytes());
            StringBuilder sb = new StringBuilder();
            
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback para hashCode se MD5 não estiver disponível
            return String.valueOf(normalizedQuery.hashCode());
        }
    }
    
    /**
     * Extrai informações úteis da query para categorização.
     * 
     * @param sql Query SQL original
     * @return Insights sobre a query
     */
    public static String extractPerformanceInsight(String sql) {
        if (sql == null) return null;
        
        String upperSql = sql.toUpperCase();
        
        // Detecta possíveis problemas de performance
        if (upperSql.contains("SELECT *")) {
            return "SELECT_ALL_COLUMNS";
        }
        
        if (upperSql.contains("WHERE") && !upperSql.contains("LIMIT") && upperSql.contains("SELECT")) {
            return "POTENTIALLY_UNBOUNDED_SELECT";
        }
        
        if (upperSql.matches(".*SELECT.*FROM.*WHERE.*LIKE\\s+'%.*")) {
            return "LEADING_WILDCARD_LIKE";
        }
        
        if (upperSql.contains("ORDER BY") && !upperSql.contains("LIMIT")) {
            return "UNBOUNDED_ORDER_BY";
        }
        
        if (upperSql.contains("GROUP BY") && !upperSql.contains("LIMIT")) {
            return "UNBOUNDED_GROUP_BY";
        }
        
        // Queries potencialmente eficientes
        if (upperSql.contains("WHERE") && (upperSql.contains("PRIMARY KEY") || upperSql.contains("= ?"))) {
            return "INDEXED_LOOKUP";
        }
        
        return null;
    }
    
    /**
     * Determina se uma query é considerada complexa baseada em heurísticas.
     * 
     * @param sql Query SQL
     * @return true se a query é considerada complexa
     */
    public static boolean isComplexQuery(String sql) {
        if (sql == null) return false;
        
        String upperSql = sql.toUpperCase();
        int complexity = 0;
        
        // Fatores que aumentam complexidade
        complexity += countOccurrences(upperSql, "JOIN");
        complexity += countOccurrences(upperSql, "UNION");
        complexity += countOccurrences(upperSql, "SUBQUERY");
        complexity += countOccurrences(upperSql, "EXISTS");
        complexity += countOccurrences(upperSql, "GROUP BY");
        complexity += countOccurrences(upperSql, "ORDER BY");
        complexity += countOccurrences(upperSql, "HAVING");
        
        // Múltiplas tabelas
        if (countOccurrences(upperSql, "FROM") > 1) {
            complexity += 2;
        }
        
        return complexity >= 3;
    }
    
    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}