#!/bin/bash
# builddebsnap.sh - Creates .deb and .snap packages for JettraFileManager

echo "--- Building DEB and SNAP packages ---"

# 1. Build the project
cd ..
mvn clean install -DskipTests

# 2. Create .deb using jpackage (Requires JDK 25)
echo "Creating DEB package..."
jpackage \
  --input JettraFileManager/jettra-sender/target/lib \
  --main-jar ../jettra-sender-1.0-SNAPSHOT.jar \
  --main-class io.jettra.fs.fx.JettraFileManagerFX \
  --type deb \
  --name jettra-file-manager \
  --app-version 1.0.0 \
  --vendor "Jettra" \
  --description "High-performance futuristic file manager" \
  --linux-shortcut \
  --dest JettraFileManager/dist/deb

# 3. Create SNAP (Requires snapcraft)
echo "Creating SNAP package..."
mkdir -p JettraFileManager/dist/snap
cat <<EOF > JettraFileManager/dist/snap/snapcraft.yaml
name: jettra-file-manager
base: core22
version: '1.0.0'
summary: Futuristic File Manager
description: |
  JettraFileManager is a high-performance file manager with 3D UI and advanced compression.

grade: devel
confinement: devmode

parts:
  jettra-app:
    plugin: dump
    source: JettraFileManager/jettra-sender/target/
    stage-packages:
      - libgtk-3-0
      - libglu1-mesa

apps:
  jettra-file-manager:
    command: java -jar \$SNAP/jettra-sender-1.0-SNAPSHOT.jar
    extensions: [gnome]
EOF

cd JettraFileManager/dist/snap && snapcraft

echo "--- Packages created in JettraFileManager/dist/ ---"
