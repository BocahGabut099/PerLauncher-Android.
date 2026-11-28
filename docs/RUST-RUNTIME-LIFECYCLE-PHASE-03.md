# Phase 03 — Rust Runtime Lifecycle API

## Scope

This phase extends the Phase 02 JNI bridge with a small, thread-safe lifecycle controller.
The controller exposes `initialize`, `status`, `launch`, and `stop` operations to Java.

It does **not** implement Minecraft rendering, asset loading, networking, authentication,
a game process, or a native Minecraft runtime. A valid launch request returns
`runtime_unavailable`, and `active_session` remains false.

## Java API

Class: `net.kdt.pojavlaunch.engine.RustEngineBridge`

- `initializeEngine()` — initialize the native controller.
- `getStatusJson()` — read the current controller state.
- `launchGame(String requestJson)` — validate a request and report runtime availability.
- `stopGame()` — stop a native session if a backend exists.
- `isAvailable()` — report whether `libperlauncher_engine.so` loaded.

All lifecycle methods return JSON strings. When the shared library cannot load, Java
returns a `bridge_unavailable` result (or `null` for the two original metadata methods).

## Launch request schema

```json
{
  "version_id": "1.20.1",
  "game_dir": "/data/user/0/example/files/.minecraft",
  "assets_dir": "/data/user/0/example/files/.minecraft/assets",
  "libraries_dir": "/data/user/0/example/files/.minecraft/libraries"
}
```

`assets_dir` and `libraries_dir` are optional. Supplied paths must be absolute and must
not contain parent-directory traversal. The version ID must be non-empty, bounded, and
must not contain path separators or control characters.

No account credentials, access tokens, or passwords belong in this request.

## Current launch behavior

After initialization and validation, the bridge returns:

- `ok: false`
- `status: "runtime_unavailable"`
- `runtime_available: false`
- `active_session: false`
- `jvm_free_gameplay: false`

No process is spawned. The old Pojav/JVM path remains untouched.

## Tests

Unit tests cover initialization, calling launch before initialization, rejecting traversal
paths, accurately reporting the missing runtime, and stopping before initialization.

The Android ABI `.so` build and APK packaging still need to be confirmed by GitHub Actions.
