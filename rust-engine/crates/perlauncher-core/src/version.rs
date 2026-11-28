use serde::{Deserialize, Serialize};
use std::fmt;

#[derive(Clone, Debug, Eq, PartialEq, Hash, Serialize, Deserialize)]
#[serde(transparent)]
pub struct VersionId(String);

impl VersionId {
    pub fn parse(value: impl Into<String>) -> Result<Self, VersionError> {
        let value = value.into();
        if value.is_empty()
            || value.len() > 96
            || !value
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b'_' | b'-' | b'+'))
        {
            return Err(VersionError::InvalidId);
        }
        Ok(Self(value))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl fmt::Display for VersionId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.0)
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum LoaderKind {
    Vanilla,
    Forge,
    Fabric,
    Quilt,
    NeoForge,
    PerLauncherNativeMod,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct GameVersion {
    pub id: VersionId,
    pub game_version: String,
    pub loader: LoaderKind,
    pub loader_version: Option<String>,
}

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct VersionMetadata {
    pub version: GameVersion,
    pub engine_id: String,
    pub supported: bool,
    pub limitations: Vec<String>,
}

#[derive(Debug, thiserror::Error, Eq, PartialEq)]
pub enum VersionError {
    #[error("invalid version identifier")]
    InvalidId,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_path_traversal() {
        assert!(VersionId::parse("../latest").is_err());
        assert!(VersionId::parse("1.20.1").is_ok());
    }

    #[test]
    fn metadata_round_trips() {
        let value = VersionMetadata {
            version: GameVersion {
                id: VersionId::parse("1.20.1-fabric").unwrap(),
                game_version: "1.20.1".into(),
                loader: LoaderKind::Fabric,
                loader_version: Some("0.15.11".into()),
            },
            engine_id: "perlauncher-starling".into(),
            supported: false,
            limitations: vec!["Not implemented yet".into()],
        };
        let json = serde_json::to_string(&value).unwrap();
        assert_eq!(
            serde_json::from_str::<VersionMetadata>(&json).unwrap(),
            value
        );
    }
}
