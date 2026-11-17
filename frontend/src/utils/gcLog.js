export const MAX_GC_EVENTS = 50

/** Newest event first, the list never grows beyond max. */
export function appendGcEvent(events, event, max = MAX_GC_EVENTS) {
    return [event, ...events].slice(0, max)
}

export function summarizeGc(events) {
    let totalPauseMs = 0
    let maxPauseMs = 0
    let reclaimedBytes = 0
    for (const e of events) {
        totalPauseMs += e.durationMs
        maxPauseMs = Math.max(maxPauseMs, e.durationMs)
        reclaimedBytes += e.reclaimedBytes
    }
    return { count: events.length, totalPauseMs, maxPauseMs, reclaimedBytes }
}