#!/bin/bash
echo "Iniciando JettraFileManager SHELL CONSOLE..."

# Compilar para asegurar que todo esté actualizado
mvn clean install -DskipTests

if [ $? -eq 0 ]; then
    # Ejecutar con el flag --shell
    java --enable-preview \
         -XX:+UnlockExperimentalVMOptions \
         -XX:+UseCompactObjectHeaders \
         -cp "jettra-sender/target/jettra-sender-1.0-SNAPSHOT.jar:jettra-sender/target/lib/*" \
         io.jettra.fs.sender.JettraMain --shell
else
    echo "Error en la compilación."
    exit 1
fi
