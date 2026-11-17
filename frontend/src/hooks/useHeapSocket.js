import { useCallback, useEffect, useRef, useState } from "react";
import { appendGcEvent } from "../utils/gcLog.js";
import { pushSample } from "../utils/sparkline.js";

const WS_URL = `${window.location.protocol === "https:" ? "wss" : "ws"}://${window.location.host}/ws/heap`;

/**
 * Keeps the socket open, collects the node and edge batches into maps, and exposes the live
 * telemetry, CPU, JMX memory pools, GC events and short history lists for the 2D charts.
 * "version" changes when a graph answer is complete.
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
    const [cpu, setCpu] = useState(null);
    const [pools, setPools] = useState([]);
    const [gcEvents, setGcEvents] = useState([]);
    const [heapHistory, setHeapHistory] = useState([]);
    const [cpuHistory, setCpuHistory] = useState([]);

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
                case "telemetry": {
                    setTelemetry(m.data);
                    const memory = m.data?.memory;
                    if (memory?.heapMax > 0) {
                        const percent = (100 * memory.heapUsed) / memory.heapMax;
                        setHeapHistory((previous) => pushSample(previous, percent));
                    }
                    break;
                }
                case "cpu":
                    setCpu(m.data);
                    if (m.data?.processCpuPercent >= 0) {
                        setCpuHistory((previous) => pushSample(previous, m.data.processCpuPercent));
                    }
                    break;
                case "pools":
                    setPools(m.data);
                    break;
                case "gc":
                    setGcEvents((previous) => appendGcEvent(previous, m.data));
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
        (pid) => {
            setTelemetry(null);
            setCpu(null);
            setPools([]);
            setHeapHistory([]);
            setCpuHistory([]);
            send({ type: "telemetry", pid, intervalMs: 1000 });
        },
        [send]
    );

    const stopTelemetry = useCallback(() => send({ type: "stopTelemetry" }), [send]);

    const startGcEvents = useCallback(
        (pid) => {
            setGcEvents([]);
            send({ type: "gcEvents", pid });
        },
        [send]
    );

    const stopGcEvents = useCallback(() => send({ type: "stopGcEvents" }), [send]);

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
        cpu,
        pools,
        gcEvents,
        heapHistory,
        cpuHistory,
        overview,
        expand,
        startTelemetry,
        stopTelemetry,
        startGcEvents,
        stopGcEvents,
    };
}