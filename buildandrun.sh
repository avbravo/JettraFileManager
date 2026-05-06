#!/bin/bash
echo "Deteniendo servidor Jettra (Puerto 8080)..."
fuser -k 8080/tcp 2>/dev/null || true
sleep 1

echo "===================================================="
echo " JettraFileManager - Build and Run System (Java 25)"
echo "===================================================="

# Compilar todo el proyecto multi-módulo
mvn clean install -DskipTests

if [ $? -eq 0 ]; then
    echo "¡Compilación exitosa!"
    echo "Iniciando JettraFileSystem Sender..."
    
    # Ejecutar el emisor con flags de Java 25
    java --enable-preview \
         -XX:+UnlockExperimentalVMOptions \
         -XX:+UseCompactObjectHeaders \
         -cp "jettra-sender/target/jettra-sender-1.0-SNAPSHOT.jar:jettra-sender/target/lib/*" \
         io.jettra.fs.sender.JettraMain
else
    echo "Error en la compilación."
    exit 1
fi
