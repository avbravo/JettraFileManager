# JettraFileManager: Sistema de Archivos de Próxima Generación

JettraFileManager es un ecosistema de gestión de archivos diseñado para la máxima eficiencia y visualización inmersiva. 

## 🏗️ Arquitectura del Sistema

El ecosistema se divide en componentes modulares que interactúan de forma desacoplada:

### 1. JettraFileManager (Emisor/Sender)
Módulo encargado de la lectura, fragmentación y envío de archivos. 
- **Funcionalidad**: Utiliza un pool de hilos virtuales (`Virtual Threads`) para enviar múltiples trozos simultáneamente.
- **Tecnología**: Se comunica con el receptor mediante `JettraGRPCClient`.

### 2. JettraReceptor (Servidor)
Servicio que reside en la unidad de destino (USB, Servidor Remoto o Nube).
- **Funcionalidad**: Implementa `JettraTransferService` para recibir trozos de archivos y reconstruirlos de forma asíncrona mediante `RandomAccessFile`.
- **Infraestructura**: Corre sobre un servidor **JettraServer** nativo.

### 3. Librerías Core (Integración)
- **JettraGRPC**: Puente de comunicación binaria que elimina la dependencia de Netty.
- **JettraChunks**: Definición de los modelos de datos y lógica de fragmentación/compresión GZIP.

## 🌟 Características Principales

- **Concurrencia Masiva**: Aprovecha los hilos virtuales de Java 25 para procesar miles de trozos de archivos concurrentemente.
- **Optimización de Memoria**: Utiliza flags experimentales como `-XX:+UseCompactObjectHeaders` para reducir la huella de memoria.
- **Arquitectura Zero-Netty**: Comunicación ligera basada en el stack nativo de Java.
- **Fragmentación Inteligente**: Trozos de 1MB comprimidos que garantizan integridad y velocidad.

## 🛠️ Compilación y Ejecución

Para poner en marcha el ecosistema Jettra, siga estos pasos:

### 1. Compilar Librerías Base (MANDATORIO)
Antes de compilar el proyecto principal, se deben instalar las librerías standalone:
```bash
# Instalar JettraGRPC
cd ../JettraGRPC && mvn clean install

# Instalar JettraChunks
cd ../JettraChunks && mvn clean install
```

### 2. Ejecutar el Sistema con Java 25
El proyecto está optimizado para las últimas características de la JVM. Utilice el script automatizado:
```bash
./buildandrun.sh
```
Este script realiza un `mvn clean install` y arranca la aplicación con los flags necesarios:
- `--enable-preview`: Habilita características de Java 25.
- `-XX:+UseCompactObjectHeaders`: Reduce el tamaño de los encabezados de objetos (JEP 450), optimizando el uso de memoria en transferencias masivas.

## 🚀 Funcionamiento del Servidor (JettraServer)

El servidor funciona como un contenedor ligero e integrado:
- **Native Stack**: No depende de servidores externos (Tomcat/Netty). Utiliza el `HttpServer` nativo de Java mejorado con Virtual Threads.
- **Virtual Threads**: Cada petición de transferencia se maneja en un hilo virtual, permitiendo miles de conexiones simultáneas sin agotar los recursos del sistema.
- **Handlers Dinámicos**: Permite registrar servicios gRPC y REST de forma programática.

## 🎮 Interfaz 3D Inmersiva (JettraWUI)

### ¿Dónde se ve la interfaz?
La interfaz 3D se activa automáticamente al iniciar el servidor y es accesible a través del navegador web:
### 3. Interfaz Web 3D Futurista (JettraWUI)

La interfaz de administración se ha restaurado y optimizado utilizando el framework **JettraWUI**. Esta interfaz proporciona una visualización "holográfica" y futurista de los archivos y unidades.

*   ### Detección Dinámica de Unidades
El sistema ahora escanea automáticamente los puntos de montaje en `/media/avbravo/` y los lista en la nueva categoría **DISPOSITIVOS** del menú lateral.
*   **Unidades USB**: Se detectan automáticamente (ej. `AVBRAVO-KIN`). Al hacer clic, la interfaz se reorienta a esa unidad.
*   **Unidades de Red**: Se integran unidades simuladas y reales (NAS) para una gestión centralizada.
*   **Interactividad**: Cada dispositivo cuenta con su propio icono (💾 para USB, 🖧 para Red) y utiliza el sistema de navegación por parámetros de Jettra.
*   **Características**:
    *   **Dashboard Futurista**: Diseño basado en glassmorphism con efectos de brillo (glow) y animaciones de entrada.
    *   **Explorador 3D**: Permite seleccionar carpetas locales para visualizarlas en una estructura de árbol "holográfica".
    *   **Gestión de Unidades**: Menú lateral para navegar entre la vista 3D, unidades remotas y operaciones de fragmentación (Chunks).
    *   **Sincronización en Tiempo Real**: La interfaz utiliza `JettraSyncManager` para detectar cambios en el servidor y notificar al usuario.

### 4. Conexión de Unidad Remota (Plug & Play)

El sistema está diseñado para detectar y montar unidades de forma dinámica:

1.  **Detección**: Al iniciar `JettraMain`, el sistema escanea los puntos de montaje (por defecto `/media/avbravo/USB_DRIVE`).
2.  **Instalación del Receptor**: Se despliega automáticamente un `JettraFileSystemReceptor` en la unidad detectada.
3.  **Registro de Servicio**: La unidad se registra como un servicio gRPC en el `JettraServer`.
4.  **Visualización**: En la interfaz 3D, bajo la sección "Unidades Remotas", podrá ver y administrar los archivos de la unidad conectada como si fueran locales.

---
© 2026 JettraStack - Advanced File Management System

## ⚙️ Configuración

JettraFileManager permite configurar el puerto de escucha del receptor mediante dos métodos:

### 1. Archivo de Propiedades
Cree un archivo llamado `jettrafilemanagercongif.properties` en la raíz del proyecto con el siguiente contenido:
```properties
jettra.server.port=8080
```

### 2. Parámetros de Ejecución
Puede sobrescribir el puerto configurado pasando el parámetro `--port` al ejecutar:
```bash
java -cp "..." io.jettra.fs.sender.JettraMain --port 9090
```

---
© 2026 JettraStack Ecosystem - Advanced Agentic Computing
