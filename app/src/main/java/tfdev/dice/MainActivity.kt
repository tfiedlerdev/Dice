package tfdev.dice

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import sensors_in_paradise.sonar.custom_views.stickman.math.Vec3
import tfdev.engine3d.gpu.GLRender3DView
import tfdev.engine3d.gpu.gl_object3d.GLCube
import tfdev.engine3d.gpu.gl_object3d.GLObject3D

class MainActivity : AppCompatActivity(), OnSeekBarChangeListener {
    private lateinit var seekBar1: SeekBar
    private lateinit var seekBar2: SeekBar
    private lateinit var seekBar3: SeekBar

    private lateinit var cube: GLObject3D
    private lateinit var renderView: GLRender3DView
    private val defaultCubeScale = 0.4f;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContentView(
            R.layout.activity_main
        )

        renderView = findViewById(R.id.renderView_activityMain)

        seekBar1 = findViewById(R.id.seekBar1)
        seekBar2 = findViewById(R.id.seekBar2)
        seekBar3 = findViewById(R.id.seekBar3)
        seekBar1.setOnSeekBarChangeListener(this)
        seekBar2.setOnSeekBarChangeListener(this)
        seekBar3.setOnSeekBarChangeListener(this)

        renderView.setOnSceneInitializedListener { scene->
            scene.apply {
                // First cube - stationary with medium bounciness
                cube = createCube(Vec3(-0.25f, 0f, 0f)).apply {
                    restitution = 0.7f  // Bouncy
                    friction = 0.3f
                    mass = 2f  // Heavier
                }
                addChild(cube)
                
                // Second cube - moving with different properties
                addChild(createCube(Vec3(.5f, 0f, 0f)).apply {
                    // Initial velocity to create collision
                    velocity.x = -2f
                    P.x = velocity.x * mass  // Set momentum
                    
                    // Different physics properties
                    restitution = 0.5f  // Less bouncy
                    friction = 0.2f
                    mass = 1f  // Lighter
                    
                    // Slight offset to ensure collision
                    pos.y = 0.05f
                    pos.z = 0.05f
                    
                    // Add some rotation for visual effect
                    omega.y = 1f
                    L.y = omega.y * (mass / 6f)  // Set angular momentum (using inertia for cube)
                    
                    setDirty()
                })
                
                // Add gravity to both objects
                cube.constantForce.y = -9.8f
                children[1].constantForce.y = -9.8f
                
                // Add a floor
                addChild(createCube(Vec3(0f, -1f, 0f)).apply {
                    scale.x = 3f
                    scale.y = 0.1f
                    scale.z = 3f
                    mass = 10000f  // Very heavy (essentially static)
                    restitution = 0.8f  // Bouncy floor
                    friction = 0.5f
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
                    pos.y = percentage
                    setDirty()
                }
            }

            seekBar2 -> {
                val s = (percentage +0.5f)*defaultCubeScale
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
                    pos.x= percentage-0.5f
                    setDirty()
                }
            }

        }
        //renderView.onObjectChanged()

    }

    override fun onStartTrackingTouch(p0: SeekBar?) {}

    override fun onStopTrackingTouch(p0: SeekBar?) {}


}