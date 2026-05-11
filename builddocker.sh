#!/bin/bash
# builddocker.sh - Generates Docker image for JettraFileManager

echo "--- Building JettraFileManager Docker Image ---"

# 1. Build the project
cd ..
mvn clean install -DskipTests

# 2. Create Dockerfile
cat <<EOF > JettraFileManager/Dockerfile
FROM bellsoft/liberica-openjdk-debian:21-full

# Install dependencies for X11/GUI
RUN apt-get update && apt-get install -y \
    libgtk-3-0 \
    libglu1-mesa \
    libx11-6 \
    libxext6 \
    libxrender1 \
    libxtst6 \
    libfreetype6 \
    libfontconfig1 \
    x11-apps \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built jar and libs
COPY jettra-sender/target/jettra-sender-1.0-SNAPSHOT.jar app.jar
COPY jettra-sender/target/lib lib/

# Set environment for X11
ENV DISPLAY=:0

# Run the application
CMD ["java", "-jar", "app.jar"]
EOF

# 3. Build the image
docker build -t jettra-file-manager:latest JettraFileManager/

echo "--- Docker Image built: jettra-file-manager:latest ---"
echo "To run (requires X11 forwarding): docker run -it --rm -e DISPLAY=\$DISPLAY -v /tmp/.X11-unix:/tmp/.X11-unix jettra-file-manager:latest"
