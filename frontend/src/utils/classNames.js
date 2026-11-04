/** "class com.example.Foo" or "com.example.Foo" gives { name: "Foo", pkg: "com.example" }. */
export function splitClassName(label) {
    const clean = String(label || '').replace(/^class /, '')
    const dot = clean.lastIndexOf('.')
    return dot < 0
        ? { name: clean, pkg: '' }
        : { name: clean.slice(dot + 1), pkg: clean.slice(0, dot) }
}