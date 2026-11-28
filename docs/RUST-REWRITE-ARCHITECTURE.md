# PerLauncher Rust — Full Rewrite Architecture

Status: Phase 01 architecture baseline. Target: Android first, Windows later.
Build: GitHub Actions. Runtime constraint: **no JVM in the game execution path**.

## Product boundary
PerLauncher is the product shell: home, account management, version selection, modpacks,
settings, downloads, storage, and navigation. The game runtime is native Rust.

Android Java/Kotlin may be used as a thin OS Activity/permission bridge, but it must not
execute Minecraft Java code, start a JVM, or invoke Pojav's game runtime. The current
Pojav source is retained during migration only; it is not a final fallback.

## Proposed layout
```text
app_pojavlauncher/                 Existing Android UI during migration
  src/main/java/.../rust/          Thin Android-to-Rust bridge (later)
  src/main/jniLibs/
    arm64-v8a/libhorizon_android.so
    armeabi-v7a/libhorizon_android.so
rust-engine/
  crates/perlauncher-core/         Versions, profiles, paths, launch contracts
  crates/perlauncher-runtime/      Runtime abstraction (later)
  crates/perlauncher-starling/     Pinned Starling adapter (later)
  crates/perlauncher-compat/       Legacy version/protocol compatibility (later)
  crates/perlauncher-mods/         Rust mod/plugin compatibility (later)
  crates/perlauncher-render/       GLES/Vulkan backend selection (later)
docs/
  RUST-REWRITE-ARCHITECTURE.md
  RUST-COMPATIBILITY-MATRIX.md
```

## Engine layers

### 1. Launcher domain (`perlauncher-core`)
Platform-neutral data: game version, loader, profile identity, game paths, launch request,
engine capability checks, and structured errors. No Android UI and no credentials.

### 2. Runtime adapter
A stable contract for capability discovery, launch validation, preparation, start, stop,
and session reporting. An adapter that requires JVM execution is incompatible with the
final product.

### 3. Starling adapter
Starling v0.9.0 is the first candidate because it already contains a Rust game client and
Android packaging. It is not proof of universal Minecraft compatibility. Pin a reviewed
tag/commit. Build native Android shared libraries, package required Activity/resources/
assets, and route PerLauncher-controlled launch requests into the engine. If the upstream
API cannot do this cleanly, maintain a narrow documented adapter/fork.

### 4. Legacy compatibility
Old Minecraft versions require more than selecting an older JAR: protocol codecs, asset
formats, gameplay logic, rendering, sound, networking, authentication, and version-specific
behavior may differ. Advertise each version only after repeatable gameplay tests.

### 5. Mod compatibility
Forge/Fabric/Quilt/NeoForge often depend on Java bytecode, transformers, mixins, and JVM APIs.
A Rust-only engine cannot promise compatibility by merely recognizing a loader name.
Options are a native Rust mod API, selective Rust reimplementation of public APIs, or a
separately evaluated non-JVM bytecode compatibility layer. Mods requiring unavailable JVM
behavior must be explicitly unsupported. Never silently start a JVM.

## Android packaging
GitHub Actions builds `aarch64-linux-android` → `arm64-v8a` and
`armv7-linux-androideabi` → `armeabi-v7a`. Pin Rust, NDK, Starling ref, and Cargo lockfiles.
The APK needs the native `.so`, Activity bridge, resources, and assets. Test 32-bit ARM
separately; a Rust target existing does not prove device support. 1 GB RAM devices are a
high-risk target and require real-device measurements.

## Accounts and secrets
PerLauncher owns account UI. Rust receives short-lived session material through a private
in-process bridge or secure IPC. Microsoft uses supported OAuth and ownership checks.
Ely.by uses supported OAuth/token refresh. Offline identities do not grant online-mode
server access. Never persist raw passwords/OTP, log tokens, or pass credentials in shell
arguments. Prefer Android Keystore for long-lived secrets. Account keys include provider ID.

## Storage
Preserve the existing game directory where possible. Keep Rust runtime data separate from
worlds and launcher configuration. Never migrate worlds in-place: copy, verify, and retain
rollback until confirmed. Existing backups are not touched.

## Release gates
- Native libraries exist for every advertised ABI.
- Launch path starts no JVM and invokes no Pojav Java game runtime.
- Microsoft/Ely.by/offline account tests pass.
- World save/load, clean exit, networking, and server session tests pass.
- Low-memory behavior is measured on real devices.
- Every advertised version/loader has a test-backed compatibility status.

## Milestones
| Stage | Deliverable | Exit condition |
|---|---|---|
| P0 | Architecture and core contracts | Core tests pass |
| P1 | Starling Android native build | `.so` built for target ABIs |
| P2 | PerLauncher bridge | Existing launcher starts/stops Rust |
| P3 | Account bridge | Provider tests pass; no secret leakage |
| P4 | Version catalog | Accurate capability matrix |
| P5 | Legacy compatibility | Specific old versions pass gameplay |
| P6 | Mod ecosystem | Loader/mod tests pass |
| P7 | Remove old runtime | No JVM/Pojav game execution path |
| P8 | Windows | Shared core with Windows backend |

## Phase 01 exclusions
No changes to Android UI, account code, manifest, Gradle, GitHub Actions, or existing backups.
No APK claim and no claim of full version/mod compatibility.
