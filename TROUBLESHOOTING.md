# Soluções para o Erro "Unrecognized option: -javaagent" no OpenJDK 1.8.0_332

## Problema Identificado
Você está usando OpenJDK 1.8.0_332 (Temurin) que pode ter limitações com agentes Java.

## Soluções (em ordem de preferência)

### 1. ⚡ SOLUÇÃO IMEDIATA - Usar Agente Mínimo
Execute no seu servidor:

```bash
# 1. Fazer upload dos scripts de teste
chmod +x create-minimal-agent.sh
./create-minimal-agent.sh

# 2. Se funcionar, use o agente mínimo temporariamente:
java -javaagent:minimal-agent/minimal-agent.jar SuaAplicacao
```

### 2. 🔧 CORREÇÃO DA VERSÃO DO JAVA

#### Verificar versão exata:
```bash
java -version
java -XX:+PrintFlagsFinal -version | grep -i javaagent
```

#### Atualizar OpenJDK:
```bash
# Para CentOS/RHEL
sudo yum update java-1.8.0-openjdk java-1.8.0-openjdk-devel

# Para Ubuntu/Debian
sudo apt update && sudo apt upgrade openjdk-8-jdk

# Verificar se a versão mudou
java -version
```

### 3. 🔄 SOLUÇÕES ALTERNATIVAS

#### A. Usar variável de ambiente:
```bash
export JAVA_TOOL_OPTIONS="-javaagent:/usr/rs2000/nyx_no1/bin/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar"
java SuaAplicacao
```

#### B. Usar path absoluto completo:
```bash
java -javaagent:/usr/rs2000/nyx_no1/bin/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar SuaAplicacao
```

#### C. Testar com opções experimentais:
```bash
java -XX:+UnlockExperimentalVMOptions -javaagent:/usr/rs2000/nyx_no1/bin/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar SuaAplicacao
```

#### D. Verificar permissões:
```bash
chmod 644 /usr/rs2000/nyx_no1/bin/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar
ls -la /usr/rs2000/nyx_no1/bin/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar
```

### 4. 🛠️ DIAGNÓSTICO COMPLETO

Execute o script de diagnóstico:

```bash
# 1. Fazer upload do test-agent.sh para o servidor
chmod +x test-agent.sh

# 2. Copiar o JAR para o mesmo diretório do script
cp /usr/rs2000/nyx_no1/bin/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar .

# 3. Executar diagnóstico
./test-agent.sh
```

### 5. 🔍 VERSÕES CONHECIDAS COM PROBLEMAS

Algumas versões do OpenJDK têm problemas conhecidos:
- OpenJDK 1.8.0_292 e anteriores: Problemas com javaagent
- OpenJDK 1.8.0_332: Pode ter limitações
- **Recomendado**: OpenJDK 1.8.0_345 ou superior

### 6. 📦 INSTALAÇÃO DE VERSÃO MAIS RECENTE

#### Para CentOS/RHEL 7/8:
```bash
# Remover versão antiga
sudo yum remove java-1.8.0-openjdk

# Instalar Adoptium (recomendado)
wget https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u345-b01/OpenJDK8U-jdk_x64_linux_hotspot_8u345b01.tar.gz
sudo tar -xzf OpenJDK8U-jdk_x64_linux_hotspot_8u345b01.tar.gz -C /opt/
sudo ln -sf /opt/jdk8u345-b01/bin/java /usr/bin/java
```

#### Para Ubuntu/Debian:
```bash
# Instalar versão mais recente
sudo apt install openjdk-8-jdk-headless

# Ou usar Adoptium
wget -qO - https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | sudo apt-key add -
echo "deb https://adoptopenjdk.jfrog.io/adoptopenjdk/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/adoptopenjdk.list
sudo apt update
sudo apt install adoptopenjdk-8-hotspot
```

### 7. 🚨 SOLUÇÃO DE EMERGÊNCIA

Se nada funcionar, compile uma versão ainda mais simples:

```bash
# Criar AgentMain.java minimalista
cat > AgentMain.java << 'EOF'
import java.lang.instrument.Instrumentation;

public class AgentMain {
    public static void premain(String args, Instrumentation inst) {
        System.out.println("Agent loaded successfully!");
    }
}
EOF

# Compilar
javac AgentMain.java

# Criar MANIFEST
echo "Premain-Class: AgentMain" > manifest.txt

# Criar JAR
jar -cfm simple-agent.jar manifest.txt AgentMain.class

# Testar
java -javaagent:simple-agent.jar SuaAplicacao
```

## ✅ Como Verificar se a Solução Funcionou

Quando o agente estiver funcionando, você verá:
```
============================================================
  Iniciando JavaAgentDiagnóstico...
============================================================
Configuração carregada de agent.properties
...
```

Ao invés de:
```
Unrecognized option: -javaagent=...
Error: Could not create the Java Virtual Machine.
```
