import { render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import App from './App'

vi.mock('./components/HeapScene', () => ({
  default: () => <div data-testid="heap-scene-stub" />,
}))

describe('App', () => {
  it('renders the project title', () => {
    render(<App />)
    expect(screen.getByText('HeapVortex')).toBeInTheDocument()
  })
})