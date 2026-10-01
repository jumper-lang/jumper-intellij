package me.padej.jumper.idea.highlighter;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

/**
 * The colors of Jumper, each falling back to the one Java code gets in the user's scheme (the language is
 * colored as Java is - lsp-guide §3), and each overridable on its own in Settings | Editor | Color Scheme | Jumper.
 * Besides Java's: `dyn`, the host's globals, the built-in functions and the keys of table literals.
 */
public final class JumperHighlighterColors {
    private JumperHighlighterColors() {}

    // lexical
    public static final TextAttributesKey KEYWORD = createTextAttributesKey("JUMPER_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey DYN = createTextAttributesKey("JUMPER_DYN", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey NUMBER = createTextAttributesKey("JUMPER_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey STRING = createTextAttributesKey("JUMPER_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey VALID_ESCAPE = createTextAttributesKey("JUMPER_VALID_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE);
    public static final TextAttributesKey LINE_COMMENT = createTextAttributesKey("JUMPER_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey BLOCK_COMMENT = createTextAttributesKey("JUMPER_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    public static final TextAttributesKey OPERATOR = createTextAttributesKey("JUMPER_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    public static final TextAttributesKey PARENTHESES = createTextAttributesKey("JUMPER_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES);
    public static final TextAttributesKey BRACES = createTextAttributesKey("JUMPER_BRACES", DefaultLanguageHighlighterColors.BRACES);
    public static final TextAttributesKey BRACKETS = createTextAttributesKey("JUMPER_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);
    public static final TextAttributesKey COMMA = createTextAttributesKey("JUMPER_COMMA", DefaultLanguageHighlighterColors.COMMA);
    public static final TextAttributesKey DOT = createTextAttributesKey("JUMPER_DOT", DefaultLanguageHighlighterColors.DOT);
    public static final TextAttributesKey SEMICOLON = createTextAttributesKey("JUMPER_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON);
    public static final TextAttributesKey IDENTIFIER = createTextAttributesKey("JUMPER_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);
    public static final TextAttributesKey BAD_CHARACTER = createTextAttributesKey("JUMPER_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);

    // semantic (the annotator)
    public static final TextAttributesKey LOCAL_VARIABLE = createTextAttributesKey("JUMPER_LOCAL_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
    public static final TextAttributesKey TOP_LEVEL_VARIABLE = createTextAttributesKey("JUMPER_TOP_LEVEL_VARIABLE", DefaultLanguageHighlighterColors.GLOBAL_VARIABLE);
    public static final TextAttributesKey PARAMETER = createTextAttributesKey("JUMPER_PARAMETER", DefaultLanguageHighlighterColors.PARAMETER);
    public static final TextAttributesKey FIELD = createTextAttributesKey("JUMPER_FIELD", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey STATIC_FIELD = createTextAttributesKey("JUMPER_STATIC_FIELD", DefaultLanguageHighlighterColors.STATIC_FIELD);
    public static final TextAttributesKey FUNCTION_DECLARATION = createTextAttributesKey("JUMPER_FUNCTION_DECLARATION", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION);
    public static final TextAttributesKey FUNCTION_CALL = createTextAttributesKey("JUMPER_FUNCTION_CALL", DefaultLanguageHighlighterColors.FUNCTION_CALL);
    public static final TextAttributesKey METHOD_CALL = createTextAttributesKey("JUMPER_METHOD_CALL", DefaultLanguageHighlighterColors.INSTANCE_METHOD);
    public static final TextAttributesKey STATIC_METHOD_CALL = createTextAttributesKey("JUMPER_STATIC_METHOD_CALL", DefaultLanguageHighlighterColors.STATIC_METHOD);
    public static final TextAttributesKey CLASS_NAME = createTextAttributesKey("JUMPER_CLASS_NAME", DefaultLanguageHighlighterColors.CLASS_NAME);
    public static final TextAttributesKey INTERFACE_NAME = createTextAttributesKey("JUMPER_INTERFACE_NAME", DefaultLanguageHighlighterColors.INTERFACE_NAME);
    public static final TextAttributesKey PROPERTY = createTextAttributesKey("JUMPER_PROPERTY", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey TABLE_KEY = createTextAttributesKey("JUMPER_TABLE_KEY", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey HOST_GLOBAL = createTextAttributesKey("JUMPER_HOST_GLOBAL", DefaultLanguageHighlighterColors.GLOBAL_VARIABLE);
    public static final TextAttributesKey BUILTIN_FUNCTION = createTextAttributesKey("JUMPER_BUILTIN_FUNCTION", DefaultLanguageHighlighterColors.STATIC_METHOD);
}
