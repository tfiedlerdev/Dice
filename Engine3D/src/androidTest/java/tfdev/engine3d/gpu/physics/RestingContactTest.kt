package tfdev.engine3d.gpu.physics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tfdev.engine3d.gpu.gl_object3d.GLScene
import kotlin.math.abs

/**
 * The most basic collision-resolution requirement: an axis-aligned box
 * dropped onto a static floor must come to rest ON the floor - not
 * penetrate through it, not hover above it, and not jitter forever.
 *
 * On the evaluated `collision-resolution` branch this exact scenario made
 * the box fall straight through the floor (penetration of several whole
 * units, not a small jitter) - see the PR description for details.
 */
@RunWith(AndroidJUnit4::class)
class RestingContactTest {

    @Test
    fun boxDroppedOnFloorSettlesOnTopWithoutPenetrating() {
        val scene = GLScene()
        val floor = box(4f, 1f, 4f, 0f, -0.5f, 0f).apply { isStatic = true; setDirty() }
        val cube = box(0.4f, 0.4f, 0.4f, 0f, 2f, 0f)
        scene.addChild(floor)
        scene.addChild(cube)

        val expectedRestY = 0.2f // floor top (y=0) + half cube height
        var maxPenetration = 0f

        val totalMillis = 6000L
        val stepMillis = 16L
        var elapsed = 0L
        while (elapsed < totalMillis) {
            simulate(scene, stepMillis, stepMillis)
            elapsed += stepMillis

            assertTrue("position must stay finite", cube.pos.y.isFinite())
            val penetration = expectedRestY - cube.pos.y
            if (penetration > maxPenetration) maxPenetration = penetration
        }

        assertTrue(
            "cube penetrated the floor by $maxPenetration (should stay < 0.05)",
            maxPenetration < 0.05f
        )
        assertTrue(
            "cube should have settled near y=$expectedRestY but is at y=${cube.pos.y}",
            abs(cube.pos.y - expectedRestY) < 0.05f
        )
        assertTrue(
            "cube should have stopped moving but velocity is ${cube.velocity}",
            cube.velocity.length() < 0.1f
        )
    }

    @Test
    fun staticFloorNeverMoves() {
        val scene = GLScene()
        val floor = box(4f, 1f, 4f, 0f, -0.5f, 0f).apply { isStatic = true; setDirty() }
        val cube = box(0.4f, 0.4f, 0.4f, 0f, 2f, 0f)
        scene.addChild(floor)
        scene.addChild(cube)

        simulate(scene, 3000L)

        assertTrue("a static body must never move", floor.pos.y == -0.5f)
        assertTrue("a static body must never gain velocity", floor.velocity.length() == 0f)
    }
}
