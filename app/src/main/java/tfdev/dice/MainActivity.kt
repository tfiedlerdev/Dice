package tfdev.dice

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import sensors_in_paradise.sonar.custom_views.stickman.object3d.Cube
import tfdev.engine3d.gpu.GLRender3DView

class MainActivity : AppCompatActivity(), OnSeekBarChangeListener {
    lateinit var seekBar1: SeekBar
    lateinit var seekBar2: SeekBar
    lateinit var seekBar3: SeekBar
    //lateinit var renderView: Render3DView
    val child = Cube(lineColor = Color.BLUE)
    val cube = Cube()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(GLRender3DView(this))

        /*
        //renderView = findViewById(R.id.renderView_activityMain)
        seekBar1 = findViewById(R.id.seekBar1)
        seekBar2 = findViewById(R.id.seekBar2)
        seekBar3 = findViewById(R.id.seekBar3)
        renderView.enableYRotation = true
        renderView.showFPS = true
        child.apply {
            scale.apply {
                x = 0.25f
                y = 0.25f
                z = 0.25f
            }
        }
        child.setDirty()



        seekBar1.setOnSeekBarChangeListener(this)
        seekBar2.setOnSeekBarChangeListener(this)
        seekBar3.setOnSeekBarChangeListener(this)

        cube.addChild(child)
        //renderView.addObject3D(cube)
        //renderView.scene.forceUpdateSelfAndChild()
        Log.d("Child Cube Model Matrix", child.modelMatrix.asString())



         */
    }

    override fun onProgressChanged(seekBar: SeekBar?, p: Int, p2: Boolean) {
        val percentage = p.toFloat() / 100f
        when (seekBar) {
            seekBar1 -> {
                child.apply {
                    scale.apply {
                        x = percentage
                        y = percentage
                        z = percentage
                    }
                    setDirty()
                }
            }
            seekBar2 -> {
                cube.apply {
                    scale.apply {
                        x = percentage
                        y = percentage
                        z = percentage
                    }
                    setDirty()
                }
            }
            seekBar3 -> {
                cube.apply {
                    eulerRotDeg.y = percentage * 360f
                    setDirty()
                }
            }

        }
        //renderView.onObjectChanged()

    }

    override fun onStartTrackingTouch(p0: SeekBar?) {

    }

    override fun onStopTrackingTouch(p0: SeekBar?) {

    }
}