import { generateGraph } from './syntheticGraph.js'

describe('generateGraph', () => {
    it('creates the requested number of nodes and a tree of edges', () => {
        const { nodes, edges } = generateGraph(1000)
        expect(nodes.size).toBe(1000)
        expect(edges.size).toBe(999)
    })

    it('lets the root retain everything', () => {
        const { nodes, rootId } = generateGraph(500)
        const total = [...nodes.values()].reduce((sum, n) => sum + n.shallowBytes, 0)
        expect(nodes.get(rootId).retainedBytes).toBe(total)
        expect(nodes.get(rootId).retainedPercent).toBeCloseTo(100, 5)
    })

    it('gives the same graph for the same seed', () => {
        const a = generateGraph(200, 7)
        const b = generateGraph(200, 7)
        expect([...a.edges.keys()]).toEqual([...b.edges.keys()])
    })
})