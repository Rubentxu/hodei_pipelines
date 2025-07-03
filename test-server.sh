#!/bin/bash

echo "🚀 Testing Hodei Pipelines Server"
echo "=================================="

# Start server in background
echo "Starting server..."
gradle :backend:application:run &
SERVER_PID=$!

# Wait for server to start
echo "Waiting for server to start..."
sleep 10

# Test gRPC server (if grpcurl is available)
echo -e "\n📡 Testing gRPC server:"
if command -v grpcurl &> /dev/null; then
    grpcurl -plaintext localhost:9090 list || echo "gRPC not responding or grpcurl not available"
else
    echo "grpcurl not available, skipping gRPC test"
fi

# Test HTTP server
echo -e "\n🌐 Testing HTTP server:"
echo "Health check:"
curl -s http://localhost:8080/health | jq . || echo "Health endpoint not responding"

echo -e "\nAPI info:"
curl -s http://localhost:8080/api/v1/info | jq . || echo "API info endpoint not responding"

# Test WebSocket (if websocat is available)
echo -e "\n🔌 Testing WebSocket:"
if command -v websocat &> /dev/null; then
    echo "Connecting to WebSocket demo..."
    timeout 10 websocat ws://localhost:8080/ws/demo || echo "WebSocket demo not responding or websocat not available"
else
    echo "websocat not available, skipping WebSocket test"
fi

# Stop server
echo -e "\n🛑 Stopping server..."
kill $SERVER_PID
wait $SERVER_PID 2>/dev/null

echo -e "\n✅ Test completed!"