#!/bin/bash
# /**
#  * This script builds the Android AAR for the shared module.
#  * It should be run from the root of the project.
#  *
#  * Usage:
#  *   ./scripts/build-android.sh
#  *
#  * After running, the AAR will be located at:
#  *   shared/build/outputs/aar/shared-release.aar
#  */
set -e

echo "🚀 Building Android AAR..."

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
cd "$ROOT_DIR"

./gradlew :shared:clean :shared:assembleRelease

echo ""
echo "✅ Android AAR generated at:"
echo "shared/build/outputs/aar/shared-release.aar"