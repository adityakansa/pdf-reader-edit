# Graph Report - PDFreaderPDFFileedit  (2026-09-29)

## Corpus Check
- Corpus is ~2,660 words - fits in a single context window. You may not need a graph.

## Summary
- 28 nodes · 24 edges · 7 communities (2 shown, 5 thin omitted)
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Template Launcher Icons
- Instrumented Test Stub
- Unit Test Stub
- Gradle Wrapper Script

## God Nodes (most connected - your core abstractions)
1. `Default Android Studio launcher icon (template, not branded)` - 10 edges
2. `ExampleInstrumentedTest` - 2 edges
3. `ExampleUnitTest` - 2 edges

## Surprising Connections (you probably didn't know these)
- None detected - all connections are within the same source files.

## Import Cycles
- None detected.

## Communities (7 total, 5 thin omitted)

### Community 1 - "Instrumented Test Stub"
Cohesion: 0.33
Nodes (4): androidjunit4, ExampleInstrumentedTest, instrumentationregistry, runwith

### Community 3 - "Gradle Wrapper Script"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Not enough signal to generate questions. This usually means the corpus has no AMBIGUOUS edges, no bridge nodes, no INFERRED relationships, and all communities are tightly cohesive. Add more files or run with --mode deep to extract richer edges._