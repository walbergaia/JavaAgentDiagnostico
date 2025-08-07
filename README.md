# JavaAgentDiagnostico - Agente de Monitoramento Produtivo

##  visão geral

O **JavaAgentDiagnostico** é um agente de instrumentação Java (`javaagent`) projetado para monitoramento e observabilidade de aplicações em ambientes de produção. Ele opera com baixa intrusão, é altamente configurável e coleta uma ampla gama de métricas, exceções e dados de performance de SQL, enviando tudo para um endpoint REST para análise centralizada.

---

## 🚀 Funcionalidades Principais

-   **Métricas da JVM e do Sistema**: Coleta de uso de Heap, Non-Heap (Metaspace, CodeCache), estatísticas de Garbage Collection, threads, uso de CPU (processo e sistema) e memória (RAM e Swap).
-   **Captura de Exceções**: Registra todas as exceções não tratadas em qualquer thread da aplicação, incluindo tipo, mensagem e stack trace.
-   **Instrumentação de SQL**: Utiliza Byte Buddy para interceptar chamadas JDBC (`PreparedStatement`), capturando o template da query, tempo de execução e erros, sem expor dados sensíveis.
-   **Altamente Configurável**: Todo o comportamento do agente é controlado por um arquivo externo `agent.properties`, permitindo ativar ou desativar funcionalidades em tempo real sem alterar o código.
-   **Envio para API REST**: Consolida todos os dados coletados em um payload JSON e os envia periodicamente para um endpoint REST configurável, com suporte para autenticação via Bearer Token.
-   **Segurança**: Sanitiza queries SQL para remover parâmetros e limita a profundidade dos stack traces para evitar o vazamento de informações sensíveis.

---

## 🏗️ Arquitetura

O agente é anexado à JVM durante a inicialização e opera em segundo plano, coletando dados em uma thread separada para minimizar o impacto na aplicação principal.

```
[ Aplicação Java ]
        │
   -javaagent:diagnostic-agent.jar
        │
[ Agente Instrumentador (JavaAgentDiagnostico) ]
        │
  ┌───────────────────────────┐
  │  - Coletor de Métricas    │
  │  - Captura de Exceções    │
  │  - Instrumentação SQL     │
  │  - Configuração Externa   │
  └───────────────────────────┘
        │
[      Payload JSON     ]
        │
[    API REST Backend   ]
        │
[ Banco de Dados Relacional ]
```

---

## 🛠️ Como Construir

O projeto utiliza o Maven. Para construir o agente e empacotar todas as dependências em um único arquivo JAR, execute o seguinte comando na raiz do projeto:

```bash
mvn clean package
```

O artefato final, `JavaAgentDiagnostico-1.0-SNAPSHOT-jar-with-dependencies.jar`, será gerado no diretório `target/`.

---

## ⚙️ Como Usar

Para monitorar sua aplicação, anexe o agente durante a inicialização usando a flag `-javaagent`.

1.  **Renomeie** o JAR gerado para `diagnostic-agent.jar` (ou o nome que preferir).
2.  **Coloque** o `diagnostic-agent.jar` e o arquivo `agent.properties` no mesmo diretório da sua aplicação.
3.  **Inicie** sua aplicação com a flag:

```bash
java -javaagent:diagnostic-agent.jar -jar sua-aplicacao.jar
```

O agente será carregado automaticamente e começará a enviar dados com base nas configurações do `agent.properties`.

---

## 📝 Configuração (`agent.properties`)

A seguir, a lista de todas as propriedades de configuração disponíveis:

| Chave                          | Tipo    | Descrição                                                 | Padrão  |
| ------------------------------ | ------- | --------------------------------------------------------- | ------- |
| `enabled`                      | boolean | Ativa ou desativa o agente completamente.                 | `true`  |
| `agent.name`                   | string  | Nome lógico do agente (ex: "agente-pagamentos").          | `java-agent` |
| `application.name`             | string  | Nome da aplicação monitorada (ex: "servico-de-faturas").  | `default-app` |
| `sampling.interval.ms`         | int     | Intervalo em milissegundos para a coleta de métricas.     | `15000` |
| `send.rest.enabled`            | boolean | Se `true`, envia os dados para a API REST.                | `true`  |
| `rest.endpoint.url`            | string  | URL do endpoint da API para onde os dados serão enviados. | -       |
| `auth.token`                   | string  | Token de autenticação (Bearer) para a API REST. Opcional. | -       |
| `enable.exception.capture`     | boolean | Ativa a captura de exceções não tratadas.                 | `true`  |
| `enable.sql.capture`           | boolean | Ativa o monitoramento de queries SQL.                     | `true`  |
| `sql.slow.threshold.ms`        | int     | Limiar em ms para considerar uma query como "lenta".      | `1000`  |
| `enable.deep.stack.analysis`   | boolean | Se `true`, captura o stack trace completo das exceções.   | `false` |
| `enable.gc.metrics`            | boolean | Ativa a coleta de métricas de Garbage Collection.         | `true`  |
| `enable.classloading.metrics`  | boolean | Ativa a coleta de métricas de carregamento de classes.    | `true`  |
| `enable.system.cpu.mem`        | boolean | Ativa a coleta de métricas de CPU e RAM do SO.            | `true`  |

---

## 🗃️ Esquema do Banco de Dados

O repositório inclui um script SQL em `/database-schema/V1__create_initial_tables.sql` para criar todas as tabelas necessárias em um banco de dados relacional (otimizado para PostgreSQL) para armazenar os dados recebidos pelo agente.