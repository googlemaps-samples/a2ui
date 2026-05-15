#!/bin/bash

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
sed -i '' "s|^ *#* *maui-a2ui-python = { path = [^,}]*|maui-a2ui-python = { path = \"$MAUI_PATH\"|" "$FILE"

echo "Updated $FILE with path: $MAUI_PATH"
