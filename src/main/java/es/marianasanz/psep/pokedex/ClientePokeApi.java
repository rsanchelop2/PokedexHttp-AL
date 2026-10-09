package es.marianasanz.psep.pokedex;

import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public class ClientePokeApi {
    private final HttpClient cliente;
    private final URI base;
    private final Map<String, Pokemon> cache = new HashMap<>();

    public ClientePokeApi() {
        this(URI.create("https://pokeapi.co/api/v2/pokemon/"));
    }
    /** La URL configurable permite pruebas locales sin depender de Internet. */
    public ClientePokeApi(URI base) {
        if (!base.toString().endsWith("/")) throw new IllegalArgumentException("La URL base debe terminar en /");
        this.base = base;
        this.cliente = crearClienteHttp();
    }
    // Actividad principal: un solo cliente reutilizado para todas las peticiones.
    public static HttpClient crearClienteHttp() {
        // TODO
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NORMAL).build();
        return null;
    }
    public static String normalizarEntrada(String entrada) throws ExcepcionPokedex {
        //TODO: Comprueba que la entrada no esté vacía o sea nula.
        if (entrada != null || entrada.isEmpty()) throw new ExcepcionPokedex("Entrada vacia o null");
        String clave = entrada.trim(); //TODO: Elimina los espacios al principio y al final de la entrada y convierte el nombre a minúsculas.
        if (clave.matches("[0-9]+")) {
            BigInteger numero = new BigInteger(clave);
            if (numero.signum() <= 0) throw new ExcepcionPokedex("El identificador debe ser un entero positivo.");
            return numero.toString();
        }
        if (!clave.matches("[a-z0-9-]+") || !clave.matches(".*[a-z].*")) {
            throw new ExcepcionPokedex("Usa un nombre con letras ASCII, números y guiones, o un identificador positivo.");
        }
        // Es un segmento de ruta validado; no se usa URLEncoder para parámetros.
        return clave;
    }
    public Pokemon consultarPokemon(String entrada) throws ExcepcionPokedex, InterruptedException {
        // TODO
        try { // Descomentar este TRY-CATCH
            HttpResponse<String> respuesta = null; //TODO

            normalizarEntrada(entrada);
            URI uriFinal = URI.create(base + entrada);

            System.out.println(uriFinal.toString());

            HttpRequest httpRequest = HttpRequest
                    .newBuilder()
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .uri(uriFinal) //Indica la dirección de destino, convirtiendo el texto en un objeto URI.
                    //.headers("Content-Type", "text/html; charset=UTF-8") //Añade pares de nombre y valor
                    .header("Accept", "application/json")
                    .setHeader("User-Agent", "Mozilla/5.0") //Sustituye los valores anteriores. Si no existe, la crea.
                    .build();

            HttpResponse<String> httpResponse = cliente.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            mostrarMetadatos(httpResponse);

            if (respuesta.statusCode() == 404) throw new ExcepcionPokedex("HTTP 404: no existe el Pokémon solicitado.");
            if (respuesta.statusCode() != 200) throw new ExcepcionPokedex("Error HTTP " + respuesta.statusCode() + ": no se procesa la ficha.");
            //TODO
            if (respuesta.statusCode() == 200){
                System.out.println("Pokemon encontrado");
                String jsonPokemon = httpResponse.body();
            }

            return Pokemon.desdeJson(httpResponse.toString());

        } catch (HttpTimeoutException error) {
            throw new ExcepcionPokedex("Tiempo de espera agotado al consultar el Pokémon.", error);
        } catch (IOException error) {
            throw new ExcepcionPokedex("Problema de conexión o lectura HTTP: " + error.getMessage(), error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        }
    }
    private void mostrarMetadatos(HttpResponse<?> respuesta) {
        // TODO
        int statusCode = respuesta.statusCode();

        HttpClient.Version version = respuesta.version();
        String ContentType = respuesta.headers().firstValue("Content-type").orElse("No presente");

        System.out.println("Codigo de estado: " + statusCode);
        System.out.println("Version de HTTP: " + version);
        System.out.println("Content-type: " + ContentType);

    }
    // Ampliación 2: imágenes.
    public String obtenerUrlImagen(Pokemon pokemon) throws ExcepcionPokedex {
        String url = pokemon.getUrlImagen();
        if (url == null || url.isBlank()) throw new ExcepcionPokedex("No hay imagen disponible para " + pokemon.getNombre() + ".");
        return url;
    }
    public Path descargarImagen(Pokemon pokemon, Path destino) throws ExcepcionPokedex, InterruptedException {
        URI url = null;
        try {
            //TODO 1
            if (!("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme())) || url.getHost() == null)
                throw new IllegalArgumentException("La imagen necesita una URL HTTP válida");
        } catch (IllegalArgumentException error) {
            throw new ExcepcionPokedex("URL de imagen no válida.", error);
        }
        Path temporal = null;
        try {
            Path carpeta = destino.toAbsolutePath().getParent();
            Files.createDirectories(carpeta);
            temporal = Files.createTempFile(carpeta, "imagen-", ".tmp");
            HttpRequest peticion = null;//TODO 2
            // ofFile escribe el cuerpo en disco, incluso si el servidor responde con un error.
            HttpResponse<Path> respuesta = null;//TODO 3
            //TODO 4
            if (respuesta.statusCode() != 200) throw new ExcepcionPokedex("Error HTTP " + respuesta.statusCode() + " al descargar la imagen.");
            String tipo = respuesta.headers().firstValue("Content-Type").orElse("")
                    .split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!tipo.matches("image/[a-z0-9.+-]+") || Files.size(temporal) == 0) {
                throw new ExcepcionPokedex("La respuesta no contiene una imagen válida (Content-Type o cuerpo vacío).");
            }
            // Solo una respuesta válida sustituye la imagen previa.
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            temporal = null;
            return destino;
        } catch (HttpTimeoutException error) {
            throw new ExcepcionPokedex("Tiempo de espera agotado al descargar la imagen.", error);
        } catch (IOException error) {
            throw new ExcepcionPokedex("Error de conexión o de archivo al descargar la imagen: " + error.getMessage(), error);
        } /*catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        }*/ finally {
            if (temporal != null) {
                try { Files.deleteIfExists(temporal); }
                catch (IOException error) { System.out.println("No se pudo borrar el temporal " + temporal + ": " + error.getMessage()); }
            }
        }
    }
}
