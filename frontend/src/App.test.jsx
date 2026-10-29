import { render, screen } from '@testing-library/react'
import axios from 'axios'
import App from './App.jsx'
import { mockJvms } from './mocks/mockData.js'

vi.mock('axios', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

vi.mock('./components/GraphScene.jsx', () => ({
  default: () => <div data-testid="graph-scene" />,
}))

vi.mock('./hooks/useHeapSocket.js', async () => {
  const { mockMetrics } = await import('./mocks/mockData.js')
  return {
    useHeapSocket: () => ({
      nodes: new Map(),
      edges: new Map(),
      version: 0,
      status: 'open',
      loading: false,
      truncated: false,
      error: null,
      telemetry: mockMetrics('1001'),
      overview: vi.fn(),
      expand: vi.fn(),
      startTelemetry: vi.fn(),
      stopTelemetry: vi.fn(),
    }),
  }
})

describe('App', () => {
  beforeEach(() => {
    axios.get.mockImplementation((url) =>
        Promise.resolve({ data: url === '/api/jvms' ? mockJvms : [] })
    )
  })

  it('shows the title and the 3D scene', async () => {
    render(<App />)
    expect(screen.getByText(/HeapVortex/)).toBeInTheDocument()
    expect(screen.getByTestId('graph-scene')).toBeInTheDocument()
    await screen.findByText(/1001 LeakyApp/)
  })

  it('lists the JVMs returned by the backend', async () => {
    render(<App />)
    expect(await screen.findByText(/1002 com.example.OrderService/)).toBeInTheDocument()
  })

  it('shows live telemetry with formatted bytes and GC time', async () => {
    render(<App />)
    expect(screen.getByText(/Live JVM 1001/)).toBeInTheDocument()
    expect(screen.getByText(/512\.0 MB/)).toBeInTheDocument()
    expect(screen.getByText(/G1 Young Generation: 15 runs, 82 ms/)).toBeInTheDocument()
    await screen.findByText(/1001 LeakyApp/)
  })
})