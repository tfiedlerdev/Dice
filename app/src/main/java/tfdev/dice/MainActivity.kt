package tfdev.dice

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec4
import tfdev.engine3d.gpu.GLRender3DView
import tfdev.engine3d.gpu.gl_object3d.GLCube
import tfdev.engine3d.gpu.gl_object3d.GLObject3D

class MainActivity : AppCompatActivity(), OnSeekBarChangeListener {
    private lateinit var seekBar1: SeekBar
    private lateinit var seekBar2: SeekBar
    private lateinit var seekBar3: SeekBar

    private lateinit var cube: GLObject3D
    private lateinit var renderView: GLRender3DView
    private val defaultCubeScale = 0.4f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        renderView = findViewById(R.id.renderView_activityMain)

        seekBar1 = findViewById(R.id.seekBar1)
        seekBar2 = findViewById(R.id.seekBar2)
        seekBar3 = findViewById(R.id.seekBar3)
        seekBar1.setOnSeekBarChangeListener(this)
        seekBar2.setOnSeekBarChangeListener(this)
        seekBar3.setOnSeekBarChangeListener(this)

        renderView.setOnSceneInitializedListener { scene ->
            scene.apply {
                // A large static floor every falling cube collides with and rests on.
                val floor = GLCube(this@MainActivity, color = Vec4(0.55f, 0.58f, 0.65f, 1f)).apply {
                    scale.apply {
                        x = 4f
                        y = 0.2f
                        z = 4f
                    }
                    pos.y = -0.4f
                    isStatic = true
                    setDirty()
                }
                addChild(floor)

                // Interactively controlled via the seek bars.
                cube = createCube(Vec3(-0.25f, 1f, 0f))
                addChild(cube)

                // A cube dropped with an initial tilt and spin - demonstrates collision
                // resolution for axis-unaligned (rotated) boxes, not just axis-aligned ones.
                addChild(createCube(Vec3(0.5f, 1.6f, 0.2f)).apply {
                    eulerRotDeg.assign(Vec3(25f, 40f, 15f))
                    velocity.assign(Vec3(-0.3f, 0f, 0f))
                    omega.assign(Vec3(1.5f, 0.8f, 0f))
                    restitution = 0.4f
                    setDirty()
                })
            }
            return@setOnSceneInitializedListener
        }
    }

    fun createCube(position: Vec3): GLCube {
        return GLCube(this).apply {
            scale.apply {
                x = defaultCubeScale
                y = defaultCubeScale
                z = defaultCubeScale
            }
            pos.apply {
                x = position.x
                y = position.y
                z = position.z
            }
            setDirty()
        }
    }

    override fun onProgressChanged(seekBar: SeekBar?, p: Int, p2: Boolean) {
        val percentage = p.toFloat() / 100f
        when (seekBar) {
            seekBar1 -> {
                cube.apply {
                    pos.y = percentage * 2f
                    setDirty()
                }
            }

            seekBar2 -> {
                val s = (percentage + 0.5f) * defaultCubeScale
                cube.apply {
                    scale.apply {
                        x = s
                        y = s
                        z = s
                    }
                    setDirty()
                }
            }

            seekBar3 -> {
                cube.apply {
                    pos.x = percentage - 0.5f
                    setDirty()
                }
            }
        }
    }

    override fun onStartTrackingTouch(p0: SeekBar?) {}

    override fun onStopTrackingTouch(p0: SeekBar?) {}
}
