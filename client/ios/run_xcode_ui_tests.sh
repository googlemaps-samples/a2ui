#!/bin/bash
set -e

echo "Starting A2UI backend..."
# Assuming we are in this directory when running this script,
# we need to go up three levels to find the root 'a2ui'.
(cd ../../../a2ui && ./run_aikit_demo.sh --local) &
BACKEND_PID=$!

# Ensure backend is killed when the script exits.
trap "kill $BACKEND_PID" EXIT

echo "Waiting for backend to start (15s)..."
sleep 15

# 2. Run the iOS UI tests
echo "Running iOS UI tests on iPhone 17 Pro..."

# We use xcodebuild to run the tests.
# https://developer.apple.com/library/archive/technotes/tn2339/_index.html
xcodebuild \
  -project A2UI-Example.xcodeproj \
  -scheme A2UI-Example \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  test

echo "UI Tests completed successfully!"
