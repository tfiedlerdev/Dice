package sensors_in_paradise.sonar.custom_views.stickman.math

import android.opengl.Matrix

class Matrix4x4(private val data: FloatArray) {
    constructor() : this(
        FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    )

    constructor(
        col1: FloatArray,
        col2: FloatArray,
        col3: FloatArray,
        col4: FloatArray
    ) : this(FloatArray(16).apply {
        val a = col1 + col2 + col3 + col4
        for (i in 0 until 16) {
            this[i] = a[i]
        }
    })

    init {
        if (data.size != 16) {
            throw InvalidSizeException("Matrix data size is invalid. Must be 16 but is ${data.size}")
        }
    }

    fun getCol(col: Int): FloatArray {
        return when (col) {
            0 -> data.copyOfRange(0, 4)
            1 -> data.copyOfRange(4, 8)
            2 -> data.copyOfRange(8, 12)
            3 -> data.copyOfRange(12, 16)
            else -> throw IndexOutOfBoundsException("Col-Index must be 0 <= index <= 3")
        }
    }

    override operator fun equals(other: Any?): Boolean {
        if (other is Matrix4x4) {
            for (i in data.indices) {
                if (other.data[i] != data[i]) {
                    return false
                }
            }
            return true
        }
        return false
    }

    operator fun get(row: Int, col: Int): Float {
        return data[col * 4 + row]
    }

    operator fun set(row: Int, col: Int, value: Float) {
        data[col * 4 + row] = value
    }

    fun getRow(row: Int): FloatArray {
        return FloatArray(4).apply {
            this[0] = this@Matrix4x4[0, row]
            this[1] = this@Matrix4x4[1, row]
            this[2] = this@Matrix4x4[2, row]
            this[3] = this@Matrix4x4[3, row]
        }
    }

    operator fun times(v: Float): Matrix4x4 {
        val result = Matrix4x4()
        for((index, value) in this.data.withIndex()){
            result.data[index] = value * v
        }
        return result
    }

    operator fun times(p: Vec4): Vec4 {
        val res = Vec4()
        for (row in 0..3) {
            var sum = 0f
            for (col in 0..3) {
                sum += this[row, col] * p[col]
            }
            res[row] = sum
        }
        return res
    }

    operator fun times(m: Matrix4x4): Matrix4x4 {
        val data = FloatArray(16)
        Matrix.multiplyMM(data, 0, this.data, 0, m.data, 0)
        return Matrix4x4(data)
    }

    fun clone(): Matrix4x4 {

        return Matrix4x4(
            data.clone()
        )
    }

    fun assign(m: Matrix4x4) {
        m.data.copyInto(data)
    }

    fun data(): FloatArray {
        return data
    }

    fun asString(): String {
        var s = ""
        for (row in 0..3) {
            for (col in 0..3) {
                s += " " + this[row, col]
            }
            s += "\n"
        }
        return s
    }

    fun rotateY(degrees: Float) {
        rotate(degrees, 0f, 1f, 0f)
    }

    fun rotateX(degrees: Float) {
        rotate(degrees, 1f, 0f, 0f)
    }

    fun rotate(degrees: Float, xFactor: Float, yFactor: Float, zFactor: Float) {
        // switch z and x so that we get the correct operation for our coordinate system
        Matrix.rotateM(data, 0, degrees, zFactor, yFactor, xFactor)
    }

    fun scale(x: Float, y: Float, z: Float) {
        Matrix.scaleM(this.data, 0, x, y, z)
    }

    fun translate(x: Float, y: Float, z: Float) {
        Matrix.translateM(this.data, 0, x, y, z)
    }

    fun inverse() {
        Matrix.invertM(this.data, 0, this.data, 0)
    }

    fun inverseClone(): Matrix4x4 {
        val m = Matrix4x4()
        this.inverse(m)
        return m
    }

    fun inverse(target: Matrix4x4) {
        Matrix.invertM(target.data, 0, this.data, 0)
    }

    fun transpose() {
        Matrix.transposeM(this.data, 0, this.data, 0)
    }

    fun transpose(target: Matrix4x4) {
        Matrix.transposeM(target.data, 0, this.data, 0)
    }

    fun transposeClone(): Matrix4x4 {
        val m = Matrix4x4()
        Matrix.transposeM(m.data, 0, this.data, 0)
        return m
    }

    /**
     * Length of column [col] (0..2) of the upper-left 3x3 part of this matrix.
     * For a matrix built as translate * rotate * scale this is the world-space
     * scale factor applied along that local axis.
     */
    fun getColumnLength3(col: Int): Float {
        val x = this[0, col]
        val y = this[1, col]
        val z = this[2, col]
        return kotlin.math.sqrt(x * x + y * y + z * z)
    }

    /**
     * Unit-length direction of column [col] (0..2) of the upper-left 3x3 part
     * of this matrix, i.e. the world-space direction of local axis [col].
     */
    fun getAxis3(col: Int): Vec3 {
        val len = getColumnLength3(col)
        if (len < 1e-8f) {
            // Degenerate (zero scale along this axis) - fall back to a standard basis vector.
            return when (col) {
                0 -> Vec3(1f, 0f, 0f)
                1 -> Vec3(0f, 1f, 0f)
                else -> Vec3(0f, 0f, 1f)
            }
        }
        return Vec3(this[0, col] / len, this[1, col] / len, this[2, col] / len)
    }

    /**
     * Re-orthonormalizes the upper-left 3x3 rotation part of this matrix using
     * Gram-Schmidt. Repeatedly integrating a rotation matrix step by step
     * (as rigid body simulation does) accumulates floating point drift that
     * slowly turns it into a non-orthogonal / non-unit-scale matrix; calling
     * this periodically keeps it a valid rotation.
     */
    fun orthonormalize() {
        var xAxis = Vec3(this[0, 0], this[1, 0], this[2, 0])
        var yAxis = Vec3(this[0, 1], this[1, 1], this[2, 1])
        var zAxis: Vec3

        xAxis = xAxis.normalize()
        zAxis = xAxis.cross(yAxis).normalize()
        yAxis = zAxis.cross(xAxis).normalize()

        this[0, 0] = xAxis.x; this[1, 0] = xAxis.y; this[2, 0] = xAxis.z
        this[0, 1] = yAxis.x; this[1, 1] = yAxis.y; this[2, 1] = yAxis.z
        this[0, 2] = zAxis.x; this[1, 2] = zAxis.y; this[2, 2] = zAxis.z
    }

    override fun hashCode(): Int {
        return data.contentHashCode()
    }

    companion object {
        fun fromRows(
            row1: FloatArray,
            row2: FloatArray,
            row3: FloatArray,
            row4: FloatArray
        ): Matrix4x4 {
            val data = FloatArray(16)
            for (i in 0..3) {
                data[i * 4 + 0] = row1[i]
                data[i * 4 + 1] = row2[i]
                data[i * 4 + 2] = row3[i]
                data[i * 4 + 3] = row4[i]
            }
            return Matrix4x4(data)
        }

        fun lookAt(
            eye: Vec3,
            center: Vec3,
            up: Vec3,
            m: Matrix4x4? = null
        ): Matrix4x4 {
            val data = m?.data ?: FloatArray(16)
            Matrix.setLookAtM(
                data,
                0,
                eye.x,
                eye.y,
                eye.z,
                center.x,
                center.y,
                center.z,
                up.x,
                up.y,
                up.z
            )
            return m ?: Matrix4x4(data)
        }

        fun project(m: Matrix4x4, fovy: Float, aspect: Float, zNear: Float, zFar: Float) {
            Matrix.perspectiveM(m.data, 0, fovy, aspect, zNear, zFar)
        }

        fun project(fovy: Float, aspect: Float, zNear: Float, zFar: Float): Matrix4x4 {
            val data = FloatArray(16)
            Matrix.perspectiveM(data, 0, fovy, aspect, zNear, zFar)
            return Matrix4x4(data)
        }

        fun rotate(degrees: Float, x: Float, y: Float, z: Float): Matrix4x4 {
            val data = FloatArray(16)
            Matrix.setRotateM(data, 0, degrees, x, y, z)
            return Matrix4x4(data)
        }

        fun rotateEuler(xDegrees: Float, yDegrees: Float, zDegrees: Float): Matrix4x4 {
            // switch z and x so that we get the correct operation for our coordinate system
            return rotate(xDegrees, 0f, 0f, 1f) * rotate(yDegrees, 0f, 1f, 0f) * rotate(
                zDegrees,
                1f,
                0f,
                0f
            ) // Matrix4x4(data)
        }

        /**
         * Builds a pure rotation matrix from an axis-angle representation
         * (Rodrigues' rotation formula). [axis] must be unit length.
         */
        fun rotateAxisAngle(angleRadians: Float, axis: Vec3): Matrix4x4 {
            val c = kotlin.math.cos(angleRadians)
            val s = kotlin.math.sin(angleRadians)
            val t = 1f - c
            val x = axis.x
            val y = axis.y
            val z = axis.z

            return fromRows(
                floatArrayOf(t * x * x + c, t * x * y - s * z, t * x * z + s * y, 0f),
                floatArrayOf(t * x * y + s * z, t * y * y + c, t * y * z - s * x, 0f),
                floatArrayOf(t * x * z - s * y, t * y * z + s * x, t * z * z + c, 0f),
                floatArrayOf(0f, 0f, 0f, 1f)
            )
        }

        /**Taken from http://www.cs.cmu.edu/~baraff/sigcourse/notesd1.pdf */
        fun star(v: Vec4): Matrix4x4 {
            return Matrix4x4(
                floatArrayOf(
                    0f,
                    v[2],
                    -v[1],
                    0f
                ),
                floatArrayOf(
                    -v[2],
                    0f,
                    v[0],
                    0f
                ),
                floatArrayOf(
                    v[1],
                    -v[0],
                    0f,
                    0f
                ),
                floatArrayOf(
                    0f,
                    0f,
                    0f,
                    1f
                ),
            )
        }
    }
}
