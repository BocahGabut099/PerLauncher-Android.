# PerLauncher Rust Compatibility Matrix

Do not advertise a feature as supported until a repeatable test passes.

| Feature | Status | Evidence required |
|---|---|---|
| Starling baseline client | Candidate | Pinned source, native build, gameplay test |
| Minecraft 26.3 via Starling | Candidate; verify exact tag | Version-specific launch test |
| Older vanilla versions | Not implemented | Per-version launch/gameplay suite |
| Microsoft | Needs bridge | OAuth, refresh, entitlement/session tests |
| Ely.by | Needs bridge | OAuth, refresh, profile/session tests |
| Offline profile | Runtime-specific | Local launch + offline-mode server test |
| Forge | Not implemented | Loader bootstrap and representative mods |
| Fabric | Not implemented | Loader bootstrap and representative mods |
| Quilt | Not implemented | Loader bootstrap and representative mods |
| NeoForge | Not implemented | Loader bootstrap and representative mods |
| Resource packs | Candidate | Import, activate, render, remove tests |
| Shader packs | Unknown | Backend/shader compatibility tests |
| ARM64 Android | Candidate | CI artifact + physical device test |
| ARMv7 Android | Candidate | CI artifact + physical device test |
| 1 GB RAM device | High risk | Launch, gameplay, OOM, thermal measurements |
| Windows | Later | Native Windows runtime and packaging |

Statuses: Not implemented = absent; Candidate = upstream may exist but not integrated;
In progress = implementation exists but tests incomplete; Verified = automated and device
tests pass; Unsupported = intentionally unavailable in the current Rust-only architecture.
