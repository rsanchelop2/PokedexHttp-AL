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
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
    public static String normalizarEntrada(String entrada) throws ExcepcionPokedex {
        if (entrada == null || entrada.isBlank()) throw new ExcepcionPokedex("Introduce un nombre o identificador de Pokémon.");
        String clave = entrada.trim().toLowerCase(Locale.ROOT);
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
        String clave = normalizarEntrada(entrada);
        Pokemon almacenado = cache.get(clave);
        if (almacenado != null) {
            System.out.println("Caché: " + almacenado.getNombre());
            return almacenado;
        }

        URI uriFinal = base.resolve(clave + "/");
        System.out.println("URL solicitada: " + uriFinal);
        HttpRequest peticion = HttpRequest
                    .newBuilder()
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .uri(uriFinal) //Indica la dirección de destino, convirtiendo el texto en un objeto URI.
                    .header("Accept", "application/json")
                    .build();
        try {
            HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            mostrarMetadatos(respuesta);
            if (respuesta.statusCode() == 404) {
                throw new ExcepcionPokedex("HTTP 404: no se ha encontrado ese Pokémon.");
            }
            if (respuesta.statusCode() != 200) {
                throw new ExcepcionPokedex("Error HTTP " + respuesta.statusCode() + ": no se procesa la ficha.");
            }
            Pokemon pokemon = Pokemon.desdeJson(respuesta.body());
            cache.put(pokemon.getNombre(), pokemon);
            cache.put(Integer.toString(pokemon.getId()), pokemon);
            return pokemon;
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
        System.out.println("Código de estado: " + respuesta.statusCode());
        System.out.println("Versión HTTP: " + respuesta.version());
        respuesta.headers().firstValue("Content-Type")
                .ifPresent(tipo -> System.out.println("Content-Type: " + tipo));
    }
    // Ampliación 2: imágenes.
    public String obtenerUrlImagen(Pokemon pokemon) throws ExcepcionPokedex {
        String url = pokemon.getUrlImagen();
        if (url == null || url.isBlank()) throw new ExcepcionPokedex("No hay imagen disponible para " + pokemon.getNombre() + ".");
        return url;
    }
    public Path descargarImagen(Pokemon pokemon, Path destino) throws ExcepcionPokedex, InterruptedException {
        String urlImagen = obtenerUrlImagen(pokemon);
        URI url;
        try {
            url = URI.create(urlImagen);
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
            System.out.println("URL solicitada: " + url);
            HttpRequest peticion = HttpRequest.newBuilder()
                    .uri(url)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            // ofFile escribe el cuerpo en disco, incluso si el servidor responde con un error.
            HttpResponse<Path> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofFile(temporal));
            mostrarMetadatos(respuesta);
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
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw error;
        } finally {
            if (temporal != null) {
                try { Files.deleteIfExists(temporal); }
                catch (IOException error) { System.out.println("No se pudo borrar el temporal " + temporal + ": " + error.getMessage()); }
            }
        }
    }
}
