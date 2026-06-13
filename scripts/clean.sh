#!/bin/bash
# /**
#  * Clean the project by removing build artifacts and temporary files.
#  *
#  * Usage:
#  *   ./scripts/clean.sh
#  */
set -e

echo "🧹 Cleaning project..."

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
cd "$ROOT_DIR"

./gradlew clean
rm -rf shared/build
rm -rf build

echo "✅ Clean complete"