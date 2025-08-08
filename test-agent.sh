#!/bin/bash

# Script para testar e resolver problemas com javaagent no OpenJDK 1.8.0_332

echo "=== Teste de Compatibilidade do Java Agent ==="
echo "OpenJDK Version: $(java -version 2>&1 | head -1)"
echo "Data: $(date)"
echo

# Função para verificar se uma opção Java é suportada
check_java_option() {
    local option="$1"
    local description="$2"
    
    echo -n "Testando $description... "
    java $option -version >/dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo "✅ Suportado"
        return 0
    else
        echo "❌ Não suportado"
        return 1
    fi
}

# Verificações básicas
echo "1. Verificações de compatibilidade:"
check_java_option "-javaagent:" "suporte para -javaagent"
check_java_option "-XX:+UnlockExperimentalVMOptions" "opções experimentais"

echo
echo "2. Verificando o JAR do agente:"
JAR_FILE="JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR não encontrado: $JAR_FILE"
    echo "   Certifique-se de que o arquivo está no diretório atual"
    exit 1
fi

echo "✅ JAR encontrado: $JAR_FILE"
echo "   Tamanho: $(ls -lh $JAR_FILE | awk '{print $5}')"

# Verificar MANIFEST
echo
echo "3. Verificando MANIFEST.MF:"
jar -tf "$JAR_FILE" | grep -q "META-INF/MANIFEST.MF"
if [ $? -eq 0 ]; then
    echo "✅ MANIFEST.MF presente"
    
    # Extrair e verificar conteúdo
    jar -xf "$JAR_FILE" META-INF/MANIFEST.MF 2>/dev/null
    if [ -f "META-INF/MANIFEST.MF" ]; then
        echo "   Conteúdo do MANIFEST:"
        sed 's/^/   /' META-INF/MANIFEST.MF
        
        if grep -q "Premain-Class" META-INF/MANIFEST.MF; then
            echo "✅ Premain-Class encontrada"
        else
            echo "❌ Premain-Class não encontrada"
        fi
        rm -rf META-INF
    fi
else
    echo "❌ MANIFEST.MF não encontrado no JAR"
fi

echo
echo "4. Criando aplicação de teste:"
cat > TestJavaAgent.java << 'EOF'
public class TestJavaAgent {
    public static void main(String[] args) {
        System.out.println("=== Teste do Java Agent ===");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("Java Vendor: " + System.getProperty("java.vendor"));
        System.out.println("OS: " + System.getProperty("os.name"));
        
        // Simular alguma atividade
        try {
            System.out.println("Aplicação iniciando...");
            Thread.sleep(1000);
            System.out.println("Aplicação executando...");
            Thread.sleep(1000);
            System.out.println("Aplicação finalizando...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
EOF

echo "✅ TestJavaAgent.java criado"

# Compilar
echo
echo "5. Compilando aplicação de teste:"
javac TestJavaAgent.java
if [ $? -eq 0 ]; then
    echo "✅ Compilação bem-sucedida"
else
    echo "❌ Falha na compilação"
    exit 1
fi

echo
echo "6. Testando execução SEM agente:"
java TestJavaAgent
echo

echo "7. Tentativas de execução COM agente:"

# Tentativa 1: Comando normal
echo "Tentativa 1 - Comando padrão:"
echo "java -javaagent:$JAR_FILE TestJavaAgent"
java -javaagent:"$JAR_FILE" TestJavaAgent
RESULT1=$?
echo "Código de saída: $RESULT1"
echo

# Tentativa 2: Com path absoluto
echo "Tentativa 2 - Com path absoluto:"
ABS_PATH=$(readlink -f "$JAR_FILE")
echo "java -javaagent:$ABS_PATH TestJavaAgent"
java -javaagent:"$ABS_PATH" TestJavaAgent
RESULT2=$?
echo "Código de saída: $RESULT2"
echo

# Tentativa 3: Com opções experimentais
echo "Tentativa 3 - Com opções experimentais:"
echo "java -XX:+UnlockExperimentalVMOptions -javaagent:$JAR_FILE TestJavaAgent"
java -XX:+UnlockExperimentalVMOptions -javaagent:"$JAR_FILE" TestJavaAgent
RESULT3=$?
echo "Código de saída: $RESULT3"
echo

# Tentativa 4: Com verbose
echo "Tentativa 4 - Com modo verbose:"
echo "java -verbose:class -javaagent:$JAR_FILE TestJavaAgent"
java -verbose:class -javaagent:"$JAR_FILE" TestJavaAgent 2>&1 | head -20
RESULT4=$?
echo "Código de saída: $RESULT4"
echo

# Análise dos resultados
echo "=== RESUMO DOS TESTES ==="
if [ $RESULT1 -eq 0 ]; then
    echo "✅ Tentativa 1 funcionou - O agente está operacional!"
elif [ $RESULT2 -eq 0 ]; then
    echo "✅ Tentativa 2 funcionou - Use path absoluto para o JAR"
elif [ $RESULT3 -eq 0 ]; then
    echo "✅ Tentativa 3 funcionou - Use opções experimentais"
elif [ $RESULT4 -eq 0 ]; then
    echo "✅ Tentativa 4 funcionou - Problema de verbose apenas"
else
    echo "❌ Todas as tentativas falharam"
    echo
    echo "POSSÍVEIS SOLUÇÕES:"
    echo "1. Atualizar OpenJDK para versão 1.8.0_40 ou superior"
    echo "2. Instalar Oracle JDK ao invés do OpenJDK"
    echo "3. Verificar se há variáveis de ambiente conflitantes"
    echo "4. Tentar com: export JAVA_TOOL_OPTIONS='-javaagent:$JAR_FILE'"
    echo "5. Verificar permissões do arquivo JAR"
    echo
    echo "Para debug adicional, execute:"
    echo "java -XX:+PrintGCDetails -XX:+PrintCommandLineFlags -javaagent:$JAR_FILE TestJavaAgent"
fi

# Cleanup
rm -f TestJavaAgent.java TestJavaAgent.class

echo
echo "=== FIM DO DIAGNÓSTICO ==="
