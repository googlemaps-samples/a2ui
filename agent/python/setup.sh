#!/bin/bash
#
# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Check if path argument is provided
if [ -z "$1" ]; then
    echo "Usage: $0 <path_to_maui_package>"
    echo "Example: $0 ./a2ui/agent/python-agent"
    exit 1
fi

MAUI_PATH="$1"
FILE="pyproject.toml"

# Check if file exists
if [ ! -f "$FILE" ]; then
    echo "Error: $FILE not found in current directory."
    exit 1
fi

# Replace path and uncomment line if needed
# Using | as delimiter for sed to handle slashes in paths
if [[ "$OSTYPE" == "darwin"* ]]; then
    sed -i '' "s|^ *#* *maui-a2ui-python = { path = [^,}]*|maui-a2ui-python = { path = \"$MAUI_PATH\"|" "$FILE"
else
    sed -i "s|^ *#* *maui-a2ui-python = { path = [^,}]*|maui-a2ui-python = { path = \"$MAUI_PATH\"|" "$FILE"
fi

echo "Updated $FILE with path: $MAUI_PATH"
