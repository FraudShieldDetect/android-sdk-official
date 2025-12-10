#!/bin/bash
set -e

echo "===== Android ProtoSDK Build ====="
echo ""

echo "Building Android APK..."
echo ""

./gradlew --no-daemon :app:assembleDebug

if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo ""
    echo "===== BUILD SUCCESSFUL ====="
    echo ""
    ls -lh app/build/outputs/apk/debug/app-debug.apk
else
    echo ""
    echo "===== BUILD FAILED ====="
    exit 1
fi
