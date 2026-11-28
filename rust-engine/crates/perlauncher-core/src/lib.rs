#![forbid(unsafe_code)]

pub mod account;
pub mod engine;
pub mod paths;
pub mod version;

pub use account::{AccountProvider, PlayerProfile, ProfileId};
pub use engine::{EngineCapabilities, EngineError, EngineKind, LaunchRequest, RuntimeEngine};
pub use paths::{GamePaths, PathError};
pub use version::{GameVersion, LoaderKind, VersionId, VersionMetadata};
