package tfdev.dice

import tfdev.engine3d.gpu.gl_object3d.GLCube

/** A die plus the color it's currently painted, kept alongside the physics/render object itself. */
data class DieEntry(val die: GLCube, var colorArgb: Int)
