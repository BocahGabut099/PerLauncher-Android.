use serde::Deserialize;
use serde_json::{json, Value};
use std::path::{Component, Path};

#[derive(Default)]
struct EngineState {
    initialized: bool,
    last_requested_version: Option<String>,
}

#[derive(Deserialize)]
struct LaunchInput {
    version_id: String,
    game_dir: String,
    #[serde(default)]
    assets_dir: Option<String>,
    #[serde(default)]
    libraries_dir: Option<String>,
}

impl EngineState {
    fn reply(&self, ok: bool, status: &str, message: &str) -> String {
        json!({
            "ok": ok,
            "status": status,
            "message": message,
            "engine_version": env!("CARGO_PKG_VERSION"),
            "initialized": self.initialized,
            "runtime_available": false,
            "active_session": false,
            "jvm_free_gameplay": false,
            "last_requested_version": self.last_requested_version.as_deref()
        })
        .to_string()
    }

    fn initialize(&mut self) -> String {
        self.initialized = true;
        self.reply(
            true,
            "ready",
            "Rust JNI lifecycle is initialized; Minecraft runtime is not available yet.",
        )
    }

    fn status(&self) -> String {
        if self.initialized {
            self.reply(
                true,
                "ready",
                "Native bridge is initialized; no native game session is active.",
            )
        } else {
            self.reply(
                true,
                "uninitialized",
                "Native bridge has not been initialized.",
            )
        }
    }

    fn launch(&mut self, request_json: &str) -> String {
        if !self.initialized {
            return self.reply(
                false,
                "not_initialized",
                "Call initializeEngine before requesting a launch.",
            );
        }

        let request: LaunchInput = match serde_json::from_str(request_json) {
            Ok(value) => value,
            Err(_) => {
                return self.reply(
                    false,
                    "invalid_request",
                    "Launch request must be valid JSON with version_id and game_dir.",
                );
            }
        };

        if !valid_version_id(&request.version_id)
            || !valid_absolute_path(&request.game_dir)
            || request
                .assets_dir
                .as_deref()
                .is_some_and(|path| !valid_absolute_path(path))
            || request
                .libraries_dir
                .as_deref()
                .is_some_and(|path| !valid_absolute_path(path))
        {
            return self.reply(
                false,
                "invalid_request",
                "Version ID or one of the supplied paths is invalid.",
            );
        }

        self.last_requested_version = Some(request.version_id);
        self.reply(
            false,
            "runtime_unavailable",
            "Request validated, but the native Minecraft runtime is not implemented. No game process was started.",
        )
    }

    fn stop(&mut self) -> String {
        if !self.initialized {
            return self.reply(
                false,
                "not_initialized",
                "Native bridge is not initialized; there is no session to stop.",
            );
        }

        self.reply(true, "ready", "No native game session is active.")
    }
}

fn valid_version_id(value: &str) -> bool {
    !value.is_empty()
        && value.trim() == value
        && value.len() <= 128
        && value != "."
        && value != ".."
        && !value
            .chars()
            .any(|character| character.is_control() || character == '/' || character == '\\')
}

fn valid_absolute_path(value: &str) -> bool {
    if value.is_empty() || value.trim() != value || value.contains('\0') {
        return false;
    }

    let path = Path::new(value);
    path.is_absolute()
        && !path
            .components()
            .any(|component| matches!(component, Component::ParentDir))
}

pub(crate) fn initialize() -> String {
    with_state(|state| state.initialize())
}

pub(crate) fn status() -> String {
    with_state(|state| state.status())
}

pub(crate) fn launch(request_json: &str) -> String {
    with_state(|state| state.launch(request_json))
}

pub(crate) fn stop() -> String {
    with_state(|state| state.stop())
}

fn with_state<T>(operation: impl FnOnce(&mut EngineState) -> T) -> T {
    use std::sync::{Mutex, OnceLock};

    static STATE: OnceLock<Mutex<EngineState>> = OnceLock::new();
    let mutex = STATE.get_or_init(|| Mutex::new(EngineState::default()));
    let mut state = mutex
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    operation(&mut state)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn response(value: &str) -> Value {
        serde_json::from_str(value).expect("bridge response must be valid JSON")
    }

    #[test]
    fn initialize_is_idempotent() {
        let mut state = EngineState::default();

        let first = response(&state.initialize());
        let second = response(&state.initialize());

        assert_eq!(first["status"], "ready");
        assert_eq!(second["status"], "ready");
        assert_eq!(second["initialized"], true);
    }

    #[test]
    fn launch_requires_initialization() {
        let mut state = EngineState::default();
        let result = response(&state.launch("{}"));

        assert_eq!(result["ok"], false);
        assert_eq!(result["status"], "not_initialized");
    }

    #[test]
    fn traversal_paths_are_rejected() {
        let mut state = EngineState::default();
        state.initialize();

        let request = r#"{"version_id":"1.20.1","game_dir":"/data/user/0/example/../files"}"#;
        let result = response(&state.launch(request));

        assert_eq!(result["ok"], false);
        assert_eq!(result["status"], "invalid_request");
    }

    #[test]
    fn valid_request_does_not_claim_to_start_minecraft() {
        let mut state = EngineState::default();
        state.initialize();

        let request =
            r#"{"version_id":"1.20.1","game_dir":"/data/user/0/example/files/.minecraft"}"#;
        let result = response(&state.launch(request));

        assert_eq!(result["ok"], false);
        assert_eq!(result["status"], "runtime_unavailable");
        assert_eq!(result["runtime_available"], false);
        assert_eq!(result["active_session"], false);
        assert_eq!(result["jvm_free_gameplay"], false);
        assert_eq!(result["last_requested_version"], "1.20.1");
    }

    #[test]
    fn stop_before_initialization_is_reported_safely() {
        let mut state = EngineState::default();
        let result = response(&state.stop());

        assert_eq!(result["ok"], false);
        assert_eq!(result["status"], "not_initialized");
    }
}
