import { useCallback, useEffect, useRef, useState } from 'react'
import axios from 'axios'
import GraphScene from './components/GraphScene.jsx'
import MetricsSidebar from './components/MetricsSidebar.jsx'
import { useHeapSocket } from './hooks/useHeapSocket.js'
import { formatBytes } from './utils/format.js'
import { splitClassName } from './utils/classNames.js'
import { generateGraph } from './utils/syntheticGraph.js'

const panel = {
  position: 'absolute',
  background: 'rgba(15, 23, 42, 0.85)',
  color: '#e2e8f0',
  padding: 12,
  borderRadius: 8,
  fontFamily: 'system-ui, sans-serif',
  fontSize: 13,
}

const REMOTE_PREFIX = 'remote:'

export default function App() {
  const heap = useHeapSocket()

  const [jvms, setJvms] = useState([])
  const [remoteJvms, setRemoteJvms] = useState([])
  const [dumps, setDumps] = useState([])
  const [pid, setPid] = useState('')
  const [dumpId, setDumpId] = useState('')
  const [selected, setSelected] = useState(null)
  const [suspect, setSuspect] = useState(null)
  const [busy, setBusy] = useState(false)
  const [apiError, setApiError] = useState(null)
  const [layout, setLayout] = useState('solar')
  const [watch, setWatch] = useState(null)
  const [stress, setStress] = useState(null)
  const [sidebarOpen, setSidebarOpen] = useState(true)

  // Prevent repeatedly refreshing the dump list for the same automatic dump.
  const lastRefreshedDumpId = useRef('')

  // A secure remote JVM gives live metrics only: the heap dump would be written on the remote machine.
  const isRemote = pid.startsWith(REMOTE_PREFIX)

  // --------------------------------------------------
  // Refresh JVM and heap dump lists
  // --------------------------------------------------

  const refresh = useCallback(async () => {
    try {
      const [j, d, r] = await Promise.all([
        axios.get('/api/jvms'),
        axios.get('/api/heapdumps'),
        axios.get('/api/remote-jvms').catch(() => ({ data: [] })),
      ])

      setJvms(j.data)
      setDumps(d.data)
      setRemoteJvms(r.data)

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
  // Live JVM telemetry, memory pools and GC events
  // --------------------------------------------------

  const { status, startTelemetry, stopTelemetry, startGcEvents, stopGcEvents } = heap

  useEffect(() => {
    if (status !== 'open') return

    if (pid) {
      startTelemetry(pid)
      startGcEvents(pid)
    } else {
      stopTelemetry()
      stopGcEvents()
    }
  }, [pid, status, startTelemetry, stopTelemetry, startGcEvents, stopGcEvents])

  // --------------------------------------------------
  // Manual heap dump
  // --------------------------------------------------

  async function takeDump() {
    if (!pid || busy || isRemote) return

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
    if (!pid || watch || isRemote) return

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

  // The static reference nearest to the leaked object is the real holder.
  // Example: class LeakyApp through static SESSIONS (not the JDK launcher further up the path).
  const holderPath = suspect?.pathToGcRoot?.path ?? []
  let holderIndex = holderPath.length - 1
  while (
      holderIndex > 0 &&
      !String(holderPath[holderIndex].referenceFromPrevious || '').startsWith('static ')
      ) {
    holderIndex--
  }

  const selectedClass = selected ? splitClassName(selected.label) : null

  // --------------------------------------------------
  // UI
  // --------------------------------------------------

  const view = stress ?? {
    nodes: heap.nodes,
    edges: heap.edges,
    rootId: heap.rootId,
    version: heap.version,
  }

  return (
      <div style={{ position: 'fixed', inset: 0, display: 'flex' }}>
        {/* 3D view with its floating panels */}
        <div style={{ position: 'relative', flex: 1, minWidth: 0 }}>
          <div style={{ position: 'absolute', inset: 0 }}>
            <GraphScene
                nodes={view.nodes}
                edges={view.edges}
                version={view.version}
                rootId={view.rootId}
                layout={layout}
                instanced={Boolean(stress)}
                selectedId={selected?.id}
                onSelect={setSelected}
                onExpand={expand}
            />
          </div>

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

                <optgroup label="Local JVMs">
                  {jvms.map((j) => (
                      <option key={j.pid} value={j.pid}>
                        {j.pid} {String(j.displayName || '').slice(0, 40)}
                      </option>
                  ))}
                </optgroup>

                {remoteJvms.length > 0 && (
                    <optgroup label="Secure remote JMX (TLS)">
                      {remoteJvms.map((j) => (
                          <option key={j.pid} value={j.pid}>
                            {j.displayName}
                          </option>
                      ))}
                    </optgroup>
                )}
              </select>

              <button
                  disabled={!pid || busy || isRemote}
                  onClick={takeDump}
                  style={{ marginTop: 4 }}
              >
                {busy ? 'Processing...' : 'Take heap dump'}
              </button>

              <button
                  disabled={!pid || isRemote}
                  onClick={watch ? stopWatch : startWatch}
                  style={{ marginTop: 4, marginLeft: 4 }}
              >
                {watch ? 'Stop leak watch' : 'Start leak watch'}
              </button>

              <button
                  onClick={refresh}
                  style={{ marginTop: 4, marginLeft: 4 }}
              >
                Refresh lists
              </button>

              {isRemote && (
                  <div style={{ color: '#fbbf24', marginTop: 4 }}>
                    Secure remote JVM: live metrics only, no heap dump or leak watch.
                  </div>
              )}
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
                  onClick={() => { setStress(null); heap.overview(dumpId) }}
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

            {/* Synthetic graph stress tests */}
            <div style={{ marginBottom: 6 }}>
              Stress test:{' '}
              {[10000, 20000].map((n) => (
                  <button
                      key={n}
                      onClick={() =>
                          setStress({ ...generateGraph(n), version: Date.now() })
                      }
                  >
                    {n / 1000}k
                  </button>
              ))}{' '}
              {stress && (
                  <button onClick={() => setStress(null)}>Exit</button>
              )}
            </div>

            {/* Layout toggle and sidebar toggle */}
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
              </button>{' '}
              <button onClick={() => setSidebarOpen((open) => !open)}>
                {sidebarOpen ? 'Hide metrics' : 'Show metrics'}
              </button>
            </div>

            {/* Rendered graph size, used as evidence for the rendering check */}
            <div style={{ marginBottom: 6 }}>
              Nodes {view.nodes.size} / Edges {view.edges.size}
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
              Hover a sphere for its class and memory, click to select it,
              double-click to expand it.
            </div>
          </div>

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

                {holderIndex > 0 && (
                    <div>
                      Held by {holderPath[holderIndex - 1].typeName} through{' '}
                      {holderPath[holderIndex].referenceFromPrevious}
                    </div>
                )}

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

                <button
                    style={{ marginTop: 6, marginLeft: 4 }}
                    onClick={() => {
                      setStress(null)
                      heap.expand(
                          dumpId,
                          suspect.accumulationObjectId,
                          6,
                          10000,
                      )
                    }}
                >
                  Load up to 10,000 real nodes
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
                  {selectedClass.name}
                </div>

                {selectedClass.pkg && (
                    <div style={{ opacity: 0.7 }}>{selectedClass.pkg}</div>
                )}

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

        {/* 2D metrics sidebar */}
        {sidebarOpen && (
            <MetricsSidebar
                pid={pid}
                telemetry={heap.telemetry}
                cpu={heap.cpu}
                heapHistory={heap.heapHistory}
                cpuHistory={heap.cpuHistory}
                pools={heap.pools}
                gcEvents={heap.gcEvents}
                watch={watch}
            />
        )}
      </div>
  )
}