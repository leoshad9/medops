# AGENTS.md

## Build & Test (Java / Spring Boot 3.5)

Java 21+ and Maven 3.9+ are required.

```
cd medops-api
mvn test                    # run all tests
mvn test -Dtest=*NameTest   # run a specific test class
mvn checkstyle:check        # lint (0 violations expected)
```

## Lint & Typecheck (React / Vite)

```
cd medops-ui
npm run lint
npx tsc --noEmit
```
