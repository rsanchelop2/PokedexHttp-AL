# PokedexHttp — solución del profesor

Pokédex de consola para Programación de Servicios y Procesos, 2.º de DAM. Incluye la actividad principal y las ampliaciones de imágenes y comparación completas.

## Requisitos y ejecución

- JDK 21 o 22, Maven 3.9 y conexión a Internet para consultar PokéAPI. No requiere claves.
- Código y archivos de salida en UTF-8. El `pom.xml` fija Java 21, Gson 2.13.2 y JUnit 5.11.4.
- En IntelliJ IDEA abre esta carpeta o su `pom.xml` como proyecto Maven y espera a que se resuelvan las dependencias. Selecciona JDK 21 o 22 como SDK del proyecto y como JRE de Maven; no actives características preview.
- Abre `src/main/java/es/marianasanz/psep/pokedex/Main.java` y ejecuta su método `main` con el icono verde. Usa la raíz del proyecto como directorio de trabajo. Si la consola presenta caracteres incorrectos, configura su codificación como UTF-8.

Desde una terminal en la raíz, con `JAVA_HOME` apuntando al JDK 21 o 22 y Maven en `PATH`:

```text
mvn clean compile
mvn test
mvn package
mvn exec:java
```

El complemento compiler configura la compilación, surefire ejecuta JUnit 5 y exec ejecuta `Main`. También puede usarse el Maven integrado de IntelliJ desde su ventana Maven. El JAR ordinario no incluye dependencias: utiliza `exec:java` para ejecutar.

## Menú y ejemplos de comprobación

1. **Consultar Pokémon**: solicita nombre o id, muestra la ficha y guarda el JSON original.
2. **Descargar imagen**: obtiene y guarda la ficha, usa `sprites.front_default` y guarda la imagen. Si ya existe, permite conservarla (`c`) o descargar otra vez (`d`).
3. **Comparar dos Pokémon**: guarda ambas fichas, muestra las seis estadísticas, concede un punto por victoria y guarda el mismo informe mostrado. Los empates no puntúan. Es una comparación de estadísticas, no una simulación de combate.
0. **Salir**. El final de la entrada también termina ordenadamente. Las opciones incorrectas y errores recuperables devuelven al menú; una interrupción termina la aplicación.

Prueba `pikachu` y después `25`: solo la primera consulta accede a la red. Compara `pikachu` con `bulbasaur`. Compara `pikachu` con `25` para comprobar el rechazo de un mismo id. Consulta `pokemon-inexistente-prueba` para comprobar HTTP 404 y pulsa Intro sin texto para comprobar una entrada vacía. Las barras, parámetros, fragmentos, números no positivos y letras no ASCII se rechazan antes de acceder a la red.

## Clases y correspondencia con la actividad

| Clase | Responsabilidad y métodos |
| --- | --- |
| `Main` | Menú, único Scanner, coordinación, confirmación de sustitución de imagen y mensajes de guardado |
| `ClientePokeApi` | `crearClienteHttp()`, `consultarPokemon()`, normalización y caché; ampliación 1: `obtenerUrlImagen()` y `descargarImagen()` |
| `Pokemon` | `desdeJson()`, datos, conversión de unidades y conservación del cuerpo original |
| `ServicioPokedex` | `mostrarFicha()`; ampliación 2: `compararPokemon()` |
| `GestorArchivos` | `guardarFicha()`, rutas de imágenes y `guardarComparacion()` |
| `ResultadoComparacion` | Datos y texto del resultado, reutilizados sin repetir puntuaciones |
| `ExcepcionPokedex` | Mensajes recuperables de validación, HTTP, JSON y descarga |

Los bloques del código están identificados con comentarios de actividad principal, ampliación 1 y ampliación 2. La plantilla inicial del proyecto se conserva en `referencia/PokedexHttp.java.txt` fuera de las fuentes compiladas.

Las salidas son relativas al **directorio de trabajo**, no al lugar donde está la clase:

```text
resources/pokemon/{nombre-canonico}.json
resources/imagenes/{nombre-canonico}.png
resources/comparaciones/{nombre1}_vs_{nombre2}.txt
```

Las carpetas se crean al guardar. Los JSON e informes se sustituyen; un error de escritura se informa separado del éxito de la consulta. Las salidas generadas, `target` y configuración local del IDE están excluidas en `.gitignore`.

## HTTP, JSON, archivos y caché

Se reutiliza un cliente HTTP/1.1 con conexión limitada a 10 segundos y peticiones síncronas `send()` limitadas a 15 segundos. `Redirect.NORMAL` sigue redirecciones excepto de HTTPS a HTTP. Los GET de datos envían `Accept: application/json`, sin `Content-Type` porque no tienen cuerpo. Se muestra la URL solicitada y, cuando se recibe respuesta, estado, versión y Content-Type si existe.

Un **error HTTP** (404, 500, etc.) es una respuesta recibida del servidor; se comprueba antes de interpretar el JSON. Un **error de conexión** es una excepción de transporte (por ejemplo DNS o conexión rechazada) y puede no existir respuesta ni código HTTP. Los tiempos de espera tienen mensaje propio. Un HTTP 200 con JSON mal formado o datos imprescindibles ausentes produce un error de procesamiento JSON. No se inventan estadísticas cero ni se presupone el orden del array: se usa `stat.name`. Las interrupciones restauran la marca del hilo y terminan el menú.

`BodyHandlers.ofString(UTF_8)` recibe el cuerpo como texto para leerlo con Gson y conservarlo íntegro. `BodyHandlers.ofFile()` escribe el cuerpo directamente a un archivo: también escribiría una página de error, por eso la imagen se recibe en un temporal. Solo se mueve al destino si hay HTTP 200, Content-Type `image/*` y cuerpo no vacío. Si falla, se elimina el temporal y se conserva la imagen anterior. No se construye la URL de imagen manualmente.

La caché vive únicamente durante la ejecución. Tras validar una respuesta se registra el mismo objeto por nombre canónico e id (y la clave consultada). Consulta, descarga y comparación comparten ese cliente; `pikachu` y `25` reutilizan los mismos datos. Los fallos no entran en caché. Una reutilización muestra un mensaje de memoria sin inventar metadatos HTTP. Guardar usa el JSON ya recibido, sin repetir peticiones, incluidas las fichas obtenidas desde imágenes y comparaciones. No se cargan archivos al iniciar.

## Pruebas

`mvn test` ejecuta pruebas sin Internet. Un servidor HTTP en la interfaz local y carpetas temporales verifican JSON y unidades, orden de estadísticas, campos ausentes, ganador y empate, rechazo del mismo id, informe y JSON guardados, caché por nombre e id, 404 no interpretable y no almacenado, entradas inválidas, imagen ausente y descargas HTTP fallidas, con contenido no imagen o cuerpo truncado sin sobrescribir una imagen previa ni dejar temporales. La URL base configurable solo se utiliza en los tests; la aplicación usa PokéAPI.

Para una comprobación real moderada, ejecuta el menú: consulta `pikachu`, descarga la imagen de `25` y sal. Se espera una petición de ficha y una de imagen; el segundo acceso a la ficha debe proceder de la caché. Esta comprobación requiere conectividad y no forma parte de las pruebas automatizadas.

Verificación realizada el 8 de octubre de 2026: compilación con JDK 22.0.2 y destino Java 21, 10 pruebas locales superadas sin fallos ni omisiones y generación del JAR con Maven. La consulta real de `pikachu` y su imagen respondieron HTTP 200; `25` reutilizó la ficha de la caché. También se comprobó `mvn exec:java` con opción incorrecta, entrada vacía y salida normal. No se ha ejecutado directamente sobre un JDK 21. En este equipo el Java predeterminado del terminal es 17: selecciona el JDK 21 o 22 antes de ejecutar Maven. Se utilizó el Maven integrado de IntelliJ y un repositorio de dependencias local `.maven-repository` (ignorado por Git), porque Maven no está en el PATH.

La versión concreta de Gson puede consultarse en [Maven Central](https://central.sonatype.com/artifact/com.google.code.gson/gson/2.13.2).
