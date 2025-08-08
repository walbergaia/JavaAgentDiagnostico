#!/bin/bash

# Script para criar um agente Java mínimo para teste

echo "=== Criando Agente Java Mínimo para Teste ==="

# Criar um agente mínimo sem dependências
mkdir -p minimal-agent
cd minimal-agent

# Criar classe do agente mínimo
cat > MinimalAgent.java << 'EOF'
import java.lang.instrument.Instrumentation;

public class MinimalAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("=== AGENTE JAVA MÍNIMO INICIADO ===");
        System.out.println("Argumentos do agente: " + agentArgs);
        System.out.println("Instrumentação disponível: " + (inst != null ? "SIM" : "NÃO"));
        System.out.println("Pode redefinir classes: " + inst.isRedefineClassesSupported());
        System.out.println("Pode retransformar classes: " + inst.isRetransformClassesSupported());
        System.out.println("=== AGENTE INICIALIZADO COM SUCESSO ===");
    }
}
EOF

echo "1. Compilando agente mínimo..."
javac MinimalAgent.java
if [ $? -ne 0 ]; then
    echo "❌ Falha na compilação"
    exit 1
fi

# Criar MANIFEST.MF
cat > MANIFEST.MF << 'EOF'
Manifest-Version: 1.0
Premain-Class: MinimalAgent
Can-Redefine-Classes: true
Can-Retransform-Classes: true
EOF

echo "2. Criando JAR do agente mínimo..."
jar -cfm minimal-agent.jar MANIFEST.MF MinimalAgent.class

if [ ! -f "minimal-agent.jar" ]; then
    echo "❌ Falha ao criar JAR"
    exit 1
fi

echo "✅ JAR criado: minimal-agent.jar"

# Criar aplicação de teste
cat > TestApp.java << 'EOF'
public class TestApp {
    public static void main(String[] args) {
        System.out.println("=== APLICAÇÃO DE TESTE ===");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        
        try {
            System.out.println("Executando por 3 segundos...");
            Thread.sleep(3000);
            System.out.println("Aplicação finalizada com sucesso!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
EOF

echo "3. Compilando aplicação de teste..."
javac TestApp.java
if [ $? -ne 0 ]; then
    echo "❌ Falha na compilação da aplicação"
    exit 1
fi

echo "4. Testando sem agente:"
java TestApp
echo

echo "5. Testando COM agente mínimo:"
java -javaagent:minimal-agent.jar TestApp
RESULT=$?

cd ..

if [ $RESULT -eq 0 ]; then
    echo
    echo "✅ SUCESSO! O agente mínimo funcionou."
    echo "   Isso significa que o problema está nas dependências do agente principal."
    echo "   Copie o minimal-agent.jar para o servidor e teste:"
    echo "   java -javaagent:minimal-agent/minimal-agent.jar SuaAplicacao"
else
    echo
    echo "❌ FALHA! O problema é mais fundamental."
    echo "   Sua versão do OpenJDK pode não suportar javaagent adequadamente."
    echo "   Recomendações:"
    echo "   1. Atualizar para OpenJDK 1.8.0_40 ou superior"
    echo "   2. Tentar Oracle JDK"
    echo "   3. Verificar se está usando JDK (não JRE)"
fi

echo
echo "Arquivos criados em minimal-agent/:"
ls -la minimal-agent/
