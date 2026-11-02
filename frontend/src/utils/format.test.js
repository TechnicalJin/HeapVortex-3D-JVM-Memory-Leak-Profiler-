import { formatBytes, usagePercent } from './format'

describe('formatBytes', () => {
  it('formats plain bytes', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(512)).toBe('512 B')
  })

  it('formats kilobytes and megabytes', () => {
    expect(formatBytes(1024)).toBe('1.0 KB')
    expect(formatBytes(1536)).toBe('1.5 KB')
    expect(formatBytes(1048576)).toBe('1.0 MB')
  })

  it('returns N/A for undefined or negative values', () => {
    expect(formatBytes(-1)).toBe('N/A')
    expect(formatBytes(undefined)).toBe('N/A')
  })
})

describe('usagePercent', () => {
  it('computes a rounded percentage', () => {
    expect(usagePercent(50, 200)).toBe(25)
  })

  it('returns null when max is unknown', () => {
    expect(usagePercent(10, -1)).toBeNull()
    expect(usagePercent(10, 0)).toBeNull()
  })
})