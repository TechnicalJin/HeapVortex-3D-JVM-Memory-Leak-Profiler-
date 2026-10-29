import { useCallback, useEffect, useState } from 'react'
import axios from 'axios'
import GraphScene from './components/GraphScene.jsx'
import { useHeapSocket } from './hooks/useHeapSocket.js'
import { formatBytes, usagePercent } from './utils/format.js'

const panel = {
  position: 'absolute',
  background: 'rgba(15, 23, 42, 0.85)',
  color: '#e2e8f0',
  padding: 12,
  borderRadius: 8,
  fontFamily: 'system-ui, sans-serif',
  fontSize: 13,
}

export default function App() {
  const heap = useHeapSocket()
  const [jvms, setJvms] = useState([])
  const [dumps, setDumps] = useState([])
  const [pid, setPid] = useState('')
  const [dumpId, setDumpId] = useState('')
  const [selected, setSelected] = useState(null)
  const [suspect, setSuspect] = useState(null)
  const [busy, setBusy] = useState(false)
  const [apiError, setApiError] = useState(null)

  const refresh = useCallback(async () => {
    try {
      const [j, d] = await Promise.all([
        axios.get('/api/jvms'),
        axios.get('/api/heapdumps'),
      ])
      setJvms(j.data)
      setDumps(d.data)
      setDumpId((current) => current || d.data[0]?.id || '')
      setApiError(null)
    } catch (e) {
      setApiError(`Backend not reachable: ${e.message}`)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  // live telemetry for the chosen JVM
  const { status, startTelemetry, stopTelemetry } = heap
  useEffect(() => {
    if (status !== 'open') return
    if (pid) startTelemetry(pid)
    else stopTelemetry()
  }, [pid, status, startTelemetry, stopTelemetry])

  async function takeDump() {
    setBusy(true)
    try {
      const response = await axios.post(`/api/jvms/${pid}/heapdump`, null, {
        params: { live: true },
      })
      await refresh()
      setDumpId(response.data.id)
    } catch (e) {
      setApiError(`Heap dump failed: ${e.response?.data?.message ?? e.message}`)
    } finally {
      setBusy(false)
    }
  }

  async function findSuspect() {
    setBusy(true)
    try {
      const response = await axios.get(`/api/heapdumps/${dumpId}/dominators/suspects`, {
        params: { limit: 1 },
      })
      setSuspect(response.data[0] || null)
    } catch (e) {
      setApiError(`Suspect search failed: ${e.response?.data?.message ?? e.message}`)
    } finally {
      setBusy(false)
    }
  }

  function expand(node) {
    heap.expand(dumpId, node.id, 2, 400)
  }

  const memory = heap.telemetry?.memory
  const heapPercent = usagePercent(memory?.heapUsed, memory?.heapMax) ?? 0

  return (
      <div style={{ position: 'fixed', inset: 0 }}>
        <GraphScene
            nodes={heap.nodes}
            edges={heap.edges}
            version={heap.version}
            selectedId={selected?.id}
            onSelect={setSelected}
            onExpand={expand}
        />

        <div style={{ ...panel, top: 12, left: 12, width: 300 }}>
          <div style={{ fontWeight: 600, marginBottom: 8 }}>HeapVortex ({heap.status})</div>

          <div style={{ marginBottom: 6 }}>
            <select value={pid} onChange={(e) => setPid(e.target.value)} style={{ width: '100%' }}>
              <option value="">Choose a JVM</option>
              {jvms.map((j) => (
                  <option key={j.pid} value={j.pid}>
                    {j.pid} {String(j.displayName || '').slice(0, 40)}
                  </option>
              ))}
            </select>
            <button disabled={!pid || busy} onClick={takeDump} style={{ marginTop: 4 }}>
              Take heap dump
            </button>
          </div>

          <div style={{ marginBottom: 6 }}>
            <select value={dumpId} onChange={(e) => setDumpId(e.target.value)} style={{ width: '100%' }}>
              <option value="">Choose a dump</option>
              {dumps.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.id}
                  </option>
              ))}
            </select>
            <button
                disabled={!dumpId || heap.loading}
                onClick={() => heap.overview(dumpId)}
                style={{ marginTop: 4 }}
            >
              Load overview
            </button>{' '}
            <button disabled={!dumpId || busy} onClick={findSuspect} style={{ marginTop: 4 }}>
              Find leak suspect
            </button>
          </div>

          {heap.loading && <div>Loading graph...</div>}
          {heap.truncated && (
              <div style={{ color: '#fbbf24' }}>Graph was capped. Expand a single node to look deeper.</div>
          )}
          {(heap.error || apiError) && (
              <div style={{ color: '#f87171' }}>{heap.error || apiError}</div>
          )}
          <div style={{ opacity: 0.7, marginTop: 6 }}>
            Click a sphere to select it, double-click to expand it.
          </div>
        </div>

        {heap.telemetry && (
            <div style={{ ...panel, top: 12, right: 12, width: 240 }}>
              <div style={{ fontWeight: 600, marginBottom: 6 }}>Live JVM {heap.telemetry.pid}</div>
              <div>
                Heap {formatBytes(memory?.heapUsed)} / {formatBytes(memory?.heapMax)}
              </div>
              <div style={{ background: '#334155', height: 8, borderRadius: 4, marginTop: 4 }}>
                <div
                    style={{
                      width: `${Math.min(100, heapPercent)}%`,
                      height: 8,
                      borderRadius: 4,
                      background: heapPercent > 80 ? '#ef4444' : '#3b82f6',
                    }}
                />
              </div>
              <div style={{ marginTop: 6 }}>Threads {heap.telemetry.threads?.threadCount ?? '-'}</div>
              {(heap.telemetry.gc || []).map((g) => (
                  <div key={g.name}>
                    {g.name}: {g.collectionCount} runs, {g.collectionTimeMs} ms
                  </div>
              ))}
            </div>
        )}

        {suspect && (
            <div style={{ ...panel, bottom: 12, left: 12, width: 380 }}>
              <div style={{ fontWeight: 600, color: '#f87171' }}>Leak suspect: {suspect.likelyLeakClass}</div>
              <div>
                {suspect.accumulationClassName} retains {formatBytes(suspect.accumulationRetainedBytes)} (
                {suspect.heapPercent.toFixed(1)}% of the heap through {suspect.topClassName})
              </div>
              <button
                  style={{ marginTop: 6 }}
                  onClick={() => heap.expand(dumpId, suspect.accumulationObjectId, 2, 400)}
              >
                Show it in 3D
              </button>
            </div>
        )}

        {selected && (
            <div style={{ ...panel, bottom: 12, right: 12, width: 320 }}>
              <div style={{ fontWeight: 600, wordBreak: 'break-all' }}>{selected.label}</div>
              <div>Kind: {selected.kind}</div>
              <div>Object id: {selected.id}</div>
              <div>Shallow: {formatBytes(selected.shallowBytes)}</div>
              <div>
                Retained: {formatBytes(selected.retainedBytes)} ({selected.retainedPercent.toFixed(2)}%)
              </div>
              <button style={{ marginTop: 6 }} onClick={() => expand(selected)}>
                Expand
              </button>
            </div>
        )}
      </div>
  )
}