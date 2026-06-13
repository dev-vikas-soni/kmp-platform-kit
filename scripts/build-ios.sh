#!/bin/bash
# /**
#  * This script builds the iOS Release XCFramework for the shared module.
#  * It cleans previous builds and assembles the new XCFramework.
#  *
#  * Usage:
#  *   ./scripts/build-ios.sh
#  *
#  * Output:
#  *   shared/build/XCFrameworks/release/Shared.xcframework
#  */
set -e

echo "🚀 Building iOS Release XCFramework..."

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
cd "$ROOT_DIR"

./gradlew :shared:clean :shared:assembleSharedReleaseXCFramework

echo ""
echo "✅ Release XCFramework generated at:"
echo "shared/build/XCFrameworks/release/Shared.xcframework"