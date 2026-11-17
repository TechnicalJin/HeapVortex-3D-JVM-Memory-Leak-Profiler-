import { formatBytes } from '../utils/format.js'
import { splitClassName } from '../utils/classNames.js'

export default function NodeTooltip({ node }) {
    const { name, pkg } = splitClassName(node.label)
    return (
        <div
            style={{
                background: 'rgba(15, 23, 42, 0.95)',
                color: '#e2e8f0',
                padding: '6px 8px',
                borderRadius: 6,
                fontSize: 12,
                fontFamily: 'system-ui, sans-serif',
                whiteSpace: 'nowrap',
                lineHeight: 1.4,
            }}
        >
            <div style={{ fontWeight: 600 }}>{name}</div>
            {pkg && <div style={{ opacity: 0.7 }}>{pkg}</div>}
            <div>Shallow {formatBytes(node.shallowBytes)}</div>
            <div>
                Retained {formatBytes(node.retainedBytes)} ({Number(node.retainedPercent ?? 0).toFixed(2)}%)
            </div>
        </div>
    )
}