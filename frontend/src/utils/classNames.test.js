import { splitClassName } from './classNames.js'

describe('splitClassName', () => {
    it('splits the package from the class name', () => {
        expect(splitClassName('java.util.HashMap$Node[]')).toEqual({ name: 'HashMap$Node[]', pkg: 'java.util' })
    })

    it('removes the class prefix of class objects', () => {
        expect(splitClassName('class com.heapvortex.mock.LeakyApp')).toEqual({
            name: 'LeakyApp',
            pkg: 'com.heapvortex.mock',
        })
    })

    it('handles names without a package', () => {
        expect(splitClassName('byte[]')).toEqual({ name: 'byte[]', pkg: '' })
    })

    it('handles missing labels', () => {
        expect(splitClassName(null)).toEqual({ name: '', pkg: '' })
    })
})