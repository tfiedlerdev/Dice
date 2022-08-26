package tfdev.dice

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import sensors_in_paradise.sonar.custom_views.stickman.object3d.Cube
import tfdev.engine3d.gpu.GLRender3DView
import tfdev.engine3d.gpu.gl_object3d.GLScene

class MainActivity : AppCompatActivity(), OnSeekBarChangeListener {
    lateinit var seekBar1: SeekBar
    lateinit var seekBar2: SeekBar
    lateinit var seekBar3: SeekBar
    //lateinit var renderView: Render3DView
    val child = Cube(lineColor = Color.BLUE)
    val cube = Cube()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scene = GLScene()
        setContentView(GLRender3DView(this,scene))
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