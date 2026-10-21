#!/usr/bin/env bash
set -euo pipefail

apkPath="app/build/outputs/apk/release/app-release.apk"
./gradlew testDebugUnitTest lintRelease assembleRelease

if [[ ! -f "$apkPath" ]]; then
    echo "未找到 release APK。" >&2
    exit 1
fi

# ponytail: 当前版本号为 Gradle 中的字面量；改为动态计算时再从 APK 元数据读取。
versionName="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)"/\1/p' app/build.gradle.kts)"
sdkDir="$(sed -n 's/^sdk\.dir=//p' local.properties)"
apksignerPath="$(find "$sdkDir/build-tools" -mindepth 2 -maxdepth 2 -type f -name apksigner | sort | tail -n 1)"

if [[ -z "$versionName" || -z "$apksignerPath" ]]; then
    echo "无法读取版本号或 apksigner。" >&2
    exit 1
fi

"$apksignerPath" verify --verbose --print-certs "$apkPath"

releaseDir="release"
mkdir -p "$releaseDir"
apkName="TimeLogger-v${versionName}.apk"

cp app/build/outputs/apk/release/app-release.apk \
  "$releaseDir/$apkName"

(
    cd "$releaseDir"
    shasum -a 256 "$apkName" > SHA256SUMS
)
