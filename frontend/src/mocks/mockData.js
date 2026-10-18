export const mockJvms = [
  { pid: '1001', displayName: 'LeakyApp' },
  { pid: '1002', displayName: 'com.example.OrderService' },
]

export function mockMetrics(pid) {
  const heapUsed = 150_000_000 + Math.floor(Math.random() * 50_000_000)
  return {
    pid,
    timestamp: new Date().toISOString(),
    memory: {
      heapUsed,
      heapCommitted: 268_435_456,
      heapMax: 536_870_912,
      nonHeapUsed: 52_428_800,
      nonHeapCommitted: 67_108_864,
    },
    threads: {
      threadCount: 24,
      peakThreadCount: 31,
      daemonThreadCount: 18,
      totalStartedThreadCount: 120,
    },
    gc: [
      { name: 'G1 Young Generation', collectionCount: 15, collectionTimeMs: 82 },
      { name: 'G1 Old Generation', collectionCount: 1, collectionTimeMs: 40 },
    ],
  }
}