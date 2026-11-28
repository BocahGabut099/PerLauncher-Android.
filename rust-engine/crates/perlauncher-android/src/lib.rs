mod runtime;

use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use std::ptr;

const CAPABILITIES: &str = r#"{"bridge":"jni","runtime_api":"lifecycle-v1","game_engine":"not_implemented","runtime_available":false,"jvm_free_gameplay":false,"target_abis":["arm64-v8a","armeabi-v7a","x86","x86_64"]}"#;

fn to_java_string(env: JNIEnv<'_>, value: String) -> jstring {
    match env.new_string(value) {
        Ok(java_value) => java_value.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_net_kdt_pojavlaunch_engine_RustEngineBridge_nativeEngineVersion(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    to_java_string(env, env!("CARGO_PKG_VERSION").to_owned())
}

#[no_mangle]
pub extern "system" fn Java_net_kdt_pojavlaunch_engine_RustEngineBridge_nativeCapabilitiesJson(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    to_java_string(env, CAPABILITIES.to_owned())
}

#[no_mangle]
pub extern "system" fn Java_net_kdt_pojavlaunch_engine_RustEngineBridge_nativeInitialize(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    to_java_string(env, runtime::initialize())
}

#[no_mangle]
pub extern "system" fn Java_net_kdt_pojavlaunch_engine_RustEngineBridge_nativeStatus(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    to_java_string(env, runtime::status())
}

#[no_mangle]
pub extern "system" fn Java_net_kdt_pojavlaunch_engine_RustEngineBridge_nativeLaunch(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    request_json: JString<'_>,
) -> jstring {
    let request = match env.get_string(&request_json) {
        Ok(value) => value.to_string_lossy().into_owned(),
        Err(_) => return ptr::null_mut(),
    };

    to_java_string(env, runtime::launch(&request))
}

#[no_mangle]
pub extern "system" fn Java_net_kdt_pojavlaunch_engine_RustEngineBridge_nativeStop(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jstring {
    to_java_string(env, runtime::stop())
}
