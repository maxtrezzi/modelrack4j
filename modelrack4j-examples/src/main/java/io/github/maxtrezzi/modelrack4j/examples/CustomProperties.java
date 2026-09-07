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

import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.CustomPropertiesHandler;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import io.github.maxtrezzi.modelrack4j.LlmRegistry;
import io.github.maxtrezzi.modelrack4j.WritableConfigSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shows values the application owns, carried inside the block they belong to.
 *
 * <p>A named configuration describes one connection to one model. Applications usually have a
 * few values that belong to that connection and that this library has no business
 * understanding — which prompt template, how many retries. They go in a {@code
 * custom-properties} block, which the library carries as text and never reads.
 *
 * <p>Reading the text is not the point, and this example is about the part that is. Register
 * a {@link CustomPropertiesHandler} and your own rules run <em>inside</em> the reload:
 *
 * <ol>
 *   <li>the registry starts, and each block's properties arrive as an object of ours;
 *   <li>a block with no {@code custom-properties} at all still reaches the handler, as
 *       {@code "{}"} — which is what lets a rule about a <em>missing</em> value fire;
 *   <li>a good edit is applied, and the bundle whose properties changed is the only one
 *       rebuilt;
 *   <li>an edit our own rules reject leaves the whole previous configuration live, exactly as
 *       a broken {@code model-name} would;
 *   <li>the same rejected change offered through {@code store()} is refused before the layer
 *       is written, so nobody has to repair anything.
 * </ol>
 *
 * <p>Steps 4 and 5 are one change in the two possible orders, and the difference is what the
 * layer holds afterwards. Writing first and reloading second leaves the broken text where it
 * was written; {@code store()} validates first, so a refusal costs nothing.
 *
 * <p>Step 4 is the reason the feature exists. Without it, a prompt template your application
 * cannot parse would be published beside a model that is fine, and you would find out on the
 * next request rather than at the reload.
 *
 * <p>It sends no request and needs no API key, so it costs nothing to run.
 */
public final class CustomProperties {

    /** What this application keeps beside each model. Any binding library would do. */
    record Support(String promptId, int maxRetries) {

        @Override
        public String toString() {
            return "prompt-id=" + promptId + " max-retries=" + maxRetries;
        }
    }

    private static final String TWO_MODELS = """
            llm {
              SL {
                provider = openai
                api-key = "unused-no-request-is-sent"
                model-name = "gpt-5.1-mini"
                custom-properties { prompt-id = "support-v3", max-retries = 3 }
              }
              SH {
                provider = openai
                api-key = "unused-no-request-is-sent"
                model-name = "gpt-5.1"
              }
            }
            """;

    private CustomProperties() {
    }

    /**
     * Runs the five steps.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        AtomicReference<String> row = new AtomicReference<>(TWO_MODELS);
        WritableConfigSource layer = new WritableConfigSource() {
            @Override
            public String id() {
                return "application_config#1";
            }

            @Override
            public String text() {
                return row.get();
            }

            @Override
            public void write(String text) {
                row.set(text);
            }
        };

        try (LlmRegistry<Support> registry = LlmRegistry.builder()
                .sources(List.of(layer))
                .customPropertiesHandler(CustomProperties::rules)
                .build()) {

            System.out.println();
            System.out.println("1. The registry is built. Each block's properties are ours now.");
            for (String name : registry.names()) {
                System.out.println("   " + name + ": " + registry.get(name).customProperties()
                        + "   (text: " + registry.get(name).customPropertiesText() + ")");
            }
            System.out.println();
            System.out.println("2. SH has no custom-properties block at all. The handler still");
            System.out.println("   saw it, as \"{}\", which is how a rule about a missing value");
            System.out.println("   can fire — see rules() below.");

            System.out.println();
            System.out.println("3. A good edit: SL's prompt template moves to support-v4.");
            var bundleOfSh = registry.get("SH");
            row.set(TWO_MODELS.replace("support-v3", "support-v4"));
            registry.reload().ifPresent(change ->
                    System.out.println("   reload() says updated=" + change.updated()
                            + " added=" + change.added() + " removed=" + change.removed()));
            System.out.println("   SL now: " + registry.get("SL").customProperties());
            System.out.println("   SH was not touched, so it is the same object: "
                    + (registry.get("SH") == bundleOfSh));

            System.out.println();
            System.out.println("4. An edit our own rules reject: max-retries = 99.");
            row.set(TWO_MODELS.replace("max-retries = 3", "max-retries = 99"));
            try {
                registry.reload();
                System.out.println("   !!! it was accepted, which this example exists to deny");
            } catch (ConfigValidationException rejected) {
                System.out.println("   rejected: " + firstLine(rejected));
            }
            System.out.println("   still live, untouched: " + registry.get("SL").customProperties());
            System.out.println("   but the layer now holds text the registry refused, because we");
            System.out.println("   wrote it there before asking. Repairing it is our job:");
            row.set(TWO_MODELS.replace("support-v3", "support-v4"));
            System.out.println("   repaired.");

            System.out.println();
            System.out.println("5. The same change through store(), which writes only what it");
            System.out.println("   has already accepted.");
            try {
                registry.store(layer, TWO_MODELS.replace("max-retries = 3", "max-retries = 99"));
                System.out.println("   !!! it was written, which this example exists to deny");
            } catch (ConfigValidationException rejected) {
                System.out.println("   rejected: " + firstLine(rejected));
            }
            System.out.println("   and the layer still holds max-retries = "
                    + (row.get().contains("max-retries = 3") ? "3" : "99"));

            System.out.println();
            System.out.println("Nothing your application cannot use ever reached a live");
            System.out.println("configuration. That is the whole difference between putting");
            System.out.println("these values here and keeping a file of your own.");
            System.out.println();
        }
    }

    /**
     * The application's own rules, run inside every build, reload and store.
     *
     * @param config the configuration being built, whose custom-properties block is text
     * @return what this application wants to hold beside that model
     * @throws IllegalArgumentException to reject the configuration
     */
    private static Support rules(LlmConfig config) {
        String json = config.customPropertiesText();
        String promptId = field(json, "prompt-id");
        int maxRetries = json.contains("max-retries")
                ? Integer.parseInt(field(json, "max-retries"))
                : 0;

        // A rule that depends on the block's own name, which is why the handler is given the
        // whole configuration rather than only the text.
        if ("SL".equals(config.name()) && promptId == null) {
            throw new IllegalArgumentException("SL is the support model and needs a prompt-id");
        }
        if (maxRetries > 5) {
            throw new IllegalArgumentException(
                    "max-retries is " + maxRetries + ", which this application will not do");
        }
        return new Support(promptId, maxRetries);
    }

    /** A deliberately small JSON reader, so the example needs no binding library. */
    private static String field(String json, String key) {
        int at = json.indexOf('"' + key + '"');
        if (at < 0) {
            return null;
        }
        String rest = json.substring(json.indexOf(':', at) + 1).trim();
        return rest.startsWith("\"")
                ? rest.substring(1, rest.indexOf('"', 1))
                : rest.split("[,}]")[0].trim();
    }

    private static String firstLine(Exception e) {
        return String.valueOf(e.getMessage()).split("\n")[0];
    }
}
