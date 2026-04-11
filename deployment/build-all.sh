#!/bin/bash

echo "Building all Lambda functions..."

# Build ms-triagem
echo "Building ms-triagem..."
cd ../ms-triagem
mvn clean package -DskipTests -Plambda
if [ $? -ne 0 ]; then
    echo "Failed to build ms-triagem"
    exit 1
fi

# Build ms-manchester
echo "Building ms-manchester..."
cd ../ms-manchester
mvn clean package -DskipTests -Plambda
if [ $? -ne 0 ]; then
    echo "Failed to build ms-manchester"
    exit 1
fi

# Build ms-nacional-consumer
echo "Building ms-nacional-consumer..."
cd ../ms-nacional-consumer
mvn clean package -DskipTests -Plambda
if [ $? -ne 0 ]; then
    echo "Failed to build ms-nacional-consumer"
    exit 1
fi

# Build ms-consolidador
echo "Building ms-consolidador..."
cd ../ms-consolidador
mvn clean package -DskipTests -Plambda
if [ $? -ne 0 ]; then
    echo "Failed to build ms-consolidador"
    exit 1
fi

# Build ms-painel-ws
echo "Building ms-painel-ws..."
cd ../ms-painel-ws
mvn clean package -DskipTests -Plambda
if [ $? -ne 0 ]; then
    echo "Failed to build ms-painel-ws"
    exit 1
fi

echo "All Lambda functions built successfully!"
