import { mockJvms, mockMetrics } from './mockData'

describe('mock data matches the API contract', () => {
  it('has pid and displayName for every JVM', () => {
    mockJvms.forEach((jvm) => {
      expect(typeof jvm.pid).toBe('string')
      expect(typeof jvm.displayName).toBe('string')
    })
  })

  it('has the memory, threads and gc fields of JvmMetricsDto', () => {
    const m = mockMetrics('1001')
    expect(m.pid).toBe('1001')
    expect(Object.keys(m.memory)).toEqual([
      'heapUsed', 'heapCommitted', 'heapMax', 'nonHeapUsed', 'nonHeapCommitted',
    ])
    expect(Object.keys(m.threads)).toEqual([
      'threadCount', 'peakThreadCount', 'daemonThreadCount', 'totalStartedThreadCount',
    ])
    expect(m.gc[0]).toHaveProperty('collectionTimeMs')
  })

  it('keeps heapUsed below heapMax', () => {
    const m = mockMetrics('1001')
    expect(m.memory.heapUsed).toBeLessThan(m.memory.heapMax)
  })
})