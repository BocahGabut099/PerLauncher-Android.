# Rust Android JNI Bridge — Phase 02

## Scope

This milestone adds a minimal `cdylib` crate named `perlauncher-android`, exporting two JNI methods for `RustEngineBridge`:
- native bridge version;
- a capability JSON marker explicitly stating that gameplay is not implemented.

The Java wrapper is not wired into the launch flow yet. It can be called by a later diagnostic screen or startup self-test.

## Build pipeline

GitHub Actions:
1. installs Android NDK 27.3.13750724;
2. installs Rust stable targets for ARM64, ARMv7, x86, and x86_64 Android;
3. installs `cargo-ndk`;
4. builds `libperlauncher_engine.so` into `app_pojavlauncher/build/rustJniLibs/<abi>/`;
5. verifies all four files;
6. proceeds with the existing Gradle APK build.

Gradle includes that generated directory as a JNI library source directory.

## Migration safety

The existing `Android.mk`, Pojav native libraries, Java launch path, JRE download step, account code, and user data remain unchanged. The final Rust-only goal is not reached by this bridge milestone.
