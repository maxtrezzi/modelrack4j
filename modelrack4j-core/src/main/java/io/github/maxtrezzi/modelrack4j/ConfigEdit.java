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
package io.github.maxtrezzi.modelrack4j;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A change to one configuration layer, applied and stored together.
 *
 * <p>Collect the changes, then {@link #commit()}:
 *
 * <pre>{@code
 * registry.edit(userLayer)
 *         .set("SL.model-name", "claude-opus-5")
 *         .set("SL.temperature", 0.2)
 *         .remove("SH")
 *         .commit();
 * }</pre>
 *
 * <p><strong>Paths are relative to the {@code llm} block</strong>, so {@code "SL.model-name"}
 * means {@code llm.SL.model-name}. An edit cannot reach outside {@code llm}. A configuration
 * name containing a dot or a space has to be quoted the way HOCON quotes it —
 * {@code "\"my.name\".model-name"} — which is one more reason to keep names simple.
 *
 * <p><strong>Only the keys you name are written.</strong> Writing a whole block into a
 * higher-precedence layer would copy every value that block inherited from the layers below
 * and freeze it there, so a later edit to the base layer would stop reaching it — silently,
 * because the configuration would still be valid. Naming single keys avoids that.
 *
 * <h2>What a commit does, in order</h2>
 *
 * <ol>
 *   <li>prepares the new text, without storing it anywhere yet;
 *   <li>re-reads every other layer and validates the whole result, exactly as a reload does,
 *       so an edit that would break the configuration is refused before anything changes;
 *   <li>publishes the new configuration;
 *   <li>stores the text — and if that fails, puts the previous configuration back and throws,
 *       having told no listener anything.
 * </ol>
 *
 * <p><strong>No reload event is fired for your own edit</strong>, and no flag is needed to
 * arrange that. The change is applied before it is stored, so when a file watcher later
 * notices the file and re-reads it, what it finds is what is already live: the difference is
 * empty and nothing is published. A listener hears about changes made by somebody else, which
 * is what a listener is for.
 *
 * <h2>Secrets</h2>
 *
 * <p>An edit works on the layer's <em>unresolved</em> text, so a {@code ${VAR}} already in the
 * file stays a {@code ${VAR}} and no resolved secret is ever written. Note what that means for
 * a value you supply: {@link #set(String, Object)} with the string {@code "${API_KEY}"} writes
 * those characters as a quoted string, not as a substitution. Use
 * {@link #setSubstitution(String, String)} when you mean the variable.
 *
 * <h2>Formatting</h2>
 *
 * <p>The layer is rewritten rather than patched, so it comes back in a canonical form: keys
 * sorted, indentation normalised, and a comment that followed a value on the same line moved
 * to the line above it. Comments and substitutions survive; alignment and key order do not.
 * <strong>Point a writable layer at a file your application owns</strong>, not at one a person
 * maintains by hand.
 *
 * <p>For the same reason <strong>a layer that uses {@code include} cannot be edited</strong>,
 * and says so rather than dropping the directive. Keep what an application writes in a layer
 * of its own, above the one that includes.
 *
 * @implNote Not thread-safe, and not meant to be: build one, commit it, discard it. The
 *     commit itself is serialised against reloads.
 */
public final class ConfigEdit {

    /** One `set` or `remove`, kept in call order so the caller's sequence is what happens. */
    private record Operation(String path, Optional<ConfigValue> value) {
    }

    /**
     * An {@code include} directive at the start of a field. Deliberately lexical: an edit
     * refuses a layer that has one at all, rather than trying to decide whether this
     * particular one would survive.
     */
    private static final Pattern INCLUDE = Pattern.compile("(?m)^\\s*include\\s");

    private static final ConfigRenderOptions RENDER = ConfigRenderOptions.defaults()
            .setJson(false)
            .setOriginComments(false)
            .setComments(true)
            .setFormatted(true);

    private final LlmRegistry registry;
    private final WritableConfigSource target;
    private final List<Operation> operations = new ArrayList<>();

    /** Set once {@link #commit()} has succeeded; a spent edit refuses to run again. */
    private boolean committed;

    ConfigEdit(LlmRegistry registry, WritableConfigSource target) {
        this.registry = registry;
        this.target = target;
    }

    /**
     * Sets a value.
     *
     * @param path the key, relative to the {@code llm} block
     * @param value a string, number, boolean, list or map. A map writes a whole block, which
     *     is how a configuration name that does not exist yet is added.
     * @return this edit
     * @throws ConfigValidationException if the path is blank or the value has a type HOCON
     *     cannot hold
     * @throws NullPointerException if the value is null — use {@link #remove(String)}
     */
    public ConfigEdit set(String path, Object value) {
        Objects.requireNonNull(value, "value; use remove(path) to delete a key");
        operations.add(new Operation(requirePath(path), Optional.of(toValue(path, value))));
        return this;
    }

    /**
     * Sets a value to a substitution, written as {@code ${name}} rather than as whatever
     * that name currently stands for.
     *
     * <p>This is how a new block gets an API key without the key reaching the stored text.
     * Use it whenever the value must stay a reference: {@link #set(String, Object)} with the
     * string {@code "${OPENAI_API_KEY}"} stores those characters as a quoted string, which
     * then means nothing.
     *
     * <p>The name is resolved the way every other substitution in this library is: against
     * the merged configuration first and the environment after it, so it can be an
     * environment variable such as {@code OPENAI_API_KEY} or a key another layer defines. It
     * is mandatory rather than optional, so a name that resolves to nothing fails the next
     * load loudly instead of leaving a model that cannot authenticate.
     *
     * @param path the key, relative to the {@code llm} block
     * @param name what to substitute, for example {@code OPENAI_API_KEY}
     * @return this edit
     * @throws ConfigValidationException if the path or the name is blank, or the name is not
     *     something HOCON can write unquoted
     */
    public ConfigEdit setSubstitution(String path, String name) {
        String variable = ConfigSources.requireUsableId(name);
        if (!variable.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new ConfigValidationException("A substitution name may hold only letters,"
                    + " digits and underscores, and may not start with a digit: " + name);
        }
        Config holder = ConfigFactory.parseString("value = ${" + variable + "}");
        operations.add(new Operation(
                requirePath(path), Optional.of(holder.root().get("value"))));
        return this;
    }

    /**
     * Removes a key, or a whole block when the path names one.
     *
     * <p>Removing it from this layer does not remove it from the configuration: a lower layer
     * that also defines it becomes visible again, which is the point of layering. Removing a
     * name no layer defines any more takes it out of the registry, and {@code get(name)} then
     * throws.
     *
     * @param path the key, relative to the {@code llm} block
     * @return this edit
     * @throws ConfigValidationException if the path is blank
     */
    public ConfigEdit remove(String path) {
        operations.add(new Operation(requirePath(path), Optional.empty()));
        return this;
    }

    /**
     * Returns the layer's text as it is stored right now, before this edit.
     *
     * <p>Unresolved, so it shows {@code ${API_KEY}} rather than a key. That makes it safe to
     * show to a person — and it is the thing to show, because it is what a commit rewrites.
     *
     * @return the target layer's current text
     * @throws ConfigValidationException if the layer cannot be read
     */
    public String text() {
        return target.text();
    }

    /**
     * Validates, publishes and stores this edit.
     *
     * @return what changed, or empty when the stored text was rewritten but the configuration
     *     it describes is the same — reordering keys, or setting a value to what it already
     *     was
     * @throws ConfigValidationException if this edit has no operations, if the result does not
     *     parse or validate, or if the text cannot be stored
     * @throws RuntimeException whatever a provider's builder threw. Nothing was published and
     *     nothing was stored.
     */
    public Optional<ReloadChange> commit() {
        if (committed) {
            throw new ConfigValidationException("This edit was already committed. Start"
                    + " another with registry.edit(...) rather than reusing this one, whose"
                    + " changes are already in the layer.");
        }
        if (operations.isEmpty()) {
            throw new ConfigValidationException(
                    "This edit changes nothing; add a set(...) or a remove(...) before"
                            + " committing");
        }
        Optional<ReloadChange> change = registry.commitEdit(this);
        committed = true;
        return change;
    }

    /** @return the layer this edit writes */
    WritableConfigSource target() {
        return target;
    }

    /**
     * Applies the operations to the target's current text and renders the result.
     *
     * @implNote Called by the registry with the reload lock held, never before. Reading the
     *     layer outside that lock and committing inside it loses an update whenever two edits
     *     overlap: both read the same text, both add their own change to it, and the second
     *     write erases the first. Measured before this was moved: 199 of 200 rounds with two
     *     concurrent commits lost one of the two changes.
     */
    String render() {
        Config config = parseCurrent();
        for (Operation operation : operations) {
            String absolute = SnapshotLoader.ROOT_PATH + "." + operation.path();
            try {
                config = operation.value().isPresent()
                        ? config.withValue(absolute, operation.value().get())
                        : config.withoutPath(absolute);
            } catch (ConfigException e) {
                throw new ConfigValidationException("Cannot apply the change to "
                        + operation.path() + " in " + target.id() + ": " + e.getMessage(), e);
            }
        }
        return config.root().render(RENDER);
    }

    private Config parseCurrent() {
        String current = text();
        if (INCLUDE.matcher(current).find()) {
            // A commit rewrites the layer from its parsed form, and an include survives
            // neither route: parsed as text it resolves to nothing and the directive is
            // rendered away, and parsed through the file its contents would be inlined into
            // this layer and frozen there. Refusing is the only answer that does not quietly
            // change what the file means (ADR-0042 is the same hazard, on the read side).
            throw new ConfigValidationException("The layer " + target.id() + " uses an"
                    + " include, and an edit rewrites a layer rather than patching it, so the"
                    + " include would be lost. Put the values an application edits in a layer"
                    + " of their own, above the one that includes.");
        }
        try {
            // Parsed, never resolved: resolving here would turn every ${VAR} into its value
            // and the commit would then write the secrets into the layer.
            return ConfigFactory.parseString(current);
        } catch (ConfigException e) {
            throw new ConfigValidationException("The layer being edited, " + target.id()
                    + ", does not parse as it stands: " + e.getMessage(), e);
        }
    }

    private static String requirePath(String path) {
        if (path == null || path.isBlank()) {
            throw new ConfigValidationException(
                    "A path is required, relative to the '" + SnapshotLoader.ROOT_PATH
                            + "' block — for example \"SL.model-name\"");
        }
        return path;
    }

    private static ConfigValue toValue(String path, Object value) {
        try {
            return ConfigValueFactory.fromAnyRef(value);
        } catch (ConfigException e) {
            throw new ConfigValidationException("The value given for " + path + " is a "
                    + value.getClass().getName() + ", which HOCON cannot hold. Use a string,"
                    + " a number, a boolean, a list or a map: " + e.getMessage(), e);
        }
    }
}
