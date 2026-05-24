# JettraFileManager

## Descripción General
`JettraFileManager` es el servicio y conjunto de utilidades para gestionar el sistema de archivos (File System) dentro del ecosistema JettraStack. Se encarga de abstraer y administrar la persistencia en disco de ficheros subidos por usuarios o generados por el sistema.

## Detalles Específicos
- **Arquitectura general**: Utilidades puras y scripts asociados al ciclo de vida del almacenamiento. También contiene scripts (como `builddocker.sh`) para el despliegue del almacenamiento si aplica.
- **Dependencias clave**: Subsistemas de I/O de Java. Integrable con `JettraCompactFile` y `JettraChunks`.
- **Roles dentro del sistema**: Proveer un mecanismo seguro, estandarizado y escalable de guardar, recuperar, listar o eliminar recursos estáticos y binarios que las aplicaciones generan o necesitan.

## Características Detalladas
- **Almacenamiento Local y Remoto**: Interfaces de abstracción que permiten manejar las rutas de directorios independientemente del entorno.
- **Scripts de Despliegue**: Soporte con scripts de Docker (ej. `builddocker.sh`) para proveer entornos aislados de almacenamiento de archivos.
- **Integridad y Limpieza**: Funciones de chequeo de existencia o compresión.

## Guía de Entrenamiento (AI / Nuevas Características)
- Cuando se añada una integración con nuevos servicios en la nube (ej. AWS S3, Azure Blob Storage), debe ser adaptada en este módulo, manteniendo la misma interfaz transparente.
- Prestar atención a las reglas de concurrencia y permisos de disco local, implementando chequeos seguros antes de operaciones I/O.
