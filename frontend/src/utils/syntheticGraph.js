/** A fake heap graph with a given number of nodes, shaped like the data the backend streams. */
export function generateGraph(count, seed = 42) {
    let state = seed
    const random = () => {
        state = (state * 1664525 + 1013904223) % 4294967296
        return state / 4294967296
    }

    const parent = new Array(count).fill(-1)
    const depth = new Array(count).fill(0)
    const shallow = new Array(count)
    const retained = new Array(count)

    for (let i = 0; i < count; i++) {
        shallow[i] = i === 0 ? 64 : Math.floor(24 + random() * 400)
        retained[i] = shallow[i]
        if (i > 0) {
            // 30 percent of the nodes hang under node 1, which makes one big "red cluster"
            parent[i] = i <= 12 ? 0 : random() < 0.3 ? 1 : Math.floor(random() * i)
            depth[i] = depth[parent[i]] + 1
        }
    }
    for (let i = count - 1; i > 0; i--) retained[parent[i]] += retained[i]

    const id = (i) => `0x${(0x7000 + i).toString(16)}`
    const nodes = new Map()
    const edges = new Map()
    for (let i = 0; i < count; i++) {
        nodes.set(id(i), {
            id: id(i),
            label: i === 0 ? 'com.demo.SessionRegistry' : i <= 12 ? 'java.util.HashMap' : 'com.demo.LeakedSession',
            kind: 'INSTANCE',
            shallowBytes: shallow[i],
            retainedBytes: retained[i],
            retainedPercent: (100 * retained[i]) / retained[0],
            depth: depth[i],
        })
        if (i > 0) {
            edges.set(`${id(parent[i])}|${id(i)}|f`, { source: id(parent[i]), target: id(i), label: 'f' })
        }
    }
    return { nodes, edges, rootId: id(0) }
}