/*
 * Copyright 2026 maxtrezzi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.maxtrezzi.modelrack4j.examples;

import io.github.maxtrezzi.modelrack4j.ConfigSource;
import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import io.github.maxtrezzi.modelrack4j.ReloadChange;
import io.github.maxtrezzi.modelrack4j.WritableConfigSource;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shows configuration that is not a file, and the reload an application asks for.
 *
 * <p>The four other examples keep their configuration on disk, where the library can watch it
 * and reload by itself. An application that lets its <em>users</em> add a model usually keeps
 * that model in a database instead, and a database row has no directory to watch. Three
 * things follow, and this example is all three.
 *
 * <p><strong>A layer is a {@code ConfigSource}: a label and its text.</strong> The class
 * below stands in for a table. Nothing about it is special — it holds a string and hands it
 * over — and the library never learns where the string came from.
 *
 * <p><strong>The application says when to re-read.</strong> {@link LlmRegistry#reload()} does
 * the same work the file watcher would have done, and returns what changed. Four calls here,
 * showing the four answers it can give:
 *
 * <ol>
 *   <li>a model added, so {@code added} names it;
 *   <li>a model edited, so {@code updated} names it;
 *   <li>nothing changed, so the result is empty and no listener runs;
 *   <li>the new text is invalid, so the call throws and the previous configuration is still
 *       live afterwards — the same rule a bad file follows.
 * </ol>
 *
 * <p><strong>The application can also hand the new text to the registry instead of saving it
 * first.</strong> {@link LlmRegistry#store(WritableConfigSource, String)} validates the text,
 * applies it and stores it as one step. Step 4 shows what the other order costs: the row
 * already holds the broken text by the time the reload rejects it, and somebody has to repair
 * it. Steps 5 and 6 show the same change offered through {@code store} — refused before
 * anything is written, and then applied and stored together.
 *
 * <p>It sends no request and needs no API key, so it costs nothing to run.
 */
public final class DatabaseSource {

    private DatabaseSource() {
    }

    /**
     * A configuration layer held in memory, standing in for a row in a table.
     *
     * @implNote {@code text()} is called on every reload, which is the point: a real
     *     implementation runs its query here. One that read the row once and remembered it
     *     would never report a change.
     */
    private static final class Row implements WritableConfigSource {

        private final AtomicReference<String> stored;

        Row(String initialText) {
            this.stored = new AtomicReference<>(initialText);
        }

        @Override
        public String id() {
            // A label for error messages, not an address: the library only prints it.
            return "llm_config#42";
        }

        @Override
        public String text() {
            return stored.get();
        }

        /**
         * Replaces the row's whole text. A real implementation runs one {@code UPDATE} here.
         *
         * @implNote One statement, not several: a reader that catches a half-written row sees
         *     a broken layer. And it must store nothing at all if it throws, because the
         *     registry puts the previous configuration back on that assumption.
         */
        @Override
        public void write(String newText) {
            stored.set(newText);
        }
    }

    /**
     * Runs the example.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Row row = new Row(models(new Model("SL", "gpt-5.1")));

        try (LlmRegistry registry = LlmRegistry.builder().sources(List.of(row)).build()) {
            System.out.println("Configuration comes from " + row.id()
                    + ", which nothing can watch. The application applies every change below"
                    + " itself: with reload() in steps 1 to 4, and with store() in 5 and 6.");
            print("at startup", registry);

            System.out.println();
            System.out.println("1. The user adds a model. The row is updated, then reloaded.");
            row.write(models(new Model("SL", "gpt-5.1"), new Model("SH", "gpt-5.1")));
            report("reload()", registry.reload());
            print("now", registry);

            System.out.println();
            System.out.println("2. The user edits SL to a different model name.");
            row.write(models(new Model("SL", "gpt-5.1-mini"), new Model("SH", "gpt-5.1")));
            report("reload()", registry.reload());
            print("now", registry);

            System.out.println();
            System.out.println("3. The application reloads without having changed anything.");
            report("reload()", registry.reload());

            String good = models(new Model("SL", "gpt-5.1-mini"), new Model("SH", "gpt-5.1"));
            String broken = "llm { SL { provider = not-a-provider, api-key = \"x\","
                    + " model-name = \"m\" } }";

            System.out.println();
            System.out.println("4. The row is saved with a provider that does not exist, and"
                    + " only then reloaded.");
            row.write(broken);
            try {
                registry.reload();
                System.out.println("   unreachable: the reload should have been rejected");
            } catch (ConfigValidationException rejected) {
                System.out.println("   rejected: " + rejected.getMessage());
            }
            print("still", registry);
            System.out.println("   but the row now holds the broken text, and the application"
                    + " has to put it right: " + oneLine(row.text()));

            System.out.println();
            System.out.println("5. The same change offered through store() instead. The row is"
                    + " repaired first.");
            row.write(good);
            try {
                registry.store(row, broken);
                System.out.println("   unreachable: the store should have been rejected");
            } catch (ConfigValidationException rejected) {
                System.out.println("   rejected: " + rejected.getMessage());
            }
            print("still", registry);
            System.out.println("   and the row was never written: "
                    + oneLine(row.text()));

            System.out.println();
            System.out.println("6. A change that is valid, through store(): validated, applied"
                    + " and stored in one step.");
            report("store()", registry.store(row,
                    models(new Model("SL", "gpt-5.1"), new Model("SH", "gpt-5.1"))));
            print("now", registry);
            System.out.println("   No reload() was needed, and no listener ran: the caller"
                    + " made the change and was given it back.");

            System.out.println();
            System.out.println("Nothing was half-applied: a rejected reload, and a rejected"
                    + " store, both leave the whole previous configuration live. The"
                    + " difference is what they leave in the row.");
        }
    }

    private static void report(String call, Optional<ReloadChange> change) {
        if (change.isEmpty()) {
            System.out.println("   " + call + " returned nothing: the configuration is"
                    + " the same, so no bundle was rebuilt and no listener ran.");
            return;
        }
        ReloadChange c = change.get();
        System.out.println("   " + call + " returned added=" + c.added()
                + " updated=" + c.updated() + " removed=" + c.removed());
    }

    /** The row's text on one line, so a print can show it as it really stands. */
    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    private static void print(String when, LlmRegistry registry) {
        StringBuilder line = new StringBuilder("   " + when + ", the registry holds:");
        for (String name : registry.names()) {
            line.append(' ').append(name).append('=')
                    .append(registry.get(name).config().modelName());
        }
        System.out.println(line);
    }

    /** One named block: the name the application asks for, and the model behind it. */
    private record Model(String name, String modelName) {
    }

    /**
     * Builds the row's text from the blocks it should contain.
     *
     * @implNote A record rather than alternating string arguments: a pair carried as two
     *     positional strings is one miscount away from building the wrong block, or from an
     *     out-of-bounds read on an odd argument list.
     * @implNote Literal api-key: nothing here calls a provider, so no credential is needed
     *     and the example costs nothing to run.
     */
    private static String models(Model... models) {
        StringBuilder hocon = new StringBuilder("llm {\n");
        for (Model model : models) {
            hocon.append("""
                      %s {
                        provider    = openai
                        api-key     = "unused-no-request-is-sent"
                        model-name  = "%s"
                      }
                    """.formatted(model.name(), model.modelName()));
        }
        return hocon.append("}\n").toString();
    }
}
