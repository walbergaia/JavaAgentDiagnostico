#!/bin/bash

echo "=== Diagnóstico do Java Agent ==="
echo "Data: $(date)"
echo

echo "1. Versão do Java:"
java -version
echo

echo "2. Localização do Java:"
which java
echo

echo "3. JAVA_HOME:"
echo "JAVA_HOME: $JAVA_HOME"
echo

echo "4. Verificando se -javaagent é suportado:"
java -help 2>&1 | grep -i javaagent
if [ $? -eq 0 ]; then
    echo "✅ -javaagent é suportado"
else
    echo "❌ -javaagent NÃO é suportado nesta versão do Java"
fi
echo

echo "5. Verificando o JAR do agente:"
if [ -f "./JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar" ]; then
    echo "✅ JAR encontrado: JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar"
    echo "Tamanho: $(ls -lh JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar | awk '{print $5}')"
    
    echo "6. Verificando MANIFEST.MF:"
    jar -xf JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar META-INF/MANIFEST.MF 2>/dev/null
    if [ -f "META-INF/MANIFEST.MF" ]; then
        echo "Conteúdo do MANIFEST.MF:"
        cat META-INF/MANIFEST.MF
        rm -rf META-INF
        echo
        
        # Verificar se tem Premain-Class
        jar -xf JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar META-INF/MANIFEST.MF 2>/dev/null
        if grep -q "Premain-Class" META-INF/MANIFEST.MF 2>/dev/null; then
            echo "✅ Premain-Class encontrada no MANIFEST"
        else
            echo "❌ Premain-Class NÃO encontrada no MANIFEST"
        fi
        rm -rf META-INF
    else
        echo "❌ Não foi possível extrair o MANIFEST.MF"
    fi
else
    echo "❌ JAR não encontrado: JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar"
fi
echo

echo "7. Teste simples com uma classe Hello World:"
cat > HelloWorld.java << 'EOF'
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello World sem agente!");
    }
}
EOF

echo "Compilando HelloWorld..."
javac HelloWorld.java
if [ $? -eq 0 ]; then
    echo "✅ Compilação bem-sucedida"
    
    echo "Executando sem agente:"
    java HelloWorld
    
    if [ -f "./JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar" ]; then
        echo
        echo "Tentando executar COM agente:"
        java -javaagent:./JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar HelloWorld
        if [ $? -eq 0 ]; then
            echo "✅ Agente funcionou!"
        else
            echo "❌ Falha ao executar com agente"
        fi
    fi
    
    # Cleanup
    rm -f HelloWorld.java HelloWorld.class
else
    echo "❌ Falha na compilação"
fi

echo
echo "=== Fim do Diagnóstico ==="
echo
echo "Se o problema persistir, tente:"
echo "1. Usar uma versão mais recente do Java (8u40+ ou superior)"
echo "2. Verificar se está usando o JDK ao invés do JRE"
echo "3. Tentar com java -XX:+UnlockExperimentalVMOptions"
