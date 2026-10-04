#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="${DOIT_MAC_APP:-$ROOT/dist/Do It.app}"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$ROOT/macos/Info.plist" "$APP/Contents/Info.plist"
rm -rf "$APP/Contents/Resources/web"
cp -R "$ROOT/web" "$APP/Contents/Resources/web"
swiftc -O -swift-version 5 -target arm64-apple-macos14.0 -framework Cocoa -framework WebKit -framework UserNotifications "$ROOT/macos/Main.swift" -o "$APP/Contents/MacOS/DoIt"
if [ -f "$ROOT/macos/AppIcon.icns" ]; then cp "$ROOT/macos/AppIcon.icns" "$APP/Contents/Resources/AppIcon.icns"; fi
codesign --force --sign - --identifier com.doit.app "$APP"
echo "$APP"
