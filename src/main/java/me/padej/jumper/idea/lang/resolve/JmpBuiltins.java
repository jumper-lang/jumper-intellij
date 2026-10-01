package me.padej.jumper.idea.lang.resolve;

import java.util.List;
import java.util.Map;

/** The built-in functions of the language (runtime.Builtins) and their signatures (lsp.Features.BUILTIN_SIGNATURES). */
public final class JmpBuiltins {
    private JmpBuiltins() {}

    public static final Map<String, List<String>> SIGNATURES = Map.ofEntries(
            Map.entry("print", List.of("print(dyn... values)")),
            Map.entry("println", List.of("println(dyn... values)")),
            Map.entry("len", List.of("int len(dyn value)")),
            Map.entry("str", List.of("String str(dyn value)")),
            Map.entry("type", List.of("String type(dyn value)")),
            Map.entry("int", List.of("int int(dyn value)")),
            Map.entry("long", List.of("long long(dyn value)")),
            Map.entry("double", List.of("double double(dyn value)")),
            Map.entry("nanoTime", List.of("long nanoTime()")),
            Map.entry("millis", List.of("long millis()")),
            Map.entry("keys", List.of("dyn keys(dyn table)")),
            Map.entry("error", List.of("error(dyn message)")),
            Map.entry("assert", List.of("dyn assert(dyn condition)", "dyn assert(dyn condition, dyn message)")),
            Map.entry("format", List.of("String format(String format, dyn... args)")),
            Map.entry("range", List.of("dyn range(int n)", "dyn range(int from, int to)", "dyn range(int from, int to, int step)")),
            Map.entry("array", List.of("dyn array(int n)", "dyn array(int n, dyn fill)")),
            Map.entry("table", List.of("dyn table()")),
            Map.entry("isa", List.of("boolean isa(dyn value, dyn cls)")));

    /** In the order of Builtins.globals(). */
    public static final List<String> NAMES = List.of("print", "println", "len", "str", "type", "int", "long", "double",
            "nanoTime", "millis", "keys", "error", "assert", "format", "range", "array", "table", "isa");

    public static final Map<String, String> DOCS = Map.ofEntries(
            Map.entry("print", "Prints the values separated by spaces."),
            Map.entry("println", "Prints the values separated by spaces, then a line break."),
            Map.entry("len", "The length of a string, array, table, collection, map or Java array."),
            Map.entry("str", "The value as a string, as the language prints it."),
            Map.entry("type", "The type of a value: \"int\", \"string\", \"table\", a class name..."),
            Map.entry("int", "Converts a number, a string (null if it does not parse) or a boolean to int."),
            Map.entry("long", "Converts a number or a string to long."),
            Map.entry("double", "Converts a number or a string to double."),
            Map.entry("nanoTime", "System.nanoTime()."),
            Map.entry("millis", "System.currentTimeMillis()."),
            Map.entry("keys", "The keys of a table or a map, as an array."),
            Map.entry("error", "Throws an error with the message."),
            Map.entry("assert", "Throws \"Assertion failed\" when the condition is false or null; returns it."),
            Map.entry("format", "String.format."),
            Map.entry("range", "An array of integers, the end not included."),
            Map.entry("array", "An array of n copies of fill (the same reference)."),
            Map.entry("table", "A new empty table."),
            Map.entry("isa", "Is the value an instance of the class (a script class or a Java class)?"));

    public static boolean isBuiltin(String name) {
        return SIGNATURES.containsKey(name);
    }

    /** The type a built-in returns when it is a String (for chains like `str(x).length()`). */
    public static boolean returnsString(String name) {
        return name.equals("str") || name.equals("type") || name.equals("format");
    }
}
