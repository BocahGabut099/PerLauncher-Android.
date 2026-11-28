use std::path::{Component, Path, PathBuf};

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GamePaths {
    pub game_dir: PathBuf,
    pub assets_dir: PathBuf,
    pub runtime_dir: PathBuf,
}

impl GamePaths {
    pub fn new(
        game_dir: PathBuf,
        assets_dir: PathBuf,
        runtime_dir: PathBuf,
    ) -> Result<Self, PathError> {
        for path in [&game_dir, &assets_dir, &runtime_dir] {
            if !is_clean_absolute_path(path) {
                return Err(PathError::InvalidPath);
            }
        }
        Ok(Self {
            game_dir,
            assets_dir,
            runtime_dir,
        })
    }

    pub fn game_dir(&self) -> &Path {
        &self.game_dir
    }
}

fn is_clean_absolute_path(path: &Path) -> bool {
    path.is_absolute()
        && path
            .components()
            .all(|c| !matches!(c, Component::ParentDir | Component::CurDir))
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, thiserror::Error)]
pub enum PathError {
    #[error("paths must be absolute and contain no traversal components")]
    InvalidPath,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_relative_and_parent_paths() {
        assert!(GamePaths::new("game".into(), "/assets".into(), "/runtime".into()).is_err());
        assert!(
            GamePaths::new("/game/../other".into(), "/assets".into(), "/runtime".into()).is_err()
        );
    }

    #[test]
    fn accepts_clean_absolute_paths() {
        let paths = GamePaths::new(
            "/storage/emulated/0/Android/data/example/files/.minecraft".into(),
            "/storage/emulated/0/Android/data/example/files/assets".into(),
            "/data/user/0/example/files/rust-runtime".into(),
        )
        .unwrap();
        assert!(paths.game_dir().is_absolute());
    }
}
