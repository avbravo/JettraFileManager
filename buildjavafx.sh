#!/bin/bash

# Script de compilación y ejecución para JettraFileManager con Interfaz JavaFX
# Optimizado para Java 25

echo "--- Iniciando Build de JettraFileManager ---"

# 1. Compilar y empaquetar todo el proyecto
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo "--- Compilación exitosa ---"
    echo "Iniciando Interfaz JavaFX Ultra-Futurista..."
    
    # 2. Ejecutar con flags de Java 25 y soporte nativo
    java --enable-preview \
         --enable-native-access=ALL-UNNAMED \
         -XX:+UnlockExperimentalVMOptions \
         -XX:+UseCompactObjectHeaders \
         -cp "jettra-sender/target/classes:jettra-receptor/target/classes:jettra-sender/target/lib/*" \
         io.jettra.fs.sender.JettraMain --fx
else
    echo "ERROR: Falló la compilación del proyecto."
    exit 1
fi
