package tfdev.engine3d.gpu.physics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import tfdev.engine3d.gpu.gl_object3d.GLScene

/**
 * Two dynamic (non-static, no floor involved) boxes of equal mass colliding
 * head-on should separate - not pass through each other - and the collision
 * should roughly conserve momentum (no net energy/momentum created out of
 * nowhere by the impulse solver).
 */
@RunWith(AndroidJUnit4::class)
class DynamicCollisionTest {

    @Test
    fun twoBoxesCollidingHeadOnSeparateAndRoughlyConserveMomentum() {
        val scene = GLScene()
        val left = box(0.4f, 0.4f, 0.4f, -1f, 0f, 0f).apply {
            gravity.zeros()
            restitution = 0.5f
            velocity.assign(Vec3(1.5f, 0f, 0f))
            setDirty()
        }
        val right = box(0.4f, 0.4f, 0.4f, 1f, 0f, 0f).apply {
            gravity.zeros()
            restitution = 0.5f
            velocity.assign(Vec3(-1.5f, 0f, 0f))
            setDirty()
        }
        scene.addChild(left)
        scene.addChild(right)

        val momentumBefore = left.mass * left.velocity.x + right.mass * right.velocity.x

        simulate(scene, 2500L)

        assertTrue(
            "positions must stay finite",
            left.pos.x.isFinite() && right.pos.x.isFinite()
        )
        assertTrue(
            "the boxes must not end up overlapping/passed through each other (left.x=${left.pos.x}, right.x=${right.pos.x})",
            right.pos.x - left.pos.x > 0.35f
        )
        assertTrue(
            "left box should have been pushed back (bounced), vx=${left.velocity.x}",
            left.velocity.x < 0f
        )
        assertTrue(
            "right box should have been pushed back (bounced), vx=${right.velocity.x}",
            right.velocity.x > 0f
        )

        val momentumAfter = left.mass * left.velocity.x + right.mass * right.velocity.x
        assertTrue(
            "total momentum should be roughly conserved (before=$momentumBefore, after=$momentumAfter)",
            kotlin.math.abs(momentumAfter - momentumBefore) < 0.2f
        )
    }
}
