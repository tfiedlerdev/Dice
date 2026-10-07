package tfdev.engine3d.gpu.physics

import tfdev.engine3d.gpu.gl_object3d.GLObject3D
import tfdev.engine3d.gpu.gl_object3d.GLScene
import java.nio.FloatBuffer

/**
 * A [GLObject3D] that renders nothing - it needs no GL context/program/shader,
 * only the physics/transform state every object already carries - so tests
 * can build scenes and run the real physics & collision pipeline on plain
 * JVM/instrumentation threads, without a GL surface.
 */
class TestBody : GLObject3D() {
    override val openGLProgram: Int? = null
    override val vertexBuffer: FloatBuffer? = null
    override val vertexCount: Int = 0
}

/** Builds a dynamic/static box body with the given full-size extents, centered at [px],[py],[pz]. */
fun box(sx: Float, sy: Float, sz: Float, px: Float, py: Float, pz: Float): TestBody {
    return TestBody().apply {
        scale.x = sx; scale.y = sy; scale.z = sz
        pos.x = px; pos.y = py; pos.z = pz
        setDirty()
    }
}

/**
 * Runs the exact same step -> updateSelfAndChild -> checkCollisions pipeline
 * [GLRenderer] drives every frame, for [totalMillis] of simulated time split
 * into steps of [stepMillis], each further divided into [subSteps] physics
 * substeps (matching how the real renderer avoids tunneling on larger
 * frame times).
 */
fun simulate(scene: GLScene, totalMillis: Long, stepMillis: Long = 16L, subSteps: Int = 4) {
    val subStepMillis = stepMillis / subSteps
    var elapsed = 0L
    while (elapsed < totalMillis) {
        repeat(subSteps) {
            scene.step(subStepMillis)
            scene.updateSelfAndChild()
            scene.checkCollisions()
        }
        elapsed += stepMillis
    }
}
