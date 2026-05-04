#!/bin/bash
# Deploy sharedbackpack mod to tffh1server
# Usage: ./deploy.sh

SERVER_DIR="/opt/dayi/plant_bird/tffh1.2.2server_fix"
MOD_JAR="/opt/dayi/plant_bird/cc_mod/build/libs/sharedbackpack-1.2.0.jar"
MODS_DIR="$SERVER_DIR/mods"

echo "Deploying Shared Backpack mod to tffh1server..."

# Check if server directory exists
if [ ! -d "$SERVER_DIR" ]; then
    echo "Error: Server directory not found: $SERVER_DIR"
    exit 1
fi

# Create mods directory if it doesn't exist
mkdir -p "$MODS_DIR"

# Copy the mod jar
cp "$MOD_JAR" "$MODS_DIR/"
echo "Copied $MOD_JAR to $MODS_DIR/"

# Check if server is running via tmux
if tmux has-session -t mc 2>/dev/null; then
    echo "Server is running in tmux. Use '/stop' in-game to stop it before testing."
    echo "Or manually run: tmux send-keys -t mc '/stop' Enter"
else
    echo "Server is not running. You can start it with: cd $SERVER_DIR && ./run.sh"
fi

echo "Deployment complete!"
echo "Mod features:"
echo "  - /c : Open shared backpack"
echo "  - /c <search> : Open with pinyin search"
echo "  - /cc help : Show help"
echo "  - /cc admin ... : Admin commands"
echo "  - SQLite storage with auto-backup"
echo "  - Team-based sharing with union support"
echo "  - Item metadata (time, count, modifier) in tooltip"
echo "  - Diamond upgrade for capacity"
echo "  - Page navigation (prev/next buttons when multi-page)"
echo "  - Left click = take 1, Right click = take full stack"
echo "  - Shift+click to move items to/from backpack"
