export const HISTORY_LENGTH = 60

/** Points for an SVG polyline: values run from the left to the right edge, 0 at the bottom and max at the top. */
export function sparklinePoints(values, width, height, max = 100) {
    if (!values || values.length < 2) return ''
    const step = width / (values.length - 1)
    return values
        .map((value, i) => {
            const clamped = Math.max(0, Math.min(value, max))
            const x = i * step
            const y = height - (clamped / max) * height
            return `${x.toFixed(1)},${y.toFixed(1)}`
        })
        .join(' ')
}

/** Appends a sample and keeps only the newest ones. */
export function pushSample(history, value, max = HISTORY_LENGTH) {
    const next = [...history, value]
    return next.length > max ? next.slice(next.length - max) : next
}