
import { useCallback, useEffect, useRef, useState } from 'react'
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
  const [layout, setLayout] = useState('solar')
  const [watch, setWatch] = useState(null)

  // Prevent repeatedly refreshing the dump list for the same automatic dump.
  const lastRefreshedDumpId = useRef('')

  // --------------------------------------------------
  // Refresh JVM and heap dump lists
  // --------------------------------------------------

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

  // --------------------------------------------------
  // Live JVM telemetry
  // --------------------------------------------------

  const { status, startTelemetry, stopTelemetry } = heap

  useEffect(() => {
    if (status !== 'open') return

    if (pid) {
      startTelemetry(pid)
    } else {
      stopTelemetry()
    }
  }, [pid, status, startTelemetry, stopTelemetry])

  // --------------------------------------------------
  // Manual heap dump
  // --------------------------------------------------

  async function takeDump() {
    if (!pid || busy) return

    setBusy(true)
    setApiError(null)

    try {
      const response = await axios.post(
          `/api/jvms/${pid}/heapdump`,
          null,
          {
            params: { live: true },
          },
      )

      await refresh()

      setDumpId(response.data.id)
      setSuspect(null)
      setSelected(null)
    } catch (e) {
      setApiError(
          `Heap dump failed: ${e.response?.data?.message ?? e.message}`,
      )
    } finally {
      setBusy(false)
    }
  }

  // --------------------------------------------------
  // Leak suspect detection
  // --------------------------------------------------

  async function findSuspect() {
    if (!dumpId || busy) return

    setBusy(true)
    setApiError(null)

    try {
      const response = await axios.get(
          `/api/heapdumps/${dumpId}/dominators/suspects`,
          {
            params: { limit: 1 },
          },
      )

      setSuspect(response.data[0] || null)
    } catch (e) {
      setApiError(
          `Suspect search failed: ${e.response?.data?.message ?? e.message}`,
      )
    } finally {
      setBusy(false)
    }
  }

  // --------------------------------------------------
  // Automatic leak watch
  // --------------------------------------------------

  async function startWatch() {
    if (!pid || watch) return

    setApiError(null)
    lastRefreshedDumpId.current = ''

    try {
      const response = await axios.post(
          `/api/jvms/${pid}/watch`,
          null,
          {
            params: {
              intervalMs: 2000,
              autoDump: true,
            },
          },
      )

      setWatch(response.data)
    } catch (e) {
      setApiError(
          `Could not start the watch: ${e.response?.data?.message ?? e.message}`,
      )
    }
  }

  async function stopWatch() {
    if (!pid) return

    try {
      await axios.delete(`/api/jvms/${pid}/watch`)
      setWatch(null)
      lastRefreshedDumpId.current = ''
    } catch (e) {
      setApiError(
          `Could not stop the watch: ${e.response?.data?.message ?? e.message}`,
      )
    }
  }

  // Poll watch status every 3 seconds.
  // When an automatic dump appears, refresh the dump list and select it.
  useEffect(() => {
    if (!watch || !pid || watch.pid !== pid) return undefined

    let cancelled = false

    const timer = setInterval(async () => {
      try {
        const response = await axios.get(`/api/jvms/${pid}/watch`)

        if (cancelled || !response.data) return

        const updatedWatch = response.data
        setWatch(updatedWatch)

        if (
            updatedWatch.dumpId &&
            updatedWatch.dumpId !== lastRefreshedDumpId.current
        ) {
          lastRefreshedDumpId.current = updatedWatch.dumpId

          await refresh()

          if (!cancelled) {
            setDumpId(updatedWatch.dumpId)
            setSuspect(null)
            setSelected(null)
          }
        }

        // The backend removes a failed watch and reports ERROR.
        if (updatedWatch.state === 'ERROR') {
          setWatch(updatedWatch)
        }
      } catch {
        // A temporary polling failure is retried on the next interval.
      }
    }, 3000)

    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [watch?.pid, pid, refresh])

  // --------------------------------------------------
  // Graph interactions
  // --------------------------------------------------

  function expand(node) {
    if (!dumpId || !node?.id) return

    heap.expand(dumpId, node.id, 2, 400)
  }

  // --------------------------------------------------
  // Telemetry calculations
  // --------------------------------------------------

  const memory = heap.telemetry?.memory

  const heapPercent =
      usagePercent(memory?.heapUsed, memory?.heapMax) ?? 0

  const watchColor =
      watch?.state === 'SUSPECTED'
          ? '#f87171'
          : watch?.state === 'ERROR'
              ? '#fbbf24'
              : '#e2e8f0'

  // --------------------------------------------------
  // UI
  // --------------------------------------------------

  return (
      <div style={{ position: 'fixed', inset: 0 }}>
        <GraphScene
            nodes={heap.nodes}
            edges={heap.edges}
            version={heap.version}
            rootId={heap.rootId}
            layout={layout}
            selectedId={selected?.id}
            onSelect={setSelected}
            onExpand={expand}
        />

        {/* Top-left controls */}
        <div style={{ ...panel, top: 12, left: 12, width: 300 }}>
          <div style={{ fontWeight: 600, marginBottom: 8 }}>
            HeapVortex ({heap.status})
          </div>

          {/* JVM selection and manual heap dump */}
          <div style={{ marginBottom: 6 }}>
            <select
                value={pid}
                onChange={(e) => {
                  setPid(e.target.value)
                  setWatch(null)
                  lastRefreshedDumpId.current = ''
                }}
                style={{ width: '100%' }}
            >
              <option value="">Choose a JVM</option>

              {jvms.map((j) => (
                  <option key={j.pid} value={j.pid}>
                    {j.pid} {String(j.displayName || '').slice(0, 40)}
                  </option>
              ))}
            </select>

            <button
                disabled={!pid || busy}
                onClick={takeDump}
                style={{ marginTop: 4 }}
            >
              {busy ? 'Processing...' : 'Take heap dump'}
            </button>

            <button
                disabled={!pid}
                onClick={watch ? stopWatch : startWatch}
                style={{ marginTop: 4, marginLeft: 4 }}
            >
              {watch ? 'Stop leak watch' : 'Start leak watch'}
            </button>
          </div>

          {/* Heap dump selection */}
          <div style={{ marginBottom: 6 }}>
            <select
                value={dumpId}
                onChange={(e) => setDumpId(e.target.value)}
                style={{ width: '100%' }}
            >
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

            <button
                disabled={!dumpId || busy}
                onClick={findSuspect}
                style={{ marginTop: 4 }}
            >
              Find leak suspect
            </button>
          </div>

          {/* Layout toggle */}
          <div style={{ marginBottom: 6 }}>
            Layout:{' '}
            <button
                onClick={() =>
                    setLayout((current) =>
                        current === 'solar' ? 'force' : 'solar',
                    )
                }
            >
              {layout === 'solar' ? 'Solar system' : 'Force graph'} (switch)
            </button>
          </div>

          {heap.loading && <div>Loading graph...</div>}

          {heap.truncated && (
              <div style={{ color: '#fbbf24' }}>
                Graph was capped. Expand a single node to look deeper.
              </div>
          )}

          {(heap.error || apiError) && (
              <div style={{ color: '#f87171', overflowWrap: 'anywhere' }}>
                {heap.error || apiError}
              </div>
          )}

          <div style={{ opacity: 0.7, marginTop: 6 }}>
            Click a sphere to select it, double-click to expand it.
          </div>
        </div>

        {/* Live JVM telemetry */}
        {heap.telemetry && (
            <div style={{ ...panel, top: 12, right: 12, width: 240 }}>
              <div style={{ fontWeight: 600, marginBottom: 6 }}>
                Live JVM {heap.telemetry.pid}
              </div>

              <div>
                Heap {formatBytes(memory?.heapUsed)} /{' '}
                {formatBytes(memory?.heapMax)}
              </div>

              <div
                  style={{
                    background: '#334155',
                    height: 8,
                    borderRadius: 4,
                    marginTop: 4,
                  }}
              >
                <div
                    style={{
                      width: `${Math.min(100, Math.max(0, heapPercent))}%`,
                      height: 8,
                      borderRadius: 4,
                      background: heapPercent > 80 ? '#ef4444' : '#3b82f6',
                    }}
                />
              </div>

              <div style={{ marginTop: 6 }}>
                Threads {heap.telemetry.threads?.threadCount ?? '-'}
              </div>

              {(heap.telemetry.gc || []).map((g) => (
                  <div key={g.name}>
                    {g.name}: {g.collectionCount} runs,{' '}
                    {g.collectionTimeMs} ms
                  </div>
              ))}
            </div>
        )}

        {/* Automatic leak-watch status */}
        {watch && (
            <div style={{ ...panel, top: 200, right: 12, width: 240 }}>
              <div
                  style={{
                    fontWeight: 600,
                    color: watchColor,
                    marginBottom: 6,
                  }}
              >
                Leak watch: {watch.state}
              </div>

              <div>{watch.message}</div>

              <div style={{ marginTop: 6 }}>
                Floor samples: {watch.floorSamples}
              </div>

              <div>
                Heap used: {formatBytes(watch.heapUsed)}
              </div>

              <div>
                Floor growth: {Number(watch.growthPercent ?? 0).toFixed(2)}%
              </div>

              {watch.dumpId && (
                  <div style={{ marginTop: 6, overflowWrap: 'anywhere' }}>
                    Automatic dump: {watch.dumpId}
                  </div>
              )}
            </div>
        )}

        {/* Leak suspect details */}
        {suspect && (
            <div style={{ ...panel, bottom: 12, left: 12, width: 380 }}>
              <div style={{ fontWeight: 600, color: '#f87171' }}>
                Leak suspect: {suspect.likelyLeakClass}
              </div>

              <div>
                {suspect.accumulationClassName} retains{' '}
                {formatBytes(suspect.accumulationRetainedBytes)} (
                {Number(suspect.heapPercent ?? 0).toFixed(1)}% of the heap
                through {suspect.topClassName})
              </div>

              <button
                  style={{ marginTop: 6 }}
                  onClick={() =>
                      heap.expand(
                          dumpId,
                          suspect.accumulationObjectId,
                          2,
                          400,
                      )
                  }
              >
                Show it in 3D
              </button>
            </div>
        )}

        {/* Selected object details */}
        {selected && (
            <div
                style={{
                  ...panel,
                  bottom: 12,
                  right: 12,
                  width: 320,
                  overflowWrap: 'anywhere',
                }}
            >
              <div style={{ fontWeight: 600, wordBreak: 'break-all' }}>
                {selected.label}
              </div>

              <div>Kind: {selected.kind}</div>
              <div>Object ID: {selected.id}</div>
              <div>Shallow: {formatBytes(selected.shallowBytes)}</div>

              <div>
                Retained: {formatBytes(selected.retainedBytes)} (
                {Number(selected.retainedPercent ?? 0).toFixed(2)}%)
              </div>

              <button
                  style={{ marginTop: 6 }}
                  onClick={() => expand(selected)}
              >
                Expand
              </button>
            </div>
        )}

        {/* Graph legend */}
        <div
            style={{
              ...panel,
              bottom: 12,
              left: '50%',
              transform: 'translateX(-50%)',
              padding: '6px 12px',
              maxWidth: 'min(520px, 90vw)',
              textAlign: 'center',
              boxSizing: 'border-box',
            }}
        >
          Sphere size = retained bytes. Colour: blue = small share of
          the heap, red = large share. Centre = expanded object, outer
          shells = further references.
        </div>
      </div>
  )
}
