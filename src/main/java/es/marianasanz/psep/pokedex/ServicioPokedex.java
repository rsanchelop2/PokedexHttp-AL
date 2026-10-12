package es.marianasanz.psep.pokedex;

import java.io.PrintStream;
import java.util.Locale;

public class ServicioPokedex {
    // Actividad principal.
    public void mostrarFicha(Pokemon pokemon) {
        System.out.println("\nFicha de " + pokemon.getNombre());
        System.out.println("ID: " + pokemon.getId());
        System.out.printf(Locale.ROOT, "Altura: %.1f m%n", pokemon.getAlturaMetros());
        System.out.printf(Locale.ROOT, "Peso: %.1f kg%n", pokemon.getPesoKilogramos());
        System.out.println("Tipos: " + String.join(", ", pokemon.getTipos()));
        System.out.println("Estadísticas:");
        for (int i = 0; i < Pokemon.ESTADISTICAS.size(); i++) {
            System.out.println("  " + Pokemon.ETIQUETAS.get(i) + ": "
                    + pokemon.getEstadistica(Pokemon.ESTADISTICAS.get(i)));
        }
    }

    // Ampliación 2: comparación de estadísticas, no simulación de combate.
    public ResultadoComparacion compararPokemon(Pokemon primero, Pokemon segundo) throws ExcepcionPokedex {
        if (primero.getId() == segundo.getId()) {
            throw new ExcepcionPokedex("No se puede comparar un Pokémon consigo mismo.");
        }
        int puntosPrimero = 0, puntosSegundo = 0;
        int ancho = Math.max(12, Math.max(primero.getNombre().length(), segundo.getNombre().length()));
        String formato = "%-18s | %" + ancho + "s | %" + ancho + "s | %s%n";
        StringBuilder informe = new StringBuilder();
        informe.append("Comparación: ").append(primero.getNombre()).append(" (#").append(primero.getId())
                .append(") y ").append(segundo.getNombre()).append(" (#").append(segundo.getId()).append(")\n");
        informe.append("Comparación de estadísticas base; no es una simulación de combate.\n\n");
        informe.append(String.format(Locale.ROOT, formato, "Estadística", primero.getNombre(), segundo.getNombre(), "Ganador"));
        for (int i = 0; i < Pokemon.ESTADISTICAS.size(); i++) {
            int valorPrimero = primero.getEstadistica(Pokemon.ESTADISTICAS.get(i));
            int valorSegundo = segundo.getEstadistica(Pokemon.ESTADISTICAS.get(i));
            String ganador = "Empate";
            if (valorPrimero > valorSegundo) { puntosPrimero++; ganador = primero.getNombre(); }
            else if (valorSegundo > valorPrimero) { puntosSegundo++; ganador = segundo.getNombre(); }
            informe.append(String.format(Locale.ROOT, formato, Pokemon.ETIQUETAS.get(i), valorPrimero, valorSegundo, ganador));
        }
        informe.append("\nPuntuación: ").append(primero.getNombre()).append(" = ").append(puntosPrimero)
                .append("; ").append(segundo.getNombre()).append(" = ").append(puntosSegundo).append("\n");
        String ganadorGlobal = "Empate";
        if (puntosPrimero > puntosSegundo) ganadorGlobal = primero.getNombre();
        else if (puntosSegundo > puntosPrimero) ganadorGlobal = segundo.getNombre();
        informe.append("Resultado final: ").append(ganadorGlobal).append("\n");
        return new ResultadoComparacion(primero, segundo, puntosPrimero, puntosSegundo, informe.toString());
    }
}
