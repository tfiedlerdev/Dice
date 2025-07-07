package tfdev.dice

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
                // Create a static floor
                val floor = createCube(Vec3(0f, 0f, 0f)).apply {
                    /*scale.apply {
                        x = 1.5f
                        y = 0.1f
                        z = 1.5f
                    }*/
                    color.apply{
                        set(0, 0f)
                        set(1, 0f)
                        set(2, 1f)
                    }
                    isStatic = true // Make it static
                    gravity.zeros()
                    setDirty()
                    Log.d("GLScene", gravity.toString())

                }
                addChild(floor)
                
                // Create the main cube that can be controlled
                cube = createCube(Vec3(-0.25f, 0.5f, 0f))
                //addChild(cube)
                
                // Create a second cube with initial physics
                val physicsCube = createCube(Vec3(0f, 2f, 0f)).apply {
                    // Apply initial velocity and angular velocity
                    //velocity.assign(Vec4(-2f, 0f, 0f, 0f))
                    //torque.assign(Vec4(0f, 0f, 0.1f, 0f)) // Rotate around Z-axis
                    setDirty()
                }
                addChild(physicsCube)
                
                // Create a third cube for more complex interactions
                /*val thirdCube = createCube(Vec3(0f, 0.8f, 0.5f)).apply {
                    velocity.assign(Vec4(0f, -1f, -1f, 0f))
                    torque.assign(Vec4(3f, 0f, 0f, 0f)) // Rotate around X-axis
                    setDirty()
                }
                addChild(thirdCube)*/
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
            // Set physics properties
            mass = 1f
            restitution = 0.7f // Bouncy
            friction = 0.3f
            setDirty()
        }
    }

    override fun onProgressChanged(seekBar: SeekBar?, p: Int, p2: Boolean) {
        val percentage = p.toFloat() / 100f
        when (seekBar) {
            seekBar1 -> {
                // Control Y position (height)
                cube.apply {
                    pos.y = percentage * 2f - 1f // Range from -1 to 1
                    setDirty()
                }
            }

            seekBar2 -> {
                // Control scale
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
                // Control X position
                cube.apply {
                    pos.x = percentage - 0.5f // Range from -0.5 to 0.5
                    setDirty()
                }
            }
        }
    }

    override fun onStartTrackingTouch(p0: SeekBar?) {}

    override fun onStopTrackingTouch(p0: SeekBar?) {}
}