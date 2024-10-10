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
                cube = createCube(Vec3(-0.25f, 0f, 0f))
                addChild(cube)
                addChild(createCube(Vec3(.5f, 0f, 0f)).apply {
                    force.x = -1f

                    pos.y = 0.1f
                    pos.z = 0.1f
                    //torque.y=0.4f
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