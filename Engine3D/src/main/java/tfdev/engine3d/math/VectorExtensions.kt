package sensors_in_paradise.sonar.custom_views.stickman.math

// Cross product for Vec3
infix fun Vec3.cross(other: Vec3): Vec3 {
    return Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )
}

// Cross product for Vec4 (ignores w component)
infix fun Vec4.cross(other: Vec4): Vec4 {
    return Vec4(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
        0f  // Cross product result has w=0 as it's a vector, not a point
    )
}