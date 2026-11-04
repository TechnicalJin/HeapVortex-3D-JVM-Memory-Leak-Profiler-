# HeapVortex

**HeapVortex** is a 3D JVM memory leak profiler built with Java 21,
Spring Boot, React, and Three.js. It connects to a running JVM, captures
heap dumps through JMX, parses HPROF data, analyzes object references
and retained sizes, and streams selected graph data to an interactive 3D
interface over WebSocket.

## Features

### Heap analysis

-   Custom streaming HPROF reader (`HprofReader`).
-   Object graph containing incoming and outgoing reference edges
    (`ObjectGraph`).
-   Dominator tree and retained-size calculation using the
    Cooper--Harvey--Kennedy algorithm (`DominatorTree`).
-   Shortest reference chain from an object to a GC root
    (`pathToGcRoot`).
-   Class histogram.
-   Two-heap-dump comparison and leak-suspect growth analysis.

### JVM monitoring and heap-dump capture

-   JVM discovery using the Attach API.
-   JVM metrics collection through JMX.
-   Heap-dump capture through `HotSpotDiagnosticMXBean`.

### Streaming and visualization

-   WebSocket endpoint: `/ws/heap`.
-   WebSocket operations include overview, graph expansion, and
    telemetry.
-   React + Three.js interface for exploring the heap graph.
-   Solar and force graph layouts.
-   Instanced rendering and synthetic graph generation for large-graph
    rendering checks.

## Technology stack

  -----------------------------------------------------------------------
  Area                                Technology
  ----------------------------------- -----------------------------------
  Backend                             Java 21, Spring Boot

  JVM attachment and monitoring       Attach API, JMX

  Heap dump analysis                  Custom HPROF reader, object graph,
                                      dominator analysis

  Real-time communication             WebSocket

  Frontend                            React, Three.js, WebGL

  Backend build                       Maven Wrapper

  Frontend build                      npm
  -----------------------------------------------------------------------

## Architecture overview

1.  The backend discovers locally accessible JVM processes.
2.  It attaches to a selected JVM and collects metrics through JMX.
3.  It can request a heap dump through `HotSpotDiagnosticMXBean`.
4.  The HPROF reader parses the dump and builds an object-reference
    graph.
5.  Heap analysis calculates dominators, retained sizes, GC-root paths,
    histograms, and differences between dumps.
6.  The backend exposes analysis results through REST endpoints and
    streams graph overview, expansion, and telemetry messages over
    `/ws/heap`.
7.  The React + Three.js frontend displays the graph using solar or
    force layout.

## REST API

The backend exposes the following endpoints:

  ------------------------------------------------------------------------------------------------------------------
  Method                  Endpoint                                                           Purpose
  ----------------------- ------------------------------------------------------------------ -----------------------
  `GET`                   `/api/jvms`                                                        List discoverable JVM
                                                                                             processes

  `GET`                   `/api/jvms/{pid}/metrics`                                          Read metrics for a JVM

  `POST`                  `/api/jvms/{pid}/heapdump`                                         Trigger a heap dump

  `GET`                   `/api/heapdumps`                                                   List available heap
                                                                                             dumps

  `GET`                   `/api/heapdumps/{id}/histogram`                                    Retrieve a class
                                                                                             histogram

  `GET`                   `/api/heapdumps/{id}/dominators/top`                               Retrieve top dominators

  `GET`                   `/api/heapdumps/{id}/dominators/suspects`                          Retrieve
                                                                                             dominator-based leak
                                                                                             suspects

  `GET`                   `/api/heapdumps/{id}/objects/{objectId}/path-to-gc-root`           Retrieve an object's
                                                                                             path to a GC root

  `GET`                   `/api/heapdumps/compare?baseline=..&comparison=..`                 Compare two heap dumps

  `GET`                   `/api/heapdumps/compare/leak-suspects?baseline=..&comparison=..`   Compare leak suspects
                                                                                             between two heap dumps
  ------------------------------------------------------------------------------------------------------------------

### WebSocket

-   **Endpoint:** `/ws/heap`
-   **Supported message areas:** overview, graph expansion, and
    telemetry.

The exact message payload schemas are defined by the backend
implementation.

## Requirements

For local development:

-   JDK 21 (a JDK is required; a JRE is not sufficient for the
    documented Attach API workflow).
-   Node.js and npm.
-   Windows PowerShell or a compatible terminal.
-   Maven is available through the included Maven Wrapper.

For Docker execution, Docker and Docker Compose are required.

## Run locally

Run the backend, leaking sample application, and frontend in separate
terminals.

### 1. Start the backend

From the repository root:

``` powershell
cd backend
.\mvnw spring-boot:run
```

Ensure that Java 21 is installed and selected.

### 2. Start the sample leaking application

Run `LeakyApp` from the IDE. The application is a mock memory-leaking
JVM used for monitoring and extraction-audit verification.

The audit script can also launch its own separate mock JVM; see
[Extraction audit](#extraction-audit).

### 3. Start the frontend

From the repository root:

``` powershell
cd frontend
npm install
npm run dev
```

Open:

<http://localhost:5173>

## Run with Docker

From the repository root:

``` bash
docker compose up --build
```

Open:

<http://localhost:3000>

The documented Docker setup starts `LeakyApp` inside the backend
container so that it appears in the JVM list.

To stop the containers, use:

``` bash
docker compose down
```

## Extraction audit

The automated `ExtractionAuditTest` verifies that HeapVortex can
identify a leak in a separate mock JVM.

The mock `LeakyApp`:

-   Adds a `LeakedSession` object to a static list every 20
    milliseconds.
-   Gives each session a 32 KB payload.
-   Never removes the sessions.

The audit:

1.  Starts the separate mock JVM.
2.  Attaches to it.
3.  Captures two heap dumps.
4.  Checks the retained-size analysis of a dump.
5.  Checks growth between the two dumps.
6.  Verifies that the leaking class is identified as:

``` text
com.heapvortex.mock.LeakyApp$LeakedSession
```

Run the audit from the repository root using PowerShell:

``` powershell
powershell -ExecutionPolicy Bypass -File scripts/run-audit.ps1
```

Audit output is written to:

``` text
docs/audit-evidence.txt
```

## Mid-Project Review evidence

**Review date:** 2026-11-03

The following results were recorded for the Mid-Project Review.

### Backend tests

-   165 tests run.
-   0 failures.

Evidence file:

``` text
docs/backend-test-output.txt
```

### Frontend tests

-   22 tests run.
-   0 failures.

### Extraction audit

The recorded audit evidence reports:

-   Successfully attached to the mock JVM.
-   Captured two heap dumps.
-   Dominator suspect: `LeakyApp`.
-   Retained size: **12,020,507 bytes**.
-   Leak class: `com.heapvortex.mock.LeakyApp$LeakedSession`.
-   Root kind: `STICKY_CLASS`.
-   Growth between dumps: **194 additional `LeakedSession` instances**.
-   `byte[]` growth: **6,358,371 bytes**.

Evidence file:

``` text
docs/audit-evidence.txt
```

### Rendering check

Chrome rendering results using instanced rendering and a synthetic
graph:

      Graph size    Edges   Observed frame rate Result
  -------------- -------- --------------------- ------------------
    10,000 nodes    9,999            29--59 FPS No browser crash
    20,000 nodes   19,999            34--54 FPS No browser crash

Recorded Chrome tab memory with the graph loaded was approximately **190
MB**.

Evidence file:

``` text
docs/rendering-check.txt
```

### Screenshots

Review screenshots are stored in:

``` text
docs/screenshots/
```

## Current limits

-   The complete object graph is held in memory.
-   The default maximum object count is **2,000,000**. The setting is
    `heapvortex.analysis.max-objects`.
-   Only reference fields are represented as graph edges.
-   Weak references (the `referent` field) are ignored for dominator
    calculations.
-   The Attach API can only see JVMs running on the same machine or in
    the same container as the backend.
-   Above **1,500 nodes**, the visualization uses the solar layout only.
-   A heap dump containing more than **2,000,000 objects** is rejected
    by the configured object limit.

## Repository evidence and documentation

Relevant review artifacts:

``` text
docs/
├── audit-evidence.txt
├── backend-test-output.txt
├── rendering-check.txt
└── screenshots/
```

The project also includes the `ExtractionAuditTest` automated test.

## Project status

As of the Mid-Project Review dated **2026-11-03**, the documented heap
parsing, retained-size and GC-root analysis, extraction audit, JVM
discovery and metrics, WebSocket graph streaming, 3D graph views, and
large-graph rendering checks have been implemented and reviewed against
the stated milestone evidence.

------------------------------------------------------------------------

**HeapVortex -- 3D JVM Memory Leak Profiler**
