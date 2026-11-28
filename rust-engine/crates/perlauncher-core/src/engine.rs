use crate::{account::PlayerProfile, paths::GamePaths, version::GameVersion};
use serde::{Deserialize, Serialize};

#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum EngineKind {
    Starling,
    PerLauncherNative,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct EngineCapabilities {
    pub engine: EngineKind,
    pub supported_game_versions: Vec<String>,
    pub supported_loaders: Vec<String>,
    pub supports_offline_profiles: bool,
    pub supports_resource_packs: bool,
    pub supports_shader_packs: bool,
    pub android_abis: Vec<String>,
}

#[derive(Clone, Debug)]
pub struct LaunchRequest {
    pub version: GameVersion,
    pub profile: PlayerProfile,
    pub paths: GamePaths,
    pub max_memory_mib: Option<u32>,
}

#[derive(Debug, thiserror::Error, Eq, PartialEq)]
pub enum EngineError {
    #[error("requested game version is not supported by this engine")]
    UnsupportedVersion,
    #[error("requested mod loader is not supported by this engine")]
    UnsupportedLoader,
    #[error("profile provider is not supported by this engine")]
    UnsupportedAccountProvider,
    #[error("game paths are invalid")]
    InvalidPaths,
    #[error("native runtime failed: {0}")]
    Runtime(String),
}

/// Contract for a native Rust runtime. Implementations must not spawn a JVM.
pub trait RuntimeEngine: Send + Sync {
    fn capabilities(&self) -> EngineCapabilities;
    fn validate_launch(&self, request: &LaunchRequest) -> Result<(), EngineError>;
    fn launch(&self, request: LaunchRequest) -> Result<EngineSession, EngineError>;
    fn stop(&self, session: EngineSession) -> Result<(), EngineError>;
}

/// Opaque handle only. Never put credentials or access tokens here.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EngineSession {
    pub id: String,
    pub engine: EngineKind,
}
