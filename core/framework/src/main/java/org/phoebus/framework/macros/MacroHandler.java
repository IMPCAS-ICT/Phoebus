/*******************************************************************************
 * Copyright (c) 2015-2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.framework.macros;

/** Handler for {@link Macros}
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class MacroHandler
{
    /** Max. recursion level to guard against recursive macros that never resolve */
    // In principle, could try to detect loops like
    // A=$(B)
    // B=$(A)
    // Current implementation quits after MAX_RECURSION attempts because
    // that's much simpler and plenty fast.
    private static final int MAX_RECURSION = 100;

    /** Check if input contains unresolved macros
     *  @param input Text that may contain macros "$(NAME)" or "${NAME}", even escaped ones because they need to be un-escaped
     *  @return <code>true</code> if there is at least one potential macro
     */
    public static boolean containsMacros(final String input)
    {
        return input != null  &&  input.indexOf('$') >= 0;
    }

    /** Replace macros in input
     *
     *  @param macros {@link MacroValueProvider} to use
     *  @param input Text that may contain macros "$(NAME)" or "${NAME}",
     *               also allowing nested "${{INNER}}"
     *  @return Text where all macros have been resolved
     *  @throws Exception on error, including recursive macro that never resolves
     */
    public static String replace(final MacroValueProvider macros, final String input) throws Exception
    {
        // Replace macros, then un-escape escaped dollar signs
        final String replaced = replace(0, macros, input, 0);
        return replaced.replace("\\$", "$");
    }

    /** Replace macros in input
     *
     *  @param recursions Recursion counter
     *  @param macros {@link MacroValueProvider} to use
     *  @param input Text that may contain macros "$(NAME)" or "${NAME}",
     *               also allowing nested "${{INNER}}"
     *  @param from Position within input where replacement should start
     *  @return Text where all macros have been resolved
     *  @throws Exception on error
     */
    private static String replace(final int recursions,
                                  final MacroValueProvider macros, final String input,
                                  final int from) throws Exception
    {
        if (recursions > MAX_RECURSION)
            throw new Exception("Recursive macro " + input + ". Values: " + macros);

        // Recursion and default values:
        // Default values provide a possible way to resolve recursive macros. If recursion
        // is detected, the default value could be used.
        // However, with the current implementation, there is no way to recover the original
        // default value. For example, replacing $(S=a) with the macro S=$(S) would throw an
        // error, since the expected default value, "a", is overwritten on the first recursion.

        DecomposedMacroValue decomposedMacroValue = new DecomposedMacroValue(input, from);

        if(decomposedMacroValue.macroName == null){
            return input;
        }

        // Resolve
        if (Macros.MACRO_NAME_PATTERN.matcher(decomposedMacroValue.macroName).matches())
        {
            final String value = macros.getValue(decomposedMacroValue.macroName);

            DecomposedMacroValue decomposedValue = new DecomposedMacroValue(value);

            if(decomposedMacroValue.macroName.equals(decomposedValue.macroName)){
                return replace(recursions + 1, macros, decomposedValue.defaultValue != null ? decomposedValue.defaultValue : value, 0);
            }
            else if(value != null || decomposedMacroValue.defaultValue != null)
            {
                // Replace macro in input, removing the '$(' resp. ')'
                final String result = input.substring(0, decomposedMacroValue.start) +
                        (value != null ? value : decomposedMacroValue.defaultValue) +
                        input.substring(decomposedMacroValue.end + 1);
                // Text has now changed.
                // Subsequent calls to find() would return indices for the original text
                // which are no longer valid for the changed text
                // -> Recurse with updated text for next macro,
                //    which also handles nested $($(INNER))
                return replace(recursions + 1, macros, result, 0);
            }
        }
        // Leave macro unresolved, continue with remaining input
        return replace(recursions + 1, macros, input, decomposedMacroValue.start + 2);
    }

    /** @param input Input that might contain '(..)' or '{..}'
     *  @param pos Position of opening '(' or '{'
     *  @return Position of closing ')' respectively '}', or -1
     */
    public static int findClosingBrace(final String input, int pos)
    {
        final int N = input.length();
        if (pos >= N)
            return -1;

        final char closing;
        char c = input.charAt(pos);
        if (c == '(')
            closing = ')';
        else if (c == '{')
            closing = '}';
        else
            return -1;

        while (++pos < N)
        {
            c = input.charAt(pos);
            // Skip escaped chars
            if (c == '\\')
            {
                ++pos;
                continue;
            }
            if (c == closing)
                return pos;
            if (c == '('  ||  c == '{')
            {   // Find closing sub-brace
                pos = findClosingBrace(input, pos);
                if (pos < 0)
                    return -1;
            }
        }

        return -1;
    }

    /**
     * Decomposes a macro value into name and default value (if present).
     * Main justification is to avoid duplication of macro parser code.
     */
    public static class DecomposedMacroValue{

        private String macroName;
        private String defaultValue;
        private int start;
        private int end;

        public DecomposedMacroValue(String input){
            parse(input, 0);
        }

        public DecomposedMacroValue(String input, int from){
            parse(input, from);
        }

        private void parse(String input, int from){
            if(input == null){
                return;
            }
            // Find first un-escaped $(.. or ${..
            start = input.indexOf('$', from);
            while (start > 0 && input.charAt(start-1) == '\\') {
                start = input.indexOf('$', start + 1);
            }

            // Short cut if there is nothing to replace
            if (start < 0 || start + 1 >= input.length()){
                defaultValue = input;
                return;
            }

            // Is there a ( or { ?
            if (start + 1 >= input.length()) {
                defaultValue = input;
                return;
            }

            // Find end of $(..) or ${..}
            end = findClosingBrace(input, start+1);
            if (end < 0){
                defaultValue = input;
                return;
            }

            // Find macro name
            String name = input.substring(start + 2, end);
            int sep = name.indexOf('=');
            if (sep > 0)
            {
                defaultValue = name.substring(sep + 1).trim();
                name = name.substring(0, sep);
            }
            macroName = name;
        }
    }
}
