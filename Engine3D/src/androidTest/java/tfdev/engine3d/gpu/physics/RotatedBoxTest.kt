package tfdev.engine3d.gpu.physics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import tfdev.engine3d.gpu.gl_object3d.GLScene
import kotlin.math.abs

/**
 * The stated goal is collision resolution for *axis-unaligned* boxes, so
 * this drops a box onto the floor with an initial tilt on all three axes
 * (exercising the general oriented-bounding-box SAT path, including its
 * edge-edge contact case, not just the axis-aligned face-face case) and
 * requires it to still settle into a stable, non-penetrating, non-spinning
 * rest - same as a non-rotated box would.
 */
@RunWith(AndroidJUnit4::class)
class RotatedBoxTest {

    @Test
    fun tiltedBoxDroppedOnFloorSettlesWithoutExplodingOrPenetrating() {
        val scene = GLScene()
        val floor = box(6f, 1f, 6f, 0f, -0.5f, 0f).apply { isStatic = true; setDirty() }
        val cube = box(0.4f, 0.4f, 0.4f, 0f, 2f, 0f).apply {
            eulerRotDeg.assign(Vec3(25f, 40f, 15f))
            setDirty()
        }
        scene.addChild(floor)
        scene.addChild(cube)

        // A cube's rest height above the floor is the same (half its side length) no matter
        // which face ends up facing down, since all faces are identical.
        val expectedRestY = 0.2f

        simulate(scene, 8000L)

        assertTrue("position must stay finite", cube.pos.x.isFinite() && cube.pos.y.isFinite() && cube.pos.z.isFinite())
        assertTrue(
            "cube should not have flown off sideways, x=${cube.pos.x} z=${cube.pos.z}",
            abs(cube.pos.x) < 2f && abs(cube.pos.z) < 2f
        )
        assertTrue(
            "cube should have settled near y=$expectedRestY but is at y=${cube.pos.y}",
            abs(cube.pos.y - expectedRestY) < 0.08f
        )
        assertTrue(
            "cube should have stopped moving but velocity is ${cube.velocity}",
            cube.velocity.length() < 0.15f
        )
        assertTrue(
            "cube should have stopped spinning but omega is ${cube.omega}",
            cube.omega.length() < 0.3f
        )
    }
}
