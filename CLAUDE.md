# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview
This is an Android application with a custom 3D graphics engine for simulating dice physics. The project is written in Kotlin and consists of two modules:
- `app`: The Android application demonstrating the engine
- `Engine3D`: A custom 3D graphics engine library with physics simulation

## Build Commands
```bash
# Build the entire project
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Install debug build on connected device
./gradlew installDebug

# Clean build artifacts
./gradlew clean

# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

## Architecture

### Engine3D Module
The custom 3D engine is organized into several key packages:

1. **Rendering Pipeline** (`gpu` package):
   - `GLRenderer`: Core OpenGL ES 3.1 renderer
   - `GLRender3DView`: Android SurfaceView for 3D rendering
   - Shader system with vertex/fragment shader support
   - 3D primitives: GLCube, GLTriangle, GLSquare

2. **Math System** (`math` package):
   - Vector classes: Vec2, Vec3, Vec4 with operator overloading
   - Matrix4x4 for transformations
   - Quaternion support for rotations

3. **Physics System**:
   - `RigidBody`: Physics bodies with forces, torques, and dynamics
   - `BoundingBox`: AABB collision detection
   - Edge collision detection implementation

4. **Scene Management**:
   - `RenderObject`: Base class for all renderable objects
   - Parent-child hierarchical relationships
   - Transform propagation through scene graph

5. **Lighting System**:
   - Directional lighting with diffuse and ambient components
   - Normal-based shading

### App Module
Demonstrates the engine with:
- Interactive 3D cube visualization
- UI controls for real-time manipulation
- Physics simulation with multiple objects

## Key Development Areas

When implementing new features:
1. **3D Objects**: Extend `RenderObject` and implement required methods
2. **Physics**: Use `RigidBody` for dynamics, `BoundingBox` for collisions
3. **Shaders**: Place GLSL files in `src/main/assets/shaders/`
4. **Math Operations**: Use existing Vec/Matrix classes with operator overloading

## Testing
- Unit tests exist for math classes and physics components
- Test files are located in `*/src/test/java/`
- Run specific test class: `./gradlew test --tests "fully.qualified.TestClassName"`