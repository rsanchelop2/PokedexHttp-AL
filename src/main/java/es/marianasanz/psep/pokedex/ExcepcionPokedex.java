package es.marianasanz.psep.pokedex;

/** Error recuperable con un mensaje que puede mostrarse al usuario. */
public class ExcepcionPokedex extends Exception {
    public ExcepcionPokedex(String mensaje) { super(mensaje); }
    public ExcepcionPokedex(String mensaje, Throwable causa) { super(mensaje, causa); }
}
