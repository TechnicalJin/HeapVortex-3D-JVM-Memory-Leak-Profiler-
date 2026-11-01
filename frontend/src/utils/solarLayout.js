export const ORBIT_SPACING = 16
const GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5))

/**
 * Solar layout: the root sits in the centre, every other node goes on a sphere shell
 * whose radius grows with its reference distance from the root. Nodes on one shell are
 * spread evenly with a Fibonacci sphere. Without a root (the overview) the shells are
 * numbered by the depth that the server sent: top dominators first, their children next.
 * Returns a Map from node id to {x, y, z}.
 */
export function computeTargets(list, edges, rootId) {
    const ids = new Set(list.map((n) => n.id))
    const depth = new Map()
    let centred = false

    if (rootId && ids.has(rootId)) {
        centred = true
        const adjacency = new Map()
        for (const e of edges.values()) {
            if (!ids.has(e.source) || !ids.has(e.target)) continue
            if (!adjacency.has(e.source)) adjacency.set(e.source, [])
            if (!adjacency.has(e.target)) adjacency.set(e.target, [])
            adjacency.get(e.source).push(e.target)
            adjacency.get(e.target).push(e.source)
        }
        depth.set(rootId, 0)
        const queue = [rootId]
        for (let head = 0; head < queue.length; head++) {
            const current = queue[head]
            for (const next of adjacency.get(current) || []) {
                if (!depth.has(next)) {
                    depth.set(next, depth.get(current) + 1)
                    queue.push(next)
                }
            }
        }
        let max = 0
        for (const d of depth.values()) max = Math.max(max, d)
        for (const n of list) if (!depth.has(n.id)) depth.set(n.id, max + 1)
    } else {
        for (const n of list) depth.set(n.id, (n.depth ?? 0) + 1)
    }

    const rings = new Map()
    for (const n of list) {
        const d = depth.get(n.id)
        if (!rings.has(d)) rings.set(d, [])
        rings.get(d).push(n)
    }

    const targets = new Map()
    for (const [d, members] of rings) {
        members.sort((a, b) => b.retainedBytes - a.retainedBytes)
        const count = members.length
        const radius = d * ORBIT_SPACING
        members.forEach((n, i) => {
            if (centred && d === 0) {
                targets.set(n.id, { x: 0, y: 0, z: 0 })
                return
            }
            const y = count === 1 ? 0 : 1 - (2 * (i + 0.5)) / count
            const ring = Math.sqrt(Math.max(0, 1 - y * y))
            const theta = i * GOLDEN_ANGLE
            targets.set(n.id, {
                x: radius * ring * Math.cos(theta),
                y: radius * y,
                z: radius * ring * Math.sin(theta),
            })
        })
    }
    return targets
}