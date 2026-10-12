package es.marianasanz.psep.pokedex;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.InputMismatchException;
import java.util.Scanner;

public class Main {
    private final Scanner entrada;
    private final ClientePokeApi cliente = new ClientePokeApi();
    private final GestorArchivos archivos = new GestorArchivos();
    private final ServicioPokedex servicio = new ServicioPokedex();
    private Main(Scanner entrada) { this.entrada = entrada; }

    public static void main(String[] argumentos) {
        // Mantener la salida en UTF-8 también en terminales Windows.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        try (Scanner entrada = new Scanner(System.in, StandardCharsets.UTF_8)) {
            new Main(entrada).ejecutar();
        }
    }
    // Actividad principal: cada opción lee líneas con el mismo Scanner.
    private void ejecutar() {
        boolean continuar = true;
        while (continuar && !Thread.currentThread().isInterrupted()) {
            System.out.println("\n1. Consultar Pokémon.\n2. Descargar imagen de un Pokémon.\n3. Comparar dos Pokémon.\n0. Salir.");
            System.out.print("Opción: ");
            if (!entrada.hasNextLine()) throw new InputMismatchException();
            String opcion = entrada.nextLine();
            if (opcion == null) break;
            try {
                switch (opcion.trim()) {
                    case "1" -> consultar();
                    case "2" -> descargar();
                    case "3" -> comparar();
                    case "0" -> continuar = false;
                    default -> System.out.println("Opción incorrecta. Introduce 0, 1, 2 o 3.");
                }
            } catch (ExcepcionPokedex error) {
                System.out.println(error.getMessage());
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                System.out.println("Operación interrumpida. Se termina de forma ordenada.");
                continuar = false;
            }
        }
        System.out.println("Fin de la Pokédex.");
    }

    private Pokemon solicitarPokemon(String mensaje) throws ExcepcionPokedex, InterruptedException {
        System.out.print(mensaje);
        if (!entrada.hasNextLine()) return cliente.consultarPokemon(null);
        String texto = entrada.nextLine();
        return cliente.consultarPokemon(texto);
    }
    private void guardarFicha(Pokemon pokemon) {
        try {
            Path ruta = archivos.guardarFicha(pokemon);
            System.out.println("Ficha guardada en: " + ruta.toAbsolutePath());
        } catch (IOException error) {
            System.out.println("No se pudo guardar la ficha: " + error.getMessage());
        }
    }
    private void consultar() throws ExcepcionPokedex, InterruptedException {
        Pokemon pokemon = solicitarPokemon("Introduce el nombre o identificador del Pokémon: ");
        servicio.mostrarFicha(pokemon);
        guardarFicha(pokemon);
        descargarImagen(pokemon);
    }
    // Ampliación 2: imágenes.
    private void descargar() throws ExcepcionPokedex, InterruptedException {
        Pokemon pokemon = solicitarPokemon("Introduce el nombre o identificador del Pokémon: ");
        descargarImagen(pokemon);
    }
    private void descargarImagen(Pokemon pokemon) throws ExcepcionPokedex, InterruptedException {
        Path destino = archivos.rutaImagen(pokemon);
        if (Files.exists(destino)) {
            while (true) {
                System.out.print("La imagen ya existe. ¿Conservar (c) o descargar de nuevo (d)? ");
                if (!entrada.hasNextLine()) throw new InputMismatchException();
                String respuesta = entrada.nextLine();
                if (respuesta == null) return;
                if (respuesta.trim().equalsIgnoreCase("c")) {
                    System.out.println("Imagen conservada: " + destino.toAbsolutePath());
                    return;
                }
                if (respuesta.trim().equalsIgnoreCase("d")) break;
                System.out.println("Introduce c o d.");
            }
        }
        Path guardada = cliente.descargarImagen(pokemon, destino);
        System.out.println("Imagen guardada en: " + guardada.toAbsolutePath());
    }
    // Ampliación 3: comparación.
    private void comparar() throws ExcepcionPokedex, InterruptedException {
        Pokemon primero = solicitarPokemon("Primer Pokémon (nombre o identificador): ");
        Pokemon segundo = solicitarPokemon("Segundo Pokémon (nombre o identificador): ");
        ResultadoComparacion resultado = servicio.compararPokemon(primero, segundo);
        System.out.print(resultado.informe());
        try { System.out.println("Informe guardado en: " + archivos.guardarComparacion(resultado).toAbsolutePath()); }
        catch (IOException error) { System.out.println("No se pudo guardar el informe: " + error.getMessage()); }
    }
}
