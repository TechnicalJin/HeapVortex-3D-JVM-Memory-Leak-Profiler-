export function formatBytes(bytes) {
  if (bytes === null || bytes === undefined || bytes < 0) return 'N/A'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let unit = 0
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024
    unit++
  }
  return `${value.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`
}

export function usagePercent(used, max) {
  if (!max || max <= 0) return null
  return Math.round((used / max) * 100)
}