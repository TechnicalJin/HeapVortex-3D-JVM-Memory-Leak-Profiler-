import { formatBytes } from '../utils/format.js'
import { summarizeGc } from '../utils/gcLog.js'

const MAX_LISTED_EVENTS = 8

function poolPercent(pool) {
    const limit = pool.max > 0 ? pool.max : pool.committed
    return limit > 0 ? Math.min(100, (100 * pool.used) / limit) : 0
}

export default function LiveMetricsPanel({ pools, gcEvents, style }) {
    const summary = summarizeGc(gcEvents)

    return (
        <div style={style}>
            <div style={{ fontWeight: 600, marginBottom: 6 }}>JMX memory pools</div>
            {pools.length === 0 && <div style={{ opacity: 0.7 }}>Waiting for data...</div>}
            {pools.map((pool) => (
                <div key={pool.name} style={{ marginBottom: 5 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 6 }}>
                        <span>{pool.name}</span>
                        <span style={{ opacity: 0.8 }}>
              {formatBytes(pool.used)} / {pool.max > 0 ? formatBytes(pool.max) : formatBytes(pool.committed)}
            </span>
                    </div>
                    <div style={{ background: '#334155', height: 5, borderRadius: 3, marginTop: 2 }}>
                        <div
                            style={{
                                width: `${poolPercent(pool)}%`,
                                height: 5,
                                borderRadius: 3,
                                background: pool.type === 'HEAP' ? '#3b82f6' : '#94a3b8',
                            }}
                        />
                    </div>
                </div>
            ))}

            <div style={{ fontWeight: 600, margin: '10px 0 4px' }}>GC events (live)</div>
            {gcEvents.length === 0 ? (
                <div style={{ opacity: 0.7 }}>No collection yet</div>
            ) : (
                <>
                    <div style={{ opacity: 0.8, marginBottom: 4 }}>
                        {summary.count} GCs, pause total {summary.totalPauseMs} ms, longest {summary.maxPauseMs} ms,
                        reclaimed {formatBytes(summary.reclaimedBytes)}
                    </div>
                    {gcEvents.slice(0, MAX_LISTED_EVENTS).map((e, i) => (
                        <div key={`${e.timestamp}-${i}`} style={{ marginBottom: 3, overflowWrap: 'anywhere' }}>
                            <span style={{ opacity: 0.7 }}>{new Date(e.timestamp).toLocaleTimeString()}</span>{' '}
                            {e.gcName}: {e.durationMs} ms, {e.gcCause}, freed {formatBytes(e.reclaimedBytes)}
                        </div>
                    ))}
                </>
            )}
        </div>
    )
}