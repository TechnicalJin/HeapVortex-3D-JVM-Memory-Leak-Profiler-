import { pushSample, sparklinePoints } from './sparkline.js'

describe('sparklinePoints', () => {
    it('maps values to polyline points', () => {
        expect(sparklinePoints([0, 50, 100], 100, 40)).toBe('0.0,40.0 50.0,20.0 100.0,0.0')
    })

    it('returns nothing for fewer than two values', () => {
        expect(sparklinePoints([], 100, 40)).toBe('')
        expect(sparklinePoints([5], 100, 40)).toBe('')
    })

    it('clamps values outside the range', () => {
        expect(sparklinePoints([150, -10], 10, 10)).toBe('0.0,0.0 10.0,10.0')
    })
})

describe('pushSample', () => {
    it('keeps only the newest samples', () => {
        let history = []
        for (let i = 1; i <= 5; i++) history = pushSample(history, i, 3)
        expect(history).toEqual([3, 4, 5])
    })

    it('does not change the old list', () => {
        const old = [1]
        pushSample(old, 2)
        expect(old).toEqual([1])
    })
})