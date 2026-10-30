import { useEffect, useMemo, useRef, useState } from "react";
import { Canvas, useFrame } from "@react-three/fiber";
import { OrbitControls, Html } from "@react-three/drei";
import * as THREE from "three";
import { forceSimulation, forceManyBody, forceLink, forceCenter } from "d3-force-3d";

const COLD = new THREE.Color("#3b82f6");
const HOT = new THREE.Color("#ef4444");
const GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
const ORBIT_SPACING = 16;

/** Blue for small, red for objects that retain a big share of the heap. */
function heat(percent) {
    const t = Math.min(1, Math.sqrt(Math.max(percent, 0) / 25));
    return COLD.clone().lerp(HOT, t);
}

function shortName(label) {
    const clean = String(label || "").replace(/^class /, "");
    const last = clean.split(".").pop();
    return last || clean;
}

/**
 * Solar layout: the root sits in the centre, every other node goes on a sphere shell
 * whose radius grows with its reference distance from the root. Nodes on one shell are
 * spread evenly with a Fibonacci sphere. Without a root (the overview) the shells are
 * numbered by the depth that the server sent: top dominators first, their children next.
 */
function computeTargets(list, edges, rootId) {
    const ids = new Set(list.map((n) => n.id));
    const depth = new Map();
    let centred = false;

    if (rootId && ids.has(rootId)) {
        centred = true;
        const adjacency = new Map();
        for (const e of edges.values()) {
            if (!ids.has(e.source) || !ids.has(e.target)) continue;
            if (!adjacency.has(e.source)) adjacency.set(e.source, []);
            if (!adjacency.has(e.target)) adjacency.set(e.target, []);
            adjacency.get(e.source).push(e.target);
            adjacency.get(e.target).push(e.source);
        }
        depth.set(rootId, 0);
        const queue = [rootId];
        for (let head = 0; head < queue.length; head++) {
            const current = queue[head];
            for (const next of adjacency.get(current) || []) {
                if (!depth.has(next)) {
                    depth.set(next, depth.get(current) + 1);
                    queue.push(next);
                }
            }
        }
        let max = 0;
        for (const d of depth.values()) max = Math.max(max, d);
        for (const n of list) if (!depth.has(n.id)) depth.set(n.id, max + 1);
    } else {
        for (const n of list) depth.set(n.id, (n.depth ?? 0) + 1);
    }

    const rings = new Map();
    for (const n of list) {
        const d = depth.get(n.id);
        if (!rings.has(d)) rings.set(d, []);
        rings.get(d).push(n);
    }

    const targets = new Map();
    for (const [d, members] of rings) {
        members.sort((a, b) => b.retainedBytes - a.retainedBytes);
        const count = members.length;
        const radius = d * ORBIT_SPACING;
        members.forEach((n, i) => {
            if (centred && d === 0) {
                targets.set(n.id, { x: 0, y: 0, z: 0 });
                return;
            }
            const y = count === 1 ? 0 : 1 - (2 * (i + 0.5)) / count;
            const ring = Math.sqrt(Math.max(0, 1 - y * y));
            const theta = i * GOLDEN_ANGLE;
            targets.set(n.id, {
                x: radius * ring * Math.cos(theta),
                y: radius * y,
                z: radius * ring * Math.sin(theta),
            });
        });
    }
    return targets;
}

function Graph({ nodes, edges, version, rootId, layout, selectedId, onSelect, onExpand }) {
    const sim = useRef(null);
    const simNodes = useRef(new Map());
    const links = useRef([]);
    const targets = useRef(new Map());
    const linePositions = useRef(new Float32Array(0));
    const meshes = useRef(new Map());
    const lines = useRef();
    const spin = useRef();
    const anchorId = useRef(null);
    anchorId.current = selectedId;
    const [hoveredId, setHoveredId] = useState(null);

    // eslint-disable-next-line react-hooks/exhaustive-deps
    const list = useMemo(() => Array.from(nodes.values()), [version]);
    const maxRetained = useMemo(
        () => list.reduce((max, n) => Math.max(max, n.retainedBytes), 1),
        [list]
    );

    // Rebuild the simulation when a new graph answer arrives. Old nodes keep their positions.
    useEffect(() => {
        const old = simNodes.current;
        const anchor = old.get(anchorId.current);
        const next = new Map();
        for (const n of list) {
            const prev = old.get(n.id);
            next.set(
                n.id,
                prev || {
                    id: n.id,
                    x: (anchor?.x ?? 0) + (Math.random() - 0.5) * 10,
                    y: (anchor?.y ?? 0) + (Math.random() - 0.5) * 10,
                    z: (anchor?.z ?? 0) + (Math.random() - 0.5) * 10,
                }
            );
        }
        simNodes.current = next;

        const linkList = [];
        for (const e of edges.values()) {
            if (next.has(e.source) && next.has(e.target)) {
                linkList.push({ source: e.source, target: e.target });
            }
        }
        links.current = linkList;

        sim.current?.stop();
        const s = forceSimulation(Array.from(next.values()), 3)
            .force("charge", forceManyBody().strength(-40))
            .force("link", forceLink(linkList).id((d) => d.id).distance(12).strength(0.5))
            .force("center", forceCenter(0, 0, 0))
            .stop();
        s.alpha(layout === "force" ? 1 : 0);
        sim.current = s;

        linePositions.current = new Float32Array(linkList.length * 6);
        if (lines.current) {
            const geometry = lines.current.geometry;
            geometry.setAttribute("position", new THREE.BufferAttribute(linePositions.current, 3));
            geometry.setDrawRange(0, linkList.length * 2);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [version]);

    // Orbit targets for the solar layout. Switching back to force layout wakes the simulation.
    useEffect(() => {
        targets.current = computeTargets(list, edges, rootId);
        if (layout === "force") sim.current?.alpha(1);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [version, layout, rootId]);

    useFrame((_, delta) => {
        const s = sim.current;
        if (layout === "force") {
            if (s && s.alpha() > s.alphaMin()) s.tick();
        } else {
            const k = Math.min(1, delta * 4); // glide towards the orbit position
            for (const [id, p] of simNodes.current) {
                const t = targets.current.get(id);
                if (!t) continue;
                p.x += (t.x - p.x) * k;
                p.y += (t.y - p.y) * k;
                p.z += (t.z - p.z) * k;
            }
        }

        if (spin.current) {
            spin.current.rotation.y += layout === "solar" ? delta * 0.06 : 0;
        }

        for (const [id, p] of simNodes.current) {
            const mesh = meshes.current.get(id);
            if (mesh) mesh.position.set(p.x, p.y, p.z);
        }

        const arr = linePositions.current;
        const l = links.current;
        for (let i = 0; i < l.length; i++) {
            const a = l[i].source;
            const b = l[i].target;
            if (typeof a !== "object" || typeof b !== "object") continue;
            arr[i * 6] = a.x;
            arr[i * 6 + 1] = a.y;
            arr[i * 6 + 2] = a.z;
            arr[i * 6 + 3] = b.x;
            arr[i * 6 + 4] = b.y;
            arr[i * 6 + 5] = b.z;
        }
        const attribute = lines.current?.geometry.getAttribute("position");
        if (attribute) attribute.needsUpdate = true;
    });

    return (
        <group ref={spin}>
            <lineSegments ref={lines} frustumCulled={false}>
                <bufferGeometry />
                <lineBasicMaterial color="#64748b" transparent opacity={0.45} />
            </lineSegments>

            {list.map((n) => {
                const radius = 0.6 + 3.4 * Math.sqrt(n.retainedBytes / maxRetained);
                const selected = n.id === selectedId;
                const isRoot = n.id === rootId;
                return (
                    <mesh
                        key={n.id}
                        ref={(el) => {
                            if (el) meshes.current.set(n.id, el);
                            else meshes.current.delete(n.id);
                        }}
                        onClick={(e) => {
                            e.stopPropagation();
                            onSelect(n);
                        }}
                        onDoubleClick={(e) => {
                            e.stopPropagation();
                            onExpand(n);
                        }}
                        onPointerOver={(e) => {
                            e.stopPropagation();
                            setHoveredId(n.id);
                            document.body.style.cursor = "pointer";
                        }}
                        onPointerOut={() => {
                            setHoveredId((current) => (current === n.id ? null : current));
                            document.body.style.cursor = "auto";
                        }}
                    >
                        <sphereGeometry args={[radius, 20, 20]} />
                        <meshStandardMaterial
                            color={heat(n.retainedPercent)}
                            emissive={selected || isRoot ? "#ffffff" : "#000000"}
                            emissiveIntensity={selected ? 0.6 : isRoot ? 0.3 : 0}
                        />
                        {(hoveredId === n.id || isRoot) && (
                            <Html distanceFactor={45} center style={{ pointerEvents: "none" }}>
                                <div
                                    style={{
                                        transform: `translateY(-${radius * 6 + 14}px)`,
                                        background: "rgba(15, 23, 42, 0.9)",
                                        color: "#e2e8f0",
                                        padding: "2px 6px",
                                        borderRadius: 4,
                                        fontSize: 12,
                                        fontFamily: "system-ui, sans-serif",
                                        whiteSpace: "nowrap",
                                    }}
                                >
                                    {shortName(n.label)}
                                </div>
                            </Html>
                        )}
                    </mesh>
                );
            })}
        </group>
    );
}

export default function GraphScene(props) {
    return (
        <Canvas camera={{ position: [0, 0, 90], fov: 60 }} onPointerMissed={() => props.onSelect(null)}>
            <color attach="background" args={["#0b1020"]} />
            <ambientLight intensity={0.6} />
            <pointLight position={[60, 60, 60]} intensity={1.4} />
            <Graph {...props} />
            <OrbitControls makeDefault />
        </Canvas>
    );
}