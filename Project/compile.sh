#!/bin/bash
# Compilation script for Rock-Paper-Scissors project
# Run this in Git Bash from the Project directory

echo "=== Compiling IT114 Rock-Paper-Scissors Project ==="
echo ""

# Compile all Java files
echo "Compiling Java files..."
javac Server/*.java Common/*.java Exceptions/*.java Client/*.java

if [ $? -eq 0 ]; then
    echo "✓ Compilation successful!"
    echo ""
    echo "To run the server:"
    echo "  java Project.Server.Server"
    echo ""
    echo "To run a client (in a separate terminal):"
    echo "  java Project.Client.Client"
    echo ""
else
    echo "✗ Compilation failed. Check errors above."
    exit 1
fi
