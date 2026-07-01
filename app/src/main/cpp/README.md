# GGUF / llama.cpp native engine

This wires a full llama.cpp runtime (NDK + CMake, built from source) so Quiver can
run **any GGUF model** with **grammar-constrained JSON** for reliable tool-calls.

It's **inactive until you add the submodule** — `app/build.gradle.kts` only enables
`externalNativeBuild` when `app/src/main/cpp/llama.cpp/CMakeLists.txt` exists, and
`LlamaCppEngine.available` is false until `libllama-android.so` loads. So the
current build is unaffected until you do the steps below.

## 1. Add the llama.cpp source (pin a tag)
```bash
git submodule add https://github.com/ggml-org/llama.cpp app/src/main/cpp/llama.cpp
cd app/src/main/cpp/llama.cpp
git checkout b6100        # pin a known-good tag, then commit the submodule
```

## 2. Ensure NDK + CMake are installed
In `local.properties` (or SDK Manager): NDK r26+ and CMake 3.22.1. The app already
targets `arm64-v8a` only (`ndk { abiFilters += "arm64-v8a" }`), which keeps the
build fast and the APK small.

## 3. Build
```bash
JAVA_HOME="<Android Studio JBR>" ./gradlew.bat assembleDebug
```
The first native build is slow (compiles ggml/llama). Subsequent builds are cached.

## 4. Use it
Import or download a `.gguf` model in Quiver AI → the factory
(`InferenceEngines`) routes it to `LlamaCppEngine`. Recommended small,
tool-capable GGUFs: Qwen2.5-1.5B/3B-Instruct, Llama-3.2-3B-Instruct, Hammer2.1.

To force valid tool JSON, pass the grammar when the agent constructs the engine:
`LlamaCppEngine(context, path, grammar = <contents of training/grammar/quiver_tools.gbnf>)`.

## Notes
- The JNI in `llama-android.cpp` targets a recent llama.cpp C API. If it fails to
  compile after a submodule bump, the symbols most likely to have moved are noted
  inline (e.g. `llama_memory_clear` vs older `llama_kv_cache_clear`). Adjust to
  match your pinned tag.
- `.so` files aren't checked in — they're produced by the NDK build.
