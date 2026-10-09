package es.marianasanz.psep.pokedex;

/** Ampliación 2: un único resultado calculado sirve para consola e informe. */
public record ResultadoComparacion(Pokemon primero, Pokemon segundo,
                                  int puntosPrimero, int puntosSegundo, String informe) { }
