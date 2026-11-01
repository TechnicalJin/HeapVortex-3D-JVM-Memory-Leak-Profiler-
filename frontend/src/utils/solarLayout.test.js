import { computeTargets, ORBIT_SPACING } from './solarLayout.js'

function node(id, retainedBytes = 100, depth = 0) {
    return { id, retainedBytes, depth }
}

function edgeMap(...pairs) {
    const map = new Map()
    for (const [source, target] of pairs) {
        map.set(`${source}|${target}|f`, { source, target, label: 'f' })
    }
    return map
}

function distance(p) {
    return Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z)
}

describe('computeTargets', () => {
    it('puts the root in the centre and each depth on its own shell', () => {
        const list = [node('r'), node('a'), node('b'), node('c')]
        const edges = edgeMap(['r', 'a'], ['r', 'b'], ['a', 'c'])

        const t = computeTargets(list, edges, 'r')

        expect(t.get('r')).toEqual({ x: 0, y: 0, z: 0 })
        expect(distance(t.get('a'))).toBeCloseTo(ORBIT_SPACING, 5)
        expect(distance(t.get('b'))).toBeCloseTo(ORBIT_SPACING, 5)
        expect(distance(t.get('c'))).toBeCloseTo(2 * ORBIT_SPACING, 5)
    })

    it('measures depth over edges in both directions', () => {
        // the only edge points from a to the root, a is still one step away
        const list = [node('r'), node('a')]
        const t = computeTargets(list, edgeMap(['a', 'r']), 'r')

        expect(distance(t.get('a'))).toBeCloseTo(ORBIT_SPACING, 5)
    })

    it('places a node without any connection on the outermost shell', () => {
        const list = [node('r'), node('a'), node('lost')]
        const t = computeTargets(list, edgeMap(['r', 'a']), 'r')

        expect(distance(t.get('lost'))).toBeCloseTo(2 * ORBIT_SPACING, 5)
    })

    it('uses the depth sent by the server when there is no root', () => {
        const list = [node('top', 100, 0), node('child', 10, 1)]
        const t = computeTargets(list, edgeMap(['top', 'child']), null)

        expect(distance(t.get('top'))).toBeCloseTo(ORBIT_SPACING, 5)
        expect(distance(t.get('child'))).toBeCloseTo(2 * ORBIT_SPACING, 5)
    })

    it('falls back to the server depth when the root is not in the graph', () => {
        const list = [node('top', 100, 0)]
        const t = computeTargets(list, edgeMap(), 'missing')

        expect(distance(t.get('top'))).toBeCloseTo(ORBIT_SPACING, 5)
    })

    it('gives every node on a shell a different position', () => {
        const list = [node('r'), ...Array.from({ length: 20 }, (_, i) => node(`n${i}`))]
        const edges = edgeMap(...list.slice(1).map((n) => ['r', n.id]))

        const t = computeTargets(list, edges, 'r')

        const keys = new Set(
            list.slice(1).map((n) => {
                const p = t.get(n.id)
                return `${p.x.toFixed(3)},${p.y.toFixed(3)},${p.z.toFixed(3)}`
            })
        )
        expect(keys.size).toBe(20)
    })

    it('gives the biggest object of a shell the first slot, at the top', () => {
        const list = [node('r'), node('small', 10), node('big', 1000)]
        const edges = edgeMap(['r', 'small'], ['r', 'big'])

        const t = computeTargets(list, edges, 'r')

        expect(t.get('big').y).toBeGreaterThan(t.get('small').y)
    })

    it('returns an empty map for an empty graph', () => {
        expect(computeTargets([], edgeMap(), null).size).toBe(0)
    })
})