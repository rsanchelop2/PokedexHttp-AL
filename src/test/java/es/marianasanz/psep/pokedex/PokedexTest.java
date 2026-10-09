package es.marianasanz.psep.pokedex;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class PokedexTest {
    @TempDir Path carpeta;
    private HttpServer servidor;
    private URI base;
    private String ejemplo;
    private final AtomicInteger peticiones = new AtomicInteger();
    private final ByteArrayOutputStream registro = new ByteArrayOutputStream();

    @BeforeEach void preparar() throws IOException {
        try (InputStream recurso = getClass().getResourceAsStream("/pikachu.json")) {
            assertNotNull(recurso);
            ejemplo = new String(recurso.readAllBytes(), StandardCharsets.UTF_8);
        }
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/pokemon/", intercambio -> {
            peticiones.incrementAndGet();
            boolean existe = intercambio.getRequestURI().getPath().equals("/pokemon/pikachu/")
                    || intercambio.getRequestURI().getPath().equals("/pokemon/25/");
            byte[] cuerpo = (existe ? ejemplo : "esto no es JSON").getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", "application/json");
            intercambio.sendResponseHeaders(existe ? 200 : 404, cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) { salida.write(cuerpo); }
        });
        servidor.createContext("/imagen", intercambio -> {
            String modo = intercambio.getRequestURI().getQuery();
            byte[] cuerpo = "respuesta de prueba".getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", "html".equals(modo) ? "text/html" : "image/png");
            intercambio.sendResponseHeaders("error".equals(modo) ? 404 : 200, cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) { salida.write(cuerpo); }
            finally { intercambio.close(); }
        });
        servidor.start();
        base = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/pokemon/");
    }
    @AfterEach void cerrar() { servidor.stop(0); }
    private ClientePokeApi cliente() { return new ClientePokeApi(base); }

    @Test void leeJsonUnidadesTiposYConservaOriginal() throws Exception {
        Pokemon pokemon = Pokemon.desdeJson(ejemplo);
        assertEquals(25, pokemon.getId());
        assertEquals("pikachu", pokemon.getNombre());
        assertEquals(0.4, pokemon.getAlturaMetros());
        assertEquals(6.0, pokemon.getPesoKilogramos());
        assertEquals(java.util.List.of("electric"), pokemon.getTipos());
        Path archivo = new GestorArchivos(carpeta).guardarFicha(pokemon);
        assertEquals(ejemplo, Files.readString(archivo, StandardCharsets.UTF_8));
    }
    @Test void identificaEstadisticasPorNombreYRechazaIncompletas() throws Exception {
        Pokemon pokemon = Pokemon.desdeJson(ejemplo);
        int[] valores = {35, 55, 40, 50, 50, 90};
        for (int i = 0; i < valores.length; i++) assertEquals(valores[i], pokemon.getEstadistica(Pokemon.ESTADISTICAS.get(i)));
        assertThrows(ExcepcionPokedex.class, () -> Pokemon.desdeJson(ejemplo.replace("\"hp\"", "\"otra\"")));
        assertThrows(ExcepcionPokedex.class, () -> Pokemon.desdeJson(ejemplo.replace("\"height\": 4", "\"height\": 4.5")));
    }
    @Test void comparaConGanadorYGuardaMismoInforme() throws Exception {
        Pokemon primero = Pokemon.desdeJson(ejemplo);
        String superior = ejemplo.replace("25", "26").replace("pikachu", "raichu");
        for (int valor : new int[]{35, 55, 40, 50, 90}) superior = superior.replace("\"base_stat\": " + valor, "\"base_stat\": 100");
        ResultadoComparacion resultado = new ServicioPokedex().compararPokemon(primero, Pokemon.desdeJson(superior));
        assertEquals(0, resultado.puntosPrimero()); assertEquals(6, resultado.puntosSegundo());
        assertTrue(resultado.informe().contains("Resultado final: raichu"));
        Path archivo = new GestorArchivos(carpeta).guardarComparacion(resultado);
        assertEquals(resultado.informe(), Files.readString(archivo));
    }
    @Test void comparaConEmpateYRechazaMismoId() throws Exception {
        Pokemon primero = Pokemon.desdeJson(ejemplo);
        Pokemon segundo = Pokemon.desdeJson(ejemplo.replace("25", "26").replace("pikachu", "raichu"));
        ResultadoComparacion resultado = new ServicioPokedex().compararPokemon(primero, segundo);
        assertEquals(0, resultado.puntosPrimero()); assertEquals(0, resultado.puntosSegundo());
        assertTrue(resultado.informe().contains("Resultado final: Empate"));
        assertThrows(ExcepcionPokedex.class, () -> new ServicioPokedex().compararPokemon(primero, primero));
    }
    @Test void cachePorNombreEIdentificadorSinInventarMetadatos() throws Exception {
        ClientePokeApi cliente = cliente();
        Pokemon primero = cliente.consultarPokemon(" PIKACHU ");
        registro.reset();
        assertSame(primero, cliente.consultarPokemon("025"));
        assertEquals(1, peticiones.get());
        assertTrue(registro.toString(StandardCharsets.UTF_8).contains("Caché"));
        assertFalse(registro.toString(StandardCharsets.UTF_8).contains("Código de estado"));
    }
    @Test void respuesta404NoSeInterpretaNiSeCachea() {
        ClientePokeApi cliente = cliente();
        for (int i = 0; i < 2; i++) {
            ExcepcionPokedex error = assertThrows(ExcepcionPokedex.class, () -> cliente.consultarPokemon("inexistente"));
            assertTrue(error.getMessage().contains("HTTP 404"));
            assertFalse(error.getMessage().contains("JSON"));
        }
        assertEquals(2, peticiones.get());
    }
    @Test void entradasInvalidasNoRealizanPeticiones() {
        ClientePokeApi cliente = cliente();
        for (String entrada : new String[]{"", " ", "0", "-2", "a/b", "a?b", "a#b", "pokémon", "1.5"})
            assertThrows(ExcepcionPokedex.class, () -> cliente.consultarPokemon(entrada));
        assertEquals(0, peticiones.get());
    }
    @Test void imagenAusenteSeInforma() throws Exception {
        assertThrows(ExcepcionPokedex.class, () -> cliente().obtenerUrlImagen(Pokemon.desdeJson(ejemplo)));
    }
    @Test void descargaFallidaConservaImagenYEliminaTemporales() throws Exception {
        Path destino = carpeta.resolve("pikachu.png");
        byte[] anterior = {1, 2, 3, 4};
        Files.write(destino, anterior);
        for (String modo : new String[]{"error", "html"}) {
            String url = base.resolve("/imagen?" + modo).toString();
            Pokemon pokemon = Pokemon.desdeJson(ejemplo.replace("\"front_default\": null", "\"front_default\": \"" + url + "\""));
            assertThrows(ExcepcionPokedex.class, () -> cliente().descargarImagen(pokemon, destino));
            assertArrayEquals(anterior, Files.readAllBytes(destino));
            try (var archivos = Files.list(carpeta)) { assertEquals(1, archivos.count()); }
        }
    }

    @Test @Timeout(10) void descargaTruncadaConservaImagen() throws Exception {
        Path destino = carpeta.resolve("pikachu.png");
        byte[] anterior = {1, 2, 3};
        Files.write(destino, anterior);
        // Un socket permite cerrar realmente la conexión antes de completar Content-Length.
        try (ServerSocket servidorTruncado = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            servidorTruncado.setSoTimeout(5000);
            Thread respuesta = Thread.startVirtualThread(() -> {
                try (Socket conexion = servidorTruncado.accept()) {
                    conexion.setSoTimeout(5000);
                    BufferedReader lector = new BufferedReader(new InputStreamReader(conexion.getInputStream(), StandardCharsets.US_ASCII));
                    String linea;
                    while ((linea = lector.readLine()) != null && !linea.isEmpty()) { /* Leer cabeceras. */ }
                    conexion.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\n"
                            + "Content-Length: 100\r\nConnection: close\r\n\r\ncorto").getBytes(StandardCharsets.US_ASCII));
                } catch (IOException error) { throw new UncheckedIOException(error); }
            });
            String url = "http://127.0.0.1:" + servidorTruncado.getLocalPort() + "/imagen";
            Pokemon pokemon = Pokemon.desdeJson(ejemplo.replace("\"front_default\": null", "\"front_default\": \"" + url + "\""));
            assertThrows(ExcepcionPokedex.class, () -> cliente().descargarImagen(pokemon, destino));
            respuesta.join(5000);
            assertFalse(respuesta.isAlive());
        }
        assertArrayEquals(anterior, Files.readAllBytes(destino));
        try (var archivos = Files.list(carpeta)) { assertEquals(1, archivos.count()); }
    }
}
