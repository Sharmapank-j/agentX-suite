# AgentX Testing Strategy

## Test Automation Levels

### 1. Unit Tests (`app/src/test/`)
- **Domain Modeling**: Validates immutability, enum mappings, and default parameters.
- **Agent Orchestrator Logic**: Validates prompt formulation, task splitting, and supervisor control.
- **Tool Registry**: Validates tool registration, schema retrieval, and parameter validation.
- **Execution State Flow**: Tests reactive `StateFlow` updates and task status transitions.

### Running Unit Tests
```bash
./gradlew test
```

### 2. Instrumentation Tests (`app/src/androidTest/`)
- **Room DAO In-Memory Tests**: Validates entity insertion, cascade deletion, foreign key constraints, and pre-population callbacks.
- **Compose UI Tests**: Tests navigation graph transitions, drawer interactions, and message submission.

### 3. Native & Hardware Tests (Phases 3+)
- Real Android device inference verification.
- Context window strain and RAM footprint validation using Android Profiler.
