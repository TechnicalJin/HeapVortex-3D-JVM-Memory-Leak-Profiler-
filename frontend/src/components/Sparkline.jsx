import { sparklinePoints } from '../utils/sparkline.js'

export default function Sparkline({ values, color = '#3b82f6', width = 240, height = 40, max = 100 }) {
    const points = sparklinePoints(values, width, height, max)
    return (
        <svg
            viewBox={`0 0 ${width} ${height}`}
            width="100%"
            height={height}
            preserveAspectRatio="none"
            role="img"
            aria-label="history chart"
            style={{ background: '#0f172a', borderRadius: 4, display: 'block', marginTop: 4 }}
        >
            {points && (
                <polyline
                    points={points}
                    fill="none"
                    stroke={color}
                    strokeWidth="1.5"
                    vectorEffect="non-scaling-stroke"
                />
            )}
        </svg>
    )
}