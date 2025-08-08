#!/bin/bash
echo "Compilando JavaAgentDiagnostico com Gradle..."
./gradlew build
if [ $? -eq 0 ]; then
    echo ""
    echo "===================================================="
    echo "Compilação bem-sucedida!"
    echo "JAR gerado: build/libs/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar"
    echo "===================================================="
    echo ""
    echo "Para usar o agente:"
    echo "java -javaagent:build/libs/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar SuaAplicacao"
    echo ""
else
    echo ""
    echo "===================================================="
    echo "Erro na compilação!"
    echo "===================================================="
fi
