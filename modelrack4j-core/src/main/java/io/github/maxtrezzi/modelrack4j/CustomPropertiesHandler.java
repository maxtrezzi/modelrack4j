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

/**
 * Turns the text of a configuration's {@code custom-properties} block into an object of your
 * own.
 *
 * <p>The library never reads what is inside that block. It renders the block as JSON, hands
 * you the text, and keeps whatever you return on the bundle. Parsing <em>is</em> validation:
 * if your handler cannot produce its object it throws, and the configuration is rejected — on
 * the first build, on a reload, and on a store, all in the same swap as the model the block
 * belongs to.
 *
 * <pre>{@code
 * LlmRegistry<SupportProps> registry = LlmRegistry.builder()
 *         .configFiles(List.of(base, local))
 *         .customPropertiesHandler(config -> mapper.readValue(
 *                 config.customPropertiesText(), SupportProps.class))
 *         .build();
 *
 * SupportProps props = registry.get("SUPPORT").customProperties();
 * }</pre>
 *
 * <p>It receives the whole {@link LlmConfig}, so a rule can depend on the block's name or on
 * its provider, and reads the block through {@link LlmConfig#customPropertiesText()}. It is
 * called for every configuration, including one with no {@code custom-properties} block at
 * all, which arrives as {@code "{}"} — a rule such as "an openai block needs a prompt id" is
 * broken exactly in that case, so skipping it would skip the rule.
 *
 * <p>It runs while a reload holds the registry's lock, so it must be quick: no I/O, no
 * blocking, no network. That is the same contract a provider factory already works under.
 *
 * @param <T> the type you turn the block into
 * @see LlmRegistry.Builder#customPropertiesHandler(CustomPropertiesHandler)
 */
@FunctionalInterface
public interface CustomPropertiesHandler<T> {

    /**
     * Builds your object from one configuration.
     *
     * @param config the configuration, whose {@link LlmConfig#customPropertiesText()} holds
     *     the block as JSON, or {@code "{}"} when the file has no such block
     * @return your object; it is kept on the bundle and handed back by
     *     {@link LlmBundle#customProperties()}
     * @throws Exception to reject the configuration. The library wraps whatever you throw in
     *     a {@link ConfigValidationException} that names the block and keeps yours as its
     *     cause. {@code Exception} is declared because binding a block usually calls a
     *     library that throws a checked one, and a {@code try}/{@code catch} in every lambda
     *     would be a tax on the ordinary case.
     */
    T handle(LlmConfig config) throws Exception;
}
