-- =================================================================
-- Esquema do Banco de Dados para o Projeto JavaAgentDiagnostico
-- Dialeto: PostgreSQL (pode ser adaptado para outros SGBDs)
-- =================================================================

-- Tabela para registrar as instâncias únicas do agente em execução.
-- Cada agente/aplicação/host é uma instância.
CREATE TABLE agent_instances (
    id SERIAL PRIMARY KEY,
    agent_name VARCHAR(255) NOT NULL,
    application_name VARCHAR(255) NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    ip_address VARCHAR(100),
    first_seen TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Garante que a combinação de agente, aplicação e host seja única.
    UNIQUE(agent_name, application_name, hostname)
);

-- Tabela principal para armazenar as métricas periódicas coletadas.
CREATE TABLE agent_metrics (
    id BIGSERIAL PRIMARY KEY,
    instance_id INTEGER NOT NULL REFERENCES agent_instances(id) ON DELETE CASCADE,
    timestamp TIMESTAMPTZ NOT NULL,
    uptime_ms BIGINT,
    
    -- Métricas de Heap
    heap_used_bytes BIGINT,
    heap_max_bytes BIGINT,
    heap_committed_bytes BIGINT,

    -- Métricas de CPU (valores entre 0.0 e 1.0)
    cpu_process_load DOUBLE PRECISION,
    cpu_system_load DOUBLE PRECISION,

    -- Métricas de Memória do SO
    mem_total_bytes BIGINT,
    mem_free_bytes BIGINT
);
CREATE INDEX idx_agent_metrics_timestamp ON agent_metrics(timestamp);
CREATE INDEX idx_agent_metrics_instance_id ON agent_metrics(instance_id);


-- Tabela para armazenar as estatísticas de threads de cada coleta.
-- Ligada a uma entrada em agent_metrics.
CREATE TABLE thread_metrics (
    id BIGSERIAL PRIMARY KEY,
    metric_id BIGINT NOT NULL REFERENCES agent_metrics(id) ON DELETE CASCADE,
    total INTEGER,
    runnable INTEGER,
    blocked INTEGER,
    waiting INTEGER,
    timed_waiting INTEGER,
    deadlocks INTEGER
);
CREATE INDEX idx_thread_metrics_metric_id ON thread_metrics(metric_id);


-- Tabela para armazenar informações sobre exceções não tratadas.
CREATE TABLE exceptions (
    id BIGSERIAL PRIMARY KEY,
    instance_id INTEGER NOT NULL REFERENCES agent_instances(id) ON DELETE CASCADE,
    timestamp TIMESTAMPTZ NOT NULL,
    exception_type VARCHAR(255) NOT NULL,
    message TEXT,
    stack_trace TEXT NOT NULL,
    thread_name VARCHAR(255)
);
CREATE INDEX idx_exceptions_timestamp ON exceptions(timestamp);
CREATE INDEX idx_exceptions_instance_id ON exceptions(instance_id);
CREATE INDEX idx_exceptions_type ON exceptions(exception_type);


-- Tabela para armazenar informações sobre as queries SQL executadas.
CREATE TABLE sql_queries (
    id BIGSERIAL PRIMARY KEY,
    instance_id INTEGER NOT NULL REFERENCES agent_instances(id) ON DELETE CASCADE,
    timestamp TIMESTAMPTZ NOT NULL,
    query_text TEXT NOT NULL,
    query_type VARCHAR(50),
    duration_ms BIGINT NOT NULL,
    is_slow BOOLEAN NOT NULL,
    error_message TEXT,
    thread_name VARCHAR(255)
);
CREATE INDEX idx_sql_queries_timestamp ON sql_queries(timestamp);
CREATE INDEX idx_sql_queries_instance_id ON sql_queries(instance_id);
CREATE INDEX idx_sql_queries_is_slow ON sql_queries(is_slow);


/*
-- Tabela opcional para registrar alertas gerados pelo backend (ex: CPU > 90%).
-- A lógica de geração de alertas residiria no backend que recebe os dados.
CREATE TABLE alert_logs (
    id BIGSERIAL PRIMARY KEY,
    instance_id INTEGER NOT NULL REFERENCES agent_instances(id) ON DELETE CASCADE,
    timestamp TIMESTAMPTZ NOT NULL,
    alert_type VARCHAR(100) NOT NULL, -- Ex: 'HIGH_CPU', 'SLOW_QUERY', 'DEADLOCK_DETECTED'
    triggering_metric_id BIGINT REFERENCES agent_metrics(id),
    details JSONB -- Armazena os dados que causaram o alerta
);
*/

-- =================================================================
-- Fim do script
-- =================================================================