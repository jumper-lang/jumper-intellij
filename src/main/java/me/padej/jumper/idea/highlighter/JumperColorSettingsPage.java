package me.padej.jumper.idea.highlighter;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import me.padej.jumper.idea.JumperIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Map;

import static me.padej.jumper.idea.highlighter.JumperHighlighterColors.*;

/** Settings | Editor | Color Scheme | Jumper. */
public final class JumperColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = {
            new AttributesDescriptor("Keyword", KEYWORD),
            new AttributesDescriptor("Keyword//dyn", DYN),
            new AttributesDescriptor("Number", NUMBER),
            new AttributesDescriptor("String//String text", STRING),
            new AttributesDescriptor("String//Escape sequence", VALID_ESCAPE),
            new AttributesDescriptor("Comments//Line comment", LINE_COMMENT),
            new AttributesDescriptor("Comments//Block comment", BLOCK_COMMENT),
            new AttributesDescriptor("Braces and Operators//Operator", OPERATOR),
            new AttributesDescriptor("Braces and Operators//Parentheses", PARENTHESES),
            new AttributesDescriptor("Braces and Operators//Braces", BRACES),
            new AttributesDescriptor("Braces and Operators//Brackets", BRACKETS),
            new AttributesDescriptor("Braces and Operators//Comma", COMMA),
            new AttributesDescriptor("Braces and Operators//Dot", DOT),
            new AttributesDescriptor("Braces and Operators//Semicolon", SEMICOLON),
            new AttributesDescriptor("Identifiers//Default", IDENTIFIER),
            new AttributesDescriptor("Identifiers//Local variable", LOCAL_VARIABLE),
            new AttributesDescriptor("Identifiers//Top-level variable", TOP_LEVEL_VARIABLE),
            new AttributesDescriptor("Identifiers//Parameter", PARAMETER),
            new AttributesDescriptor("Identifiers//Field", FIELD),
            new AttributesDescriptor("Identifiers//Static field", STATIC_FIELD),
            new AttributesDescriptor("Identifiers//Property (Java getter)", PROPERTY),
            new AttributesDescriptor("Identifiers//Table key", TABLE_KEY),
            new AttributesDescriptor("Identifiers//Host global", HOST_GLOBAL),
            new AttributesDescriptor("Functions//Function declaration", FUNCTION_DECLARATION),
            new AttributesDescriptor("Functions//Function call", FUNCTION_CALL),
            new AttributesDescriptor("Functions//Method call", METHOD_CALL),
            new AttributesDescriptor("Functions//Static method call", STATIC_METHOD_CALL),
            new AttributesDescriptor("Functions//Built-in function", BUILTIN_FUNCTION),
            new AttributesDescriptor("Classes//Class", CLASS_NAME),
            new AttributesDescriptor("Classes//Interface", INTERFACE_NAME),
            new AttributesDescriptor("Bad character", BAD_CHARACTER),
    };

    private static final Map<String, TextAttributesKey> TAGS = Map.ofEntries(
            Map.entry("local", LOCAL_VARIABLE), Map.entry("top", TOP_LEVEL_VARIABLE), Map.entry("param", PARAMETER),
            Map.entry("field", FIELD), Map.entry("sfield", STATIC_FIELD), Map.entry("prop", PROPERTY),
            Map.entry("key", TABLE_KEY), Map.entry("global", HOST_GLOBAL), Map.entry("fdecl", FUNCTION_DECLARATION),
            Map.entry("fcall", FUNCTION_CALL), Map.entry("mcall", METHOD_CALL), Map.entry("scall", STATIC_METHOD_CALL),
            Map.entry("builtin", BUILTIN_FUNCTION), Map.entry("cls", CLASS_NAME), Map.entry("iface", INTERFACE_NAME));

    @Override
    public @Nullable Icon getIcon() {
        return JumperIcons.SCRIPT;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new JumperSyntaxHighlighter();
    }

    @Override
    public @NotNull String getDemoText() {
        return """
                // Greets players and remembers how many times each came.
                import java.util.<cls>HashMap</cls>;
                import java.util.<iface>List</iface>;

                dyn <top>visits</top> = new <cls>HashMap</cls>();
                int <top>limit</top> = 0x7F + 1_000;
                dyn <top>colors</top> = { <key>red</key>: "#f00", <key>tab</key>: "a\\tb" };

                class <cls>Counter</cls> {
                    static int <sfield>created</sfield> = 0;
                    int <field>n</field>;
                    <cls>Counter</cls>(int <param>start</param>) { <field>n</field> = <param>start</param>; <sfield>created</sfield>++; }
                    int <fdecl>next</fdecl>() { return ++this.<field>n</field>; }
                }

                void <fdecl>onJoin</fdecl>(dyn <param>p</param>) {
                    int <local>n</local> = <top>visits</top>.<mcall>getOrDefault</mcall>(<param>p</param>.<prop>name</prop>, 0) + 1;
                    <global>server</global>.<mcall>broadcast</mcall>(<param>p</param>.<mcall>name</mcall>() + " joined, visit " + <local>n</local>);
                    <builtin>println</builtin>(<cls>Math</cls>.<scall>max</scall>(<local>n</local>, <top>limit</top>));
                    dyn <local>f</local> = (<param>x</param>) -> <param>x</param> * 2;
                    <fcall>helper</fcall>(<local>f</local>);
                }

                dyn <fdecl>helper</fdecl>(dyn <param>g</param>) { return <param>g</param>(21); }
                """;
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return TAGS;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Jumper";
    }
}
