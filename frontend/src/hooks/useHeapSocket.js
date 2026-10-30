import { useCallback, useEffect, useRef, useState } from "react";

const WS_URL = "ws://localhost:8080/ws/heap";

/**
 * Keeps the socket open, collects the node and edge batches into maps,
 * and exposes the latest telemetry. "version" changes when a graph answer is complete.
 * "rootId" is the object the last graph answer was centred on (null for the overview).
 */
export function useHeapSocket() {
    const wsRef = useRef(null);
    const nodes = useRef(new Map());
    const edges = useRef(new Map());
    const [version, setVersion] = useState(0);
    const [rootId, setRootId] = useState(null);
    const [status, setStatus] = useState("connecting");
    const [loading, setLoading] = useState(false);
    const [truncated, setTruncated] = useState(false);
    const [error, setError] = useState(null);
    const [telemetry, setTelemetry] = useState(null);

    useEffect(() => {
        let closed = false;
        let retry = null;

        function connect() {
            const ws = new WebSocket(WS_URL);
            wsRef.current = ws;
            ws.onopen = () => setStatus("open");
            ws.onclose = () => {
                setStatus("closed");
                if (!closed) retry = setTimeout(connect, 2000);
            };
            ws.onmessage = (event) => handle(JSON.parse(event.data));
        }

        function handle(m) {
            switch (m.type) {
                case "start":
                    setLoading(true);
                    setError(null);
                    setTruncated(Boolean(m.truncated));
                    setRootId(m.rootId ?? null);
                    if (m.view === "overview") {
                        nodes.current.clear();
                        edges.current.clear();
                    }
                    break;
                case "nodes":
                    for (const n of m.items) nodes.current.set(n.id, n);
                    break;
                case "edges":
                    for (const e of m.items) {
                        edges.current.set(`${e.source}|${e.target}|${e.label}`, e);
                    }
                    break;
                case "done":
                    setLoading(false);
                    setVersion((v) => v + 1);
                    break;
                case "telemetry":
                    setTelemetry(m.data);
                    break;
                case "error":
                    setError(m.message);
                    setLoading(false);
                    break;
                default:
                    break;
            }
        }

        connect();
        return () => {
            closed = true;
            clearTimeout(retry);
            wsRef.current?.close();
        };
    }, []);

    const send = useCallback((payload) => {
        const ws = wsRef.current;
        if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(payload));
    }, []);

    const overview = useCallback(
        (dumpId, topN = 10, childrenPerNode = 8) =>
            send({ type: "overview", dumpId, topN, childrenPerNode }),
        [send]
    );

    const expand = useCallback(
        (dumpId, objectId, depth = 2, maxNodes = 400) =>
            send({ type: "expand", dumpId, objectId, depth, maxNodes }),
        [send]
    );

    const startTelemetry = useCallback(
        (pid) => send({ type: "telemetry", pid, intervalMs: 1000 }),
        [send]
    );

    const stopTelemetry = useCallback(() => send({ type: "stopTelemetry" }), [send]);

    return {
        nodes: nodes.current,
        edges: edges.current,
        version,
        rootId,
        status,
        loading,
        truncated,
        error,
        telemetry,
        overview,
        expand,
        startTelemetry,
        stopTelemetry,
    };
}