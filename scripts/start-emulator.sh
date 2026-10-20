#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
IMAGE="$SDK/system-images/android-31/android-tv/arm64-v8a"
[[ -f "$IMAGE/system.img" ]] || { echo "Install system-images;android-31;android-tv;arm64-v8a with SDK Manager first."; exit 1; }
AVD_ROOT="$ROOT/.emulator"
mkdir -p "$AVD_ROOT/StillTV_API31.avd"
if [[ ! -f "$AVD_ROOT/StillTV_API31.avd/config.ini" ]]; then
  cat > "$AVD_ROOT/StillTV_API31.ini" <<CONFIG
avd.ini.encoding=UTF-8
path=$AVD_ROOT/StillTV_API31.avd
target=android-31
CONFIG
  cat > "$AVD_ROOT/StillTV_API31.avd/config.ini" <<CONFIG
AvdId=StillTV_API31
avd.ini.displayname=Still TV API 31
avd.ini.encoding=UTF-8
abi.type=arm64-v8a
hw.cpu.arch=arm64
hw.cpu.ncore=2
hw.ramSize=1536
hw.lcd.width=1920
hw.lcd.height=1080
hw.lcd.density=320
hw.dPad=yes
hw.keyboard=yes
hw.mainKeys=yes
hw.touchScreen=no
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.audioInput=no
hw.camera.back=none
hw.camera.front=none
hw.sdCard=no
disk.dataPartition.size=2G
image.sysdir.1=$IMAGE/
tag.id=android-tv
tag.display=Android TV
PlayStore.enabled=false
showDeviceFrame=no
CONFIG
fi
export ANDROID_AVD_HOME="$AVD_ROOT"
exec "$SDK/emulator/emulator" -avd StillTV_API31 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -port 5554
