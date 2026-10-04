#!/usr/bin/env bash
set -e

echo "==================================="
echo "Building RAGLab (Backend + RCache)"
echo "==================================="
mvn clean package

echo "==================================="
echo "Building RAGLab (Frontend)"
echo "==================================="
cd frontend
npm run test
npm run build
cd ..

echo "[SUCCESS] Full build completed successfully!"
