import { appendGcEvent, summarizeGc } from './gcLog.js'

const event = (id, durationMs = 10, reclaimedBytes = 100) => ({ id, durationMs, reclaimedBytes })

describe('gcLog', () => {
    it('puts the newest event first', () => {
        const list = appendGcEvent(appendGcEvent([], event(1)), event(2))
        expect(list.map((e) => e.id)).toEqual([2, 1])
    })

    it('keeps at most the given number of events', () => {
        let list = []
        for (let i = 0; i < 10; i++) list = appendGcEvent(list, event(i), 4)
        expect(list).toHaveLength(4)
        expect(list[0].id).toBe(9)
    })

    it('sums pauses and reclaimed bytes', () => {
        const s = summarizeGc([event(1, 5, 100), event(2, 20, 300)])
        expect(s).toEqual({ count: 2, totalPauseMs: 25, maxPauseMs: 20, reclaimedBytes: 400 })
    })

    it('handles an empty list', () => {
        expect(summarizeGc([])).toEqual({ count: 0, totalPauseMs: 0, maxPauseMs: 0, reclaimedBytes: 0 })
    })
})