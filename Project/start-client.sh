#!/bin/bash
# Quick start script for Client
# Run this in Git Bash from the Project directory

echo "=== Starting Rock-Paper-Scissors Game Client ==="
echo ""
echo "Available commands:"
echo "  /name <username>       - Set your name"
echo "  /connect localhost:3000 - Connect to server"
echo "  /ready                 - Toggle ready status"
echo "  /pick <r|p|s>          - Make your pick"
echo "  /listusers             - View players and scores"
echo ""
java Project.Client.Client
