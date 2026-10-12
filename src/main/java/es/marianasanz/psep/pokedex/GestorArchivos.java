package es.marianasanz.psep.pokedex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class GestorArchivos {
    private final Path carpeta;
    public GestorArchivos() { this(Path.of("resources")); }
    public GestorArchivos(Path carpeta) { this.carpeta = carpeta; }

    // Actividad principal
    public Path guardarFicha(Pokemon pokemon) throws IOException {
        return escribir(carpeta.resolve("pokemon").resolve(pokemon.getNombre() + ".json"),
                pokemon.getJsonOriginal());
    }
    // Ampliación 2: imágenes.
    public Path rutaImagen(Pokemon pokemon) {
        return carpeta.resolve("imagenes").resolve(pokemon.getNombre() + ".png");
    }
    // Ampliación 3: comparación. Ya hecho
    public Path guardarComparacion(ResultadoComparacion resultado) throws IOException {
        return escribir(carpeta.resolve("comparaciones").resolve(resultado.primero().getNombre()
                + "_vs_" + resultado.segundo().getNombre() + ".txt"), resultado.informe());
    }
    private Path escribir(Path destino, String contenido) throws IOException {
        Files.createDirectories(destino.getParent());
        // writeString usa CREATE y TRUNCATE_EXISTING: crea o sustituye, siempre en UTF-8.
        Files.writeString(destino, contenido, StandardCharsets.UTF_8);
        return destino;
    }
}
