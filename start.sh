#!/bin/bash

# Securely load environment keys
if [ -f .env ]; then
    echo "🔐 Loading .env configuration..."
    source .env
fi

echo "🚀 Starting Project SHRAPNEL Build & Run Sequence..."

# Cleanup function to kill background processes when Ctrl+C is pressed
cleanup() {
    echo -e "\n🛑 Shutting down SHRAPNEL servers..."
    kill $(jobs -p) 2>/dev/null
    wait $(jobs -p) 2>/dev/null
    echo "✅ All processes stopped."
    exit
}

# Trap Ctrl+C (SIGINT) to trigger the cleanup function
trap cleanup SIGINT

# 1. Compile the Backend
echo "🔨 Compiling Spring Boot Backend..."
mvn clean compile

# Check if compilation succeeded before starting servers
if [ $? -ne 0 ]; then
    echo "❌ Backend compilation failed! Aborting startup."
    exit 1
fi

# 1.5 Start MLflow Tracking Server
echo "📊 Starting MLflow Tracking Server..."
# Run in background and pipe output to dev/null to keep console clean, or leave it. Keep it simple.
mlflow server --backend-store-uri sqlite:///mlflow.db --host 127.0.0.1 --port 5000 > /dev/null 2>&1 &
sleep 2

# 2. Start the Backend in the background
echo "⚙️  Starting Spring Boot Server..."
# We can skip 'clean' here since we just did it above
mvn spring-boot:run -Dmaven.test.skip=true &

# Give the backend a 3-second head start to initialize
sleep 3

# 3. Start the Frontend in the background
echo "🎨 Starting Next.js Frontend..."
cd shrapnel-ui || { echo "❌ shrapnel-ui directory not found!"; exit 1; }
npm run dev &

echo ""
echo "====================================================="
echo "✅ SHRAPNEL is launching!"
echo "🌐 Dashboard: http://localhost:3000"
echo "🔌 API:       http://localhost:8080"
echo "📈 MLflow:    http://localhost:5000"
echo "🛑 Press [CTRL+C] to stop both servers safely."
echo "====================================================="
echo ""

# Keep the script running and wait for the background processes
wait
