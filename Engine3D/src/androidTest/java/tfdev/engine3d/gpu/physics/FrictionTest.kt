package tfdev.engine3d.gpu.physics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import tfdev.engine3d.gpu.gl_object3d.GLScene
import kotlin.math.abs

/**
 * A box sliding across a high-friction floor should be decelerated by
 * friction and come to a stop, while still being held up by the
 * non-penetration constraint the whole time (friction and the normal
 * constraint are solved together, per contact, every iteration).
 */
@RunWith(AndroidJUnit4::class)
class FrictionTest {

    @Test
    fun slidingBoxIsDeceleratedByFrictionAndStops() {
        val scene = GLScene()
        val floor = box(8f, 1f, 8f, 0f, -0.5f, 0f).apply {
            isStatic = true
            friction = 0.6f
            setDirty()
        }
        val cube = box(0.4f, 0.4f, 0.4f, -2f, 0.21f, 0f).apply {
            friction = 0.6f
            restitution = 0f
            velocity.assign(Vec3(2.5f, 0f, 0f))
            setDirty()
        }
        scene.addChild(floor)
        scene.addChild(cube)

        var maxPenetration = 0f
        val expectedRestY = 0.2f
        val totalMillis = 3000L
        val stepMillis = 16L
        var elapsed = 0L
        while (elapsed < totalMillis) {
            simulate(scene, stepMillis, stepMillis)
            elapsed += stepMillis
            val penetration = expectedRestY - cube.pos.y
            if (penetration > maxPenetration) maxPenetration = penetration
        }

        assertTrue("cube should stay finite", cube.pos.x.isFinite() && cube.velocity.x.isFinite())
        assertTrue("cube should not sink into the floor while sliding, max penetration was $maxPenetration", maxPenetration < 0.05f)
        assertTrue(
            "friction should have stopped the slide, but vx is still ${cube.velocity.x}",
            abs(cube.velocity.x) < 0.05f
        )
        assertTrue(
            "the box should have actually travelled forward before stopping, dx=${cube.pos.x - (-2f)}",
            cube.pos.x - (-2f) > 0.1f
        )
    }

    @Test
    fun frictionlessBoxKeepsSlidingMuchFartherThanHighFrictionBox() {
        fun finalXAfterSliding(friction: Float): Float {
            val scene = GLScene()
            val floor = box(12f, 1f, 12f, 0f, -0.5f, 0f).apply {
                isStatic = true
                this.friction = friction
                setDirty()
            }
            val cube = box(0.4f, 0.4f, 0.4f, -4f, 0.21f, 0f).apply {
                this.friction = friction
                restitution = 0f
                velocity.assign(Vec3(2f, 0f, 0f))
                setDirty()
            }
            scene.addChild(floor)
            scene.addChild(cube)
            simulate(scene, 2500L)
            return cube.pos.x
        }

        val lowFrictionX = finalXAfterSliding(0.02f)
        val highFrictionX = finalXAfterSliding(0.9f)

        assertTrue(
            "a near-frictionless box ($lowFrictionX) should travel noticeably farther than a high-friction one ($highFrictionX)",
            lowFrictionX > highFrictionX + 0.3f
        )
    }
}
