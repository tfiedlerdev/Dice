package tfdev.engine3d.gpu.physics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tfdev.engine3d.gpu.gl_object3d.GLScene

/**
 * A bouncy box dropped onto the floor should actually bounce - gain upward
 * velocity on impact - and lose some, but not all, energy on each bounce.
 */
@RunWith(AndroidJUnit4::class)
class RestitutionTest {

    @Test
    fun bouncyBoxBouncesAndLosesHeightEachBounce() {
        val scene = GLScene()
        val floor = box(4f, 1f, 4f, 0f, -0.5f, 0f).apply {
            isStatic = true
            restitution = 0.6f
            setDirty()
        }
        val restHeight = 0.2f
        val dropHeight = 2f
        val cube = box(0.4f, 0.4f, 0.4f, 0f, dropHeight, 0f).apply {
            restitution = 0.6f
            setDirty()
        }
        scene.addChild(floor)
        scene.addChild(cube)

        var wasFalling = false
        var firstBounceApex = Float.NaN
        var trackingApexAfterBounce = false
        var apexCandidate = Float.NEGATIVE_INFINITY

        val stepMillis = 16L
        var elapsed = 0L
        while (elapsed < 4000L && firstBounceApex.isNaN()) {
            val yBefore = cube.pos.y
            val vyBefore = cube.velocity.y
            simulate(scene, stepMillis, stepMillis)
            elapsed += stepMillis

            assertTrue("cube must stay finite", cube.pos.y.isFinite())

            val vyAfter = cube.velocity.y
            if (!trackingApexAfterBounce) {
                // A bounce is: falling (or resting) one step, then clearly moving upward the next,
                // while already near the floor (not still up in the air).
                if (wasFalling && vyAfter > 0.5f && yBefore < restHeight + 0.1f) {
                    trackingApexAfterBounce = true
                    apexCandidate = yBefore
                }
            } else {
                if (cube.pos.y > apexCandidate) {
                    apexCandidate = cube.pos.y
                } else if (vyAfter <= 0f) {
                    // started falling again -> apexCandidate was the peak of the first bounce
                    firstBounceApex = apexCandidate
                }
            }
            wasFalling = vyBefore <= 0f
        }

        assertTrue("the box should have bounced within the simulated time", !firstBounceApex.isNaN())
        val bounceHeight = firstBounceApex - restHeight
        assertTrue(
            "a restitution-0.6 bounce should rise noticeably above the resting height (rose $bounceHeight)",
            bounceHeight > 0.1f
        )
        assertTrue(
            "the bounce should lose energy, not exceed the original drop height ($dropHeight), apex was $firstBounceApex",
            firstBounceApex < dropHeight
        )
    }
}
