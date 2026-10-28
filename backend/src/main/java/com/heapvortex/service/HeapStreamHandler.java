package com.heapvortex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heapvortex.dto.GraphEdgeDto;
import com.heapvortex.dto.GraphNodeDto;
import com.heapvortex.dto.SubgraphDto;
import com.heapvortex.heap.SubgraphService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One socket for everything. The client sends JSON requests:
 *
 *   {"type":"overview","dumpId":"heap-123-...","topN":10,"childrenPerNode":5}
 *   {"type":"expand","dumpId":"heap-123-...","objectId":"0x7f3a","depth":2,"maxNodes":500}
 *   {"type":"telemetry","pid":"1234","intervalMs":1000}
 *   {"type":"stopTelemetry"}
 *
 * A graph answer arrives as: "start", several "nodes" batches, several "edges" batches, "done".
 * Telemetry arrives as {"type":"telemetry","data":{...}} on every tick. Errors as {"type":"error","message":"..."}.
 */
@Component
public class HeapStreamHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(HeapStreamHandler.class);

    private static final int NODE_BATCH = 200;
    private static final int EDGE_BATCH = 500;
    private static final int MAX_NODES = 2000;
    private static final int MAX_DEPTH = 6;

    /** What the client may send. Fields that a request type does not use stay null. */
    public record StreamRequest(String type, String dumpId, String objectId, String pid,
                                Integer topN, Integer childrenPerNode,
                                Integer depth, Integer maxNodes, Integer intervalMs) {}

    private final ObjectMapper mapper;
    private final SubgraphService subgraphService;
    private final JvmTelemetryService telemetryService;
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> telemetryTasks = new ConcurrentHashMap<>();

    public HeapStreamHandler(ObjectMapper mapper,
                             SubgraphService subgraphService,
                             JvmTelemetryService telemetryService) {
        this.mapper = mapper;
        this.subgraphService = subgraphService;
        this.telemetryService = telemetryService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        StreamRequest request;
        try {
            request = mapper.readValue(message.getPayload(), StreamRequest.class);
        } catch (IOException e) {
            send(session, msg("error", "message", "Invalid request JSON"));
            return;
        }
        if (request.type() == null) {
            send(session, msg("error", "message", "Missing type"));
            return;
        }

        switch (request.type()) {
            case "overview" -> workers.submit(() -> run(session, () -> {
                int topN = bounded(request.topN(), 10, 50);
                int children = bounded(request.childrenPerNode(), 5, 50);
                streamGraph(session, "overview", subgraphService.overview(request.dumpId(), topN, children));
            }));
            case "expand" -> workers.submit(() -> run(session, () -> {
                int depth = bounded(request.depth(), 2, MAX_DEPTH);
                int maxNodes = bounded(request.maxNodes(), 500, MAX_NODES);
                streamGraph(session, "expand",
                        subgraphService.expand(request.dumpId(), request.objectId(), depth, maxNodes));
            }));
            case "telemetry" -> startTelemetry(session, request);
            case "stopTelemetry" -> stopTelemetry(session.getId());
            default -> send(session, msg("error", "message", "Unknown type: " + request.type()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        stopTelemetry(session.getId());
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        workers.shutdownNow();
    }

    private void startTelemetry(WebSocketSession session, StreamRequest request) {
        if (request.pid() == null || request.pid().isBlank()) {
            send(session, msg("error", "message", "pid is required"));
            return;
        }
        stopTelemetry(session.getId());
        long interval = Math.max(500, request.intervalMs() == null ? 1000 : request.intervalMs());
        String pid = request.pid();

        ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(() -> {
            try {
                send(session, msg("telemetry", "data", telemetryService.collect(pid)));
            } catch (RuntimeException e) {
                send(session, msg("error", "message", "Telemetry stopped: " + e.getMessage()));
                stopTelemetry(session.getId());
            }
        }, 0, interval, TimeUnit.MILLISECONDS);
        telemetryTasks.put(session.getId(), task);
    }

    private void stopTelemetry(String sessionId) {
        ScheduledFuture<?> task = telemetryTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
        }
    }

    private void streamGraph(WebSocketSession session, String view, SubgraphDto graph) {
        send(session, msg("start", "view", view, "rootId", graph.rootId(),
                "nodeCount", graph.nodes().size(), "edgeCount", graph.edges().size(),
                "truncated", graph.truncated()));

        List<GraphNodeDto> nodes = graph.nodes();
        for (int i = 0; i < nodes.size(); i += NODE_BATCH) {
            send(session, msg("nodes", "items", nodes.subList(i, Math.min(i + NODE_BATCH, nodes.size()))));
        }
        List<GraphEdgeDto> edges = graph.edges();
        for (int i = 0; i < edges.size(); i += EDGE_BATCH) {
            send(session, msg("edges", "items", edges.subList(i, Math.min(i + EDGE_BATCH, edges.size()))));
        }
        send(session, msg("done"));
    }

    private void run(WebSocketSession session, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            log.warn("Stream request failed", e);
            send(session, msg("error", "message", String.valueOf(e.getMessage())));
        }
    }

    /** A WebSocketSession is not safe for two threads sending at once, so sends are serialized. */
    private void send(WebSocketSession session, Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.debug("Could not send to session {}", session.getId(), e);
        }
    }

    private static int bounded(Integer value, int fallback, int max) {
        int v = value == null ? fallback : value;
        return Math.max(1, Math.min(v, max));
    }

    private static Map<String, Object> msg(String type, Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}