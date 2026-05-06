# Jettra File Manager - Interfaz JavaFX

Esta guía detalla la implementación y el uso de la interfaz nativa de escritorio basada en **JavaFX** para el ecosistema JettraFileManager.

## Descripción General

La interfaz JavaFX de JettraFileManager ha sido diseñada para ofrecer una experiencia de usuario (UX) fluida y de alto rendimiento, complementando la interfaz web con una estética futurista y capacidades nativas. Sigue un patrón de diseño de **doble panel** (estilo Commander) para optimizar la gestión de archivos entre diferentes unidades o directorios.

## Características Principales

- **Explorador Dual**: Dos paneles independientes (`TreeView`) que permiten visualizar origen y destino simultáneamente.
- **Estética Futurista**: Uso de CSS personalizado con efectos de brillo (glow), gradientes cian y tipografía moderna.
- **Soporte de Iconos Dinámicos**: Identificación visual de tipos de archivos (Java, PDF, Imágenes, Videos, etc.).
- **Gestión de Dispositivos**: Detección automática de unidades locales y soportes extraíbles (pendrives, discos externos).
- **Operaciones de Archivo**: Menú contextual completo para Copiar, Pegar, Renombrar y Eliminar.
- **Archivos Ocultos**: Opción para alternar la visibilidad de archivos del sistema.

## Requisitos de Ejecución

- **Java 25** o superior (con soporte para preview features).
- **Maven 3.9+**.
- **JavaFX SDK** (incluido automáticamente vía dependencias de Maven).

## Instrucciones de Compilación y Ejecución

Debido a que el proyecto es multi-módulo, es necesario asegurar que todas las dependencias y clases estén correctamente ubicadas.

### 1. Compilación y Empaquetado

Desde la raíz del proyecto (`JettraFileManager`), ejecuta el siguiente comando para compilar y descargar las librerías necesarias:

```bash
mvn clean package -DskipTests
```

### 2. Ejecución de la Interfaz JavaFX

Para iniciar JettraFileManager con la interfaz gráfica activada, utiliza el siguiente comando desde la raíz del proyecto:

```bash
java -cp "jettra-sender/target/classes:jettra-receptor/target/classes:jettra-sender/target/lib/*" \
     io.jettra.fs.sender.JettraMain --fx
```

> [!TIP]
> Si deseas iniciar también la consola interactiva (Shell), añade la bandera `--shell` al final del comando.

## Estilo y Personalización

El archivo de estilos se encuentra en:
`jettra-sender/src/main/resources/io/jettra/fs/fx/style.css`

Puedes modificar los colores principales ajustando las variables en el bloque `.root`:
- `-fx-accent`: Color principal de resplandor.
- `-fx-base`: Color de fondo base.
- `-fx-control-inner-background`: Color de fondo de los árboles y listas.
