# HeapVortex

3D JVM memory leak profiler. A Spring Boot backend attaches to a running JVM, takes
heap dumps through JMX, parses the HPROF file, computes dominators and retained sizes,
and streams small pieces of the object graph over WebSocket to a React + Three.js UI.

## What is implemented

Heap parsing: own streaming HPROF reader (HprofReader), object graph with incoming and
outgoing edges (ObjectGraph), dominator tree with retained sizes (DominatorTree,
Cooper-Harvey-Kennedy algorithm), shortest reference chain to a GC root
(pathToGcRoot), class histogram and two-dump comparison.

Live agent side: JVM discovery and metrics through the Attach API and JMX, heap dump
trigger through HotSpotDiagnosticMXBean.

Streaming and UI: WebSocket endpoint /ws/heap (overview, expand, telemetry), 3D view
with a solar layout and a force layout.

## REST endpoints

GET  /api/jvms
GET  /api/jvms/{pid}/metrics
POST /api/jvms/{pid}/heapdump
GET  /api/heapdumps
GET  /api/heapdumps/{id}/histogram
GET  /api/heapdumps/{id}/dominators/top
GET  /api/heapdumps/{id}/dominators/suspects
GET  /api/heapdumps/{id}/objects/{objectId}/path-to-gc-root
GET  /api/heapdumps/compare?baseline=..&comparison=..
GET  /api/heapdumps/compare/leak-suspects?baseline=..&comparison=..

## Extraction audit

The test ExtractionAuditTest starts a separate JVM running LeakyApp (it adds a
LeakedSession with a 32 KB payload to a static list every 20 ms and never removes it),
attaches to it, takes two heap dumps and checks that the backend names
com.heapvortex.mock.LeakyApp$LeakedSession as the leaking class, both from the
retained-size analysis of one dump and from the growth between two dumps.

Run: powershell -ExecutionPolicy Bypass -File scripts/run-audit.ps1
Output: docs/audit-evidence.txt

### Evidence

(Paste the contents of docs/audit-evidence.txt here after you run the audit.)

## Run locally

1. Backend: cd backend, then .\mvnw spring-boot:run (needs a JDK 21, not a JRE)
2. Leaking app: run LeakyApp from the IDE
3. Frontend: cd frontend, then npm install and npm run dev, open http://localhost:5173

## Run with Docker

docker compose up --build, then open http://localhost:3000. The backend container
starts LeakyApp itself, so it shows up in the JVM list.

## Limits

The whole object graph is held in memory (default cap 2,000,000 objects, see
heapvortex.analysis.max-objects). Only reference fields become graph edges. Weak
references (the referent field) are ignored for dominators. The Attach API only sees
JVMs on the same machine or container as the backend.