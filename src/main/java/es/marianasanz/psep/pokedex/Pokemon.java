package es.marianasanz.psep.pokedex;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import java.util.*;

/** Actividad principal: modelo y lectura explícita de los campos JSON. */
public class Pokemon {
    public static final List<String> ESTADISTICAS = List.of(
            "hp", "attack", "defense", "special-attack", "special-defense", "speed");
    public static final List<String> ETIQUETAS = List.of(
            "Puntos de salud", "Ataque", "Defensa", "Ataque especial", "Defensa especial", "Velocidad");
    private final int id, altura, peso;
    private final String nombre, jsonOriginal, urlImagen;
    private final List<String> tipos;
    private final Map<String, Integer> estadisticas;

    private Pokemon(int id, String nombre, int altura, int peso, List<String> tipos,
                    Map<String, Integer> estadisticas, String urlImagen, String jsonOriginal) {
        this.id = id; this.nombre = nombre; this.altura = altura; this.peso = peso;
        this.tipos = List.copyOf(tipos); this.estadisticas = Map.copyOf(estadisticas);
        this.urlImagen = urlImagen; this.jsonOriginal = jsonOriginal;
    }

    public static Pokemon desdeJson(String json) throws ExcepcionPokedex {
        try {
            JsonReader lector = new JsonReader(new StringReader(json));
            lector.setStrictness(Strictness.STRICT);
            JsonObject objeto = JsonParser.parseReader(lector).getAsJsonObject();
            int id = entero(objeto, "id");
            String nombre = texto(objeto, "name");
            int altura = entero(objeto, "height"), peso = entero(objeto, "weight");
            if (id <= 0 || altura < 0 || peso < 0 || !nombre.matches("[a-z0-9-]+")) {
                throw new IllegalArgumentException("Identidad o medidas no válidas");
            }
            List<String> tipos = new ArrayList<>();
            for (JsonElement elemento : objeto.getAsJsonArray("types")) {
                tipos.add(texto(elemento.getAsJsonObject().getAsJsonObject("type"), "name"));
            }
            if (tipos.isEmpty()) throw new IllegalArgumentException("Faltan tipos");
            Map<String, Integer> estadisticas = new HashMap<>();
            // El orden del array no importa: cada valor se asocia a stat.name.
            for (JsonElement elemento : objeto.getAsJsonArray("stats")) {
                JsonObject dato = elemento.getAsJsonObject();
                String clave = texto(dato.getAsJsonObject("stat"), "name");
                int valor = entero(dato, "base_stat");
                if (valor < 0 || estadisticas.putIfAbsent(clave, valor) != null) {
                    throw new IllegalArgumentException("Estadística no válida o repetida");
                }
            }
            for (String clave : ESTADISTICAS) {
                if (!estadisticas.containsKey(clave)) throw new IllegalArgumentException("Falta " + clave);
            }
            // Ampliación 2: la imagen es opcional, nunca se inventa una URL.
            String imagen = null;
            JsonElement sprites = objeto.get("sprites");
            if (sprites != null && !sprites.isJsonNull()) {
                JsonElement frontal = sprites.getAsJsonObject().get("front_default");
                if (frontal != null && !frontal.isJsonNull()) {
                    imagen = texto(sprites.getAsJsonObject(), "front_default");
                }
            }
            return new Pokemon(id, nombre, altura, peso, tipos, estadisticas, imagen, json);
        } catch (RuntimeException error) {
            throw new ExcepcionPokedex("Error de procesamiento JSON: respuesta incompleta o no válida ("
                    + error.getMessage() + ").", error);
        }
    }

    private static int entero(JsonObject objeto, String campo) {
        JsonElement valor = objeto.get(campo);
        if (valor == null || !valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Falta un número: " + campo);
        return valor.getAsBigDecimal().intValueExact();
    }
    private static String texto(JsonObject objeto, String campo) {
        JsonElement valor = objeto.get(campo);
        if (valor == null || !valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Falta un texto: " + campo);
        return valor.getAsString();
    }
    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public double getAlturaMetros() { return altura / 10.0; }
    public double getPesoKilogramos() { return peso / 10.0; }
    public List<String> getTipos() { return tipos; }
    public int getEstadistica(String clave) { return estadisticas.get(clave); }
    public String getJsonOriginal() { return jsonOriginal; }
    public String getUrlImagen() { return urlImagen; }
}
