package tfdev.engine3d.gpu.physics

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tfdev.engine3d.gpu.gl_object3d.GLScene
import kotlin.math.abs

/**
 * Two boxes stacked on the floor must settle into a stable stack: the
 * bottom box resting on the floor, the top box resting on the bottom one,
 * neither sunk into the other. This is the scenario that most directly
 * exercises the multi-point contact manifold: a single deepest-corner
 * contact point is not enough here, since that lets a flat resting box
 * rock between corners instead of settling.
 */
@RunWith(AndroidJUnit4::class)
class StackingTest {

    @Test
    fun twoBoxesStackCleanlyOnTheFloor() {
        val scene = GLScene()
        val floor = box(4f, 1f, 4f, 0f, -0.5f, 0f).apply { isStatic = true; setDirty() }
        val bottom = box(0.4f, 0.4f, 0.4f, 0f, 0.21f, 0f)
        val top = box(0.4f, 0.4f, 0.4f, 0f, 0.66f, 0f)
        scene.addChild(floor)
        scene.addChild(bottom)
        scene.addChild(top)

        simulate(scene, 6000L)

        assertTrue(
            "positions must stay finite",
            listOf(bottom, top).all { it.pos.x.isFinite() && it.pos.y.isFinite() && it.pos.z.isFinite() }
        )

        val expectedBottomY = 0.2f
        val expectedTopY = 0.6f
        assertTrue(
            "bottom box should rest on the floor near y=$expectedBottomY, is at ${bottom.pos.y}",
            abs(bottom.pos.y - expectedBottomY) < 0.06f
        )
        assertTrue(
            "top box should rest on the bottom box near y=$expectedTopY, is at ${top.pos.y}",
            abs(top.pos.y - expectedTopY) < 0.08f
        )
        assertTrue(
            "the stack should not have toppled sideways: bottom x/z=(${bottom.pos.x},${bottom.pos.z}), top x/z=(${top.pos.x},${top.pos.z})",
            abs(bottom.pos.x) < 0.1f && abs(bottom.pos.z) < 0.1f && abs(top.pos.x) < 0.15f && abs(top.pos.z) < 0.15f
        )
        assertTrue(
            "both boxes should have settled (low velocity): bottom=${bottom.velocity}, top=${top.velocity}",
            bottom.velocity.length() < 0.1f && top.velocity.length() < 0.1f
        )
    }
}
