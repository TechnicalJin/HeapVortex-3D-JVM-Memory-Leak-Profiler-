import LiveMetricsPanel from './LiveMetricsPanel.jsx'
import Sparkline from './Sparkline.jsx'
import { formatBytes, usagePercent } from '../utils/format.js'

const card = {
    background: 'rgba(15, 23, 42, 0.85)',
    color: '#e2e8f0',
    padding: 12,
    borderRadius: 8,
    fontFamily: 'system-ui, sans-serif',
    fontSize: 13,
}

function percentText(value) {
    return typeof value === 'number' && value >= 0 ? `${value.toFixed(1)}%` : 'n/a'
}

function Bar({ percent, color }) {
    return (
        <div style={{ background: '#334155', height: 8, borderRadius: 4, marginTop: 4 }}>
            <div
                style={{
                    width: `${Math.min(100, Math.max(0, percent))}%`,
                    height: 8,
                    borderRadius: 4,
                    background: color,
                }}
            />
        </div>
    )
}

/** The standard 2D metrics that sit next to the 3D view. */
export default function MetricsSidebar({ pid, telemetry, cpu, heapHistory, cpuHistory, pools, gcEvents, watch }) {
    const memory = telemetry?.memory
    const heapPercent = usagePercent(memory?.heapUsed, memory?.heapMax) ?? 0

    const watchColor =
        watch?.state === 'SUSPECTED' ? '#f87171' : watch?.state === 'ERROR' ? '#fbbf24' : '#e2e8f0'

    return (
        <aside
            style={{
                width: 300,
                flexShrink: 0,
                boxSizing: 'border-box',
                padding: 10,
                display: 'flex',
                flexDirection: 'column',
                gap: 8,
                overflowY: 'auto',
                background: '#080d19',
                borderLeft: '1px solid #1e293b',
            }}
        >
            <div style={{ color: '#e2e8f0', fontFamily: 'system-ui, sans-serif', fontWeight: 700, fontSize: 15 }}>
                Live metrics
            </div>

            {!telemetry && (
                <div style={{ ...card, opacity: 0.8 }}>Choose a JVM to see its live metrics.</div>
            )}

            {telemetry && (
                <div style={card}>
                    <div style={{ fontWeight: 600, marginBottom: 6 }}>Live JVM {telemetry.pid}</div>

                    <div>
                        Heap {formatBytes(memory?.heapUsed)} / {formatBytes(memory?.heapMax)}
                    </div>
                    <Bar percent={heapPercent} color={heapPercent > 80 ? '#ef4444' : '#3b82f6'} />

                    <div style={{ marginTop: 8 }}>Heap usage {heapPercent}%</div>
                    <Sparkline values={heapHistory} color={heapPercent > 80 ? '#ef4444' : '#3b82f6'} />

                    <div style={{ marginTop: 8 }}>Process CPU {percentText(cpu?.processCpuPercent)}</div>
                    <Sparkline values={cpuHistory} color="#f59e0b" />

                    <div style={{ marginTop: 6 }}>
                        System CPU {percentText(cpu?.systemCpuPercent)}
                        {cpu?.availableProcessors ? ` (${cpu.availableProcessors} cores)` : ''}
                    </div>

                    <div style={{ marginTop: 8 }}>Threads {telemetry.threads?.threadCount ?? '-'}</div>

                    {(telemetry.gc || []).map((g) => (
                        <div key={g.name}>
                            {g.name}: {g.collectionCount} runs, {g.collectionTimeMs} ms
                        </div>
                    ))}
                </div>
            )}

            {watch && (
                <div style={card}>
                    <div style={{ fontWeight: 600, color: watchColor, marginBottom: 6 }}>Leak watch: {watch.state}</div>
                    <div>{watch.message}</div>
                    <div style={{ marginTop: 6 }}>Floor samples: {watch.floorSamples}</div>
                    <div>Heap used: {formatBytes(watch.heapUsed)}</div>
                    <div>Floor growth: {Number(watch.growthPercent ?? 0).toFixed(2)}%</div>
                    {watch.dumpId && (
                        <div style={{ marginTop: 6, overflowWrap: 'anywhere' }}>Automatic dump: {watch.dumpId}</div>
                    )}
                </div>
            )}

            {pid && (pools.length > 0 || gcEvents.length > 0) && (
                <LiveMetricsPanel pools={pools} gcEvents={gcEvents} style={card} />
            )}
        </aside>
    )
}