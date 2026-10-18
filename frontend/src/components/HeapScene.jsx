
import { useEffect, useRef } from 'react'
import * as THREE from 'three'

export default function HeapScene() {
  const containerRef = useRef(null)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const renderer = new THREE.WebGLRenderer({ antialias: true })

    renderer.setSize(
      container.clientWidth,
      container.clientHeight
    )
    renderer.setClearColor(0x05070d)

    container.appendChild(renderer.domElement)

    renderer.render(
      new THREE.Scene(),
      new THREE.PerspectiveCamera()
    )

    return () => {
      renderer.dispose()
      container.removeChild(renderer.domElement)
    }
  }, [])

  return (
    <div
      ref={containerRef}
      style={{ width: '100%', height: 520 }}
    />
  )
}
