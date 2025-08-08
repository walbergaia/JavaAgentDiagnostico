# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JavaAgentDiagnostico is a Java instrumentation agent (`-javaagent`) designed for production monitoring and observability. It operates with low intrusion, collecting JVM metrics, system metrics, exceptions, and SQL performance data, sending them to a REST endpoint for centralized analysis.

## Development Commands

### Build System
This project supports both Maven and Gradle build systems:

**Gradle (Primary)**:
- `./gradlew build` - Build and create fat JAR with all dependencies
- `./gradlew clean` - Clean previous build artifacts
- `./gradlew shadowJar` - Create fat JAR only (without running tests)
- `./gradlew runAgent` - Run test application with the agent attached

**Maven (Alternative)**:
- `mvn clean package` - Build with Maven and create shaded JAR
- `mvn clean compile` - Compile only

**Convenience Scripts**:
- `./build.sh` (Linux/macOS) or `build.bat` (Windows) - Quick build wrapper

### Testing the Agent
- `./test-agent.sh` - Comprehensive agent testing script
- `java -javaagent:build/libs/JavaAgentDiagnostico-1.0.0-SNAPSHOT.jar -jar TestApp` - Manual test
- `./diagnose.sh` - Diagnostic script for troubleshooting

### Development Utilities
- `./create-minimal-agent.sh` - Create minimal agent for testing compatibility
- `./demo-config-server.sh` - Demo script for configuration server features

## Architecture Overview

### Core Components

**Entry Point**: `AgentMain.premain()` - JVM agent initialization
- Loads configuration from `agent.properties`
- Initializes optional HTTP configuration server
- Sets up metrics collection and SQL instrumentation

**Key Architectural Layers**:

1. **Configuration Layer**
   - `ConfigLoader` - Singleton configuration manager with dynamic reloading
   - `ConfigurationServer` - HTTP server for runtime configuration changes

2. **Data Collection Layer**
   - `MetricsCollector` - JVM and system metrics (CPU, memory, threads, GC)
   - `SqlInstrumentation` + `SqlTimingAdvice` - JDBC monitoring via Byte Buddy
   - `ExceptionHandler` - Uncaught exception capture

3. **Data Management Layer**
   - `DataQueue` - In-memory data buffering
   - `LocalStorageManager` - File-based persistence for offline scenarios
   - `MetricsScheduler` - Periodic data collection orchestrator

4. **Network Layer**
   - `RestClient` - HTTP client for sending data to backend
   - `RetryManager` - Resilient retry logic with exponential backoff

5. **Data Models** (`agent.models` package)
   - `AgentMetrics` - Root metrics container
   - `CpuMetrics`, `MemoryMetrics`, `HeapMetrics`, `GcMetrics` - System metrics
   - `SqlQueryInfo` - SQL performance data
   - `ExceptionInfo` - Exception capture data

### Technology Stack
- **Java 8** compatibility (source/target)
- **Byte Buddy** - Runtime bytecode instrumentation for SQL monitoring
- **OSHI** - System metrics (CPU, memory, IO)
- **Gson** - JSON serialization
- **Built-in HTTP server** - For configuration API

## Key Configuration

The agent is controlled by `src/main/resources/agent.properties`:

**Critical Settings**:
- `enabled=true/false` - Master switch for the entire agent
- `rest.endpoint.url` - Backend API endpoint for data transmission
- `config.server.enabled` - Enable HTTP configuration server (port 8090 by default)

**Module Toggles**:
- `enable.sql.capture` - JDBC instrumentation
- `enable.exception.capture` - Exception monitoring
- `enable.gc.metrics` - Garbage collection metrics
- `enable.system.cpu.mem` - System-level metrics via OSHI

## Development Guidelines

### Adding New Metrics
1. Create model class in `agent.models` package
2. Add collection logic to `MetricsCollector`
3. Update `AgentMetrics` to include the new metric
4. Add configuration toggle in `agent.properties`

### SQL Instrumentation
Uses Byte Buddy to intercept `PreparedStatement` methods. The instrumentation:
- Captures SQL templates (sanitized, no parameter values)
- Measures execution time
- Tracks slow queries based on threshold
- Registers queries in `SqlRegistry` for deduplication

### Configuration Server API
When enabled, provides HTTP endpoints on localhost:8090:
- `GET /health` - Agent status
- `GET /config` - Current configuration
- `POST /config` - Update configuration
- `POST /config/reload` - Reload from file

### Error Handling Philosophy
- Graceful degradation: Agent failures should never crash the host application
- Extensive try-catch blocks around instrumentation code
- Fallback mechanisms for network failures (local storage)
- Configuration validation with sensible defaults

## Deployment Notes

### JAR Structure
The build produces a fat JAR containing all dependencies, designed to be attached via `-javaagent` flag. The manifest includes:
- `Premain-Class: agent.AgentMain`
- `Can-Redefine-Classes: true`
- `Can-Retransform-Classes: true`

### Compatibility
- **Java Version**: Minimum Java 8, tested with OpenJDK
- **Application Servers**: Framework-agnostic (works with any Java application)
- **Database**: JDBC-based SQL monitoring (vendor-agnostic)

### Performance Considerations
- Metrics collection runs on separate thread
- Configurable sampling intervals (default 30 seconds)
- SQL instrumentation uses efficient bytecode modification
- Memory-conscious data structures with configurable limits