package sensors_in_paradise.sonar.custom_views.stickman.math

open class Vec3(values: FloatArray) : VecX<Vec3>(values, 3) {
    constructor(vec3: Vec3) : this(vec3.x, vec3.y, vec3.z)
    constructor() : this(0f, 0f, 0f)
    constructor(x: Float, y: Float, z: Float) : this(floatArrayOf(x, y, z))

    var x: Float
        get() = this[0]
        set(value) {
            this[0] = value
        }
    var y: Float
        get() = this[1]
        set(value) {
            this[1] = value
        }
    var z: Float
        get() = this[2]
        set(value) {
            this[2] = value
        }

    override fun clone(): Vec3 {
        return Vec3(this)
    }
    
    /**
     * Calculate the length (magnitude) of the vector
     */
    fun length(): Float {
        return kotlin.math.sqrt(lengthSquared())
    }
    
    /**
     * Calculate the squared length of the vector (faster than length())
     */
    fun lengthSquared(): Float {
        return x * x + y * y + z * z
    }
    
    /**
     * Calculate the cross product with another vector
     */
    fun cross(other: Vec3): Vec3 {
        return Vec3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )
    }
    
    /**
     * Calculate the dot product with another vector
     */
    infix fun dot(other: Vec3): Float {
        return x * other.x + y * other.y + z * other.z
    }
    
    /**
     * Normalize the vector (make it unit length)
     */
    fun normalize(): Vec3 {
        val len = length()
        return if (len > 1e-6f) {
            this / len
        } else {
            Vec3(0f, 0f, 0f)
        }
    }
}
