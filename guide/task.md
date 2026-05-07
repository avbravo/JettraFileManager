Los archivos Chunks deben tener un maximo de 1.5MG
Y no tiene que esperar a procesar todos los archivos, en cuanto el sender termina de convertir a chunk un archivo empieza a transferirlo a la unidad de destino en paralelo.
Cada archivo usa un hilo de java en el sender para convertir a chunk e iniciar la transferencia de los chunk al receptor.
Estos son hilos virtuales no afectan uno al otro se ejecutan en paralelo. y Al terminar el envio de los chunk que se descompuso el archivo , inicia el proceso de convertir al archivo originale en el receptor , y luego elimina los chunks que corresponden a ese archivo.

    