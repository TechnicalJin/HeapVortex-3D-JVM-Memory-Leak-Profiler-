import { useEffect, useMemo, useRef, useState } from "react";
import { Html } from "@react-three/drei";
import * as THREE from "three";
import { computeTargets } from "../utils/solarLayout.js";
import NodeTooltip from "./NodeTooltip.jsx";

const COLD = new THREE.Color("#3b82f6");
const HOT = new THREE.Color("#ef4444");
const HOVER_INTERVAL_MS = 60;

function heat(percent) {
    const t = Math.min(1, Math.sqrt(Math.max(percent, 0) / 25));
    return COLD.clone().lerp(HOT, t);
}

/**
 * Draws thousands of nodes with one instanced mesh. Layout is computed once per graph answer.
 * Click and hover use Three.js raycasting (through the React Three Fiber event system):
 * the ray hits the instanced mesh and reports the index of the node under the pointer.
 */
export default function InstancedGraph({ nodes, edges, version, rootId, selectedId, onSelect, onExpand }) {
    const mesh = useRef();
    const lastMove = useRef(0);
    const [hoveredId, setHoveredId] = useState(null);

    const data = useMemo(() => {
        const list = Array.from(nodes.values());
        const targets = computeTargets(list, edges, rootId);
        const maxRetained = list.reduce((max, n) => Math.max(max, n.retainedBytes), 1);
        const byId = new Map(list.map((n) => [n.id, n]));

        const positions = [];
        for (const e of edges.values()) {
            const a = targets.get(e.source);
            const b = targets.get(e.target);
            if (a && b) positions.push(a.x, a.y, a.z, b.x, b.y, b.z);
        }
        return { list, byId, targets, maxRetained, linePositions: new Float32Array(positions) };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [version, rootId]);

    useEffect(() => {
        const m = mesh.current;
        if (!m) return;
        const dummy = new THREE.Object3D();
        data.list.forEach((n, i) => {
            const p = data.targets.get(n.id) || { x: 0, y: 0, z: 0 };
            dummy.position.set(p.x, p.y, p.z);
            dummy.scale.setScalar(0.4 + 2.4 * Math.sqrt(n.retainedBytes / data.maxRetained));
            dummy.updateMatrix();
            m.setMatrixAt(i, dummy.matrix);
            m.setColorAt(i, heat(n.retainedPercent));
        });
        m.instanceMatrix.needsUpdate = true;
        if (m.instanceColor) m.instanceColor.needsUpdate = true;
        m.computeBoundingSphere();
    }, [data]);

    const selected = selectedId ? data.targets.get(selectedId) : null;
    const hovered = hoveredId ? data.byId.get(hoveredId) : null;
    const hoveredPosition = hoveredId ? data.targets.get(hoveredId) : null;

    return (
        <group>
            <lineSegments key={data.linePositions.length} frustumCulled={false}>
                <bufferGeometry>
                    <bufferAttribute attach="attributes-position" args={[data.linePositions, 3]} />
                </bufferGeometry>
                <lineBasicMaterial color="#64748b" transparent opacity={0.25} />
            </lineSegments>

            <instancedMesh
                key={data.list.length}
                ref={mesh}
                args={[null, null, data.list.length]}
                frustumCulled={false}
                onClick={(e) => {
                    e.stopPropagation();
                    const n = data.list[e.instanceId];
                    if (n) onSelect(n);
                }}
                onDoubleClick={(e) => {
                    e.stopPropagation();
                    const n = data.list[e.instanceId];
                    if (n) onExpand(n);
                }}
                onPointerMove={(e) => {
                    e.stopPropagation();
                    // the ray test walks every instance, so it runs at most about 16 times per second
                    const now = performance.now();
                    if (now - lastMove.current < HOVER_INTERVAL_MS) return;
                    lastMove.current = now;
                    const id = data.list[e.instanceId]?.id ?? null;
                    setHoveredId((current) => (current === id ? current : id));
                    document.body.style.cursor = "pointer";
                }}
                onPointerOut={() => {
                    setHoveredId(null);
                    document.body.style.cursor = "auto";
                }}
            >
                <sphereGeometry args={[1, 10, 10]} />
                <meshStandardMaterial />
            </instancedMesh>

            {selected && (
                <mesh position={[selected.x, selected.y, selected.z]}>
                    <sphereGeometry args={[3.5, 16, 16]} />
                    <meshBasicMaterial color="#ffffff" wireframe />
                </mesh>
            )}

            {hovered && hoveredPosition && (
                <Html
                    position={[hoveredPosition.x, hoveredPosition.y, hoveredPosition.z]}
                    center
                    style={{ pointerEvents: "none" }}
                >
                    <div style={{ transform: "translateY(-48px)" }}>
                        <NodeTooltip node={hovered} />
                    </div>
                </Html>
            )}
        </group>
    );
}