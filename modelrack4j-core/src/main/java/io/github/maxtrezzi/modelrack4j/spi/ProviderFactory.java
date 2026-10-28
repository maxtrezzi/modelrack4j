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
package io.github.maxtrezzi.modelrack4j.spi;

import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.github.maxtrezzi.modelrack4j.ConfigValidationException;
import io.github.maxtrezzi.modelrack4j.LlmConfig;
import java.util.Optional;

/**
 * Builds LangChain4j model objects for one provider.
 *
 * <p>One implementation per provider, each in its own module, discovered with
 * {@link java.util.ServiceLoader} from
 * {@code META-INF/services/io.github.maxtrezzi.modelrack4j.spi.ProviderFactory}. Core depends
 * on no provider artifact, so the set of usable providers is exactly the set of provider
 * modules on the classpath.
 *
 * <p>Implementations must be stateless and safe for concurrent use: one instance serves every
 * configuration naming its provider.
 *
 * <p>Each model has two {@code create} methods. Core calls the one that takes a
 * {@link ModelContext}, which carries the listeners the application registered. The one that
 * takes only the configuration is what a factory written before {@code 0.4.0} implements, and
 * the two-argument form delegates to it by default, so such a factory keeps working. A factory
 * written for this version implements both: the two-argument form builds the model, and the
 * one-argument form calls it with {@link ModelContext#empty()}.
 */
public interface ProviderFactory {

    /**
     * Returns the value this factory matches against a block's {@code provider} key.
     *
     * @return a stable id such as {@code "openai"}, never null or blank
     */
    String providerId();

    /**
     * Returns how this provider counts tokens, which decides whether token-window memory is
     * possible and whether it needs an explicit opt-in.
     *
     * @return the estimation class, never null
     */
    TokenEstimation tokenEstimation();

    /**
     * Returns whether a block naming this provider may set {@code api-key}, and whether it
     * must.
     *
     * <p>Core applies the answer before {@link #validate(LlmConfig)} is called, so a factory
     * that declares {@link KeyRequirement#MANDATORY} can rely on
     * {@link LlmConfig#apiKey()} being present, and one that declares
     * {@link KeyRequirement#FORBIDDEN} can rely on it being absent.
     *
     * @return the requirement, never null
     * @implSpec There is no default, on purpose (ADR-0062). Any default would be wrong for
     *     some provider, and {@code MANDATORY}, the obvious one, would refuse every block of a
     *     provider that takes no credential and forgot to override this. A compile error is
     *     cheaper than that.
     */
    KeyRequirement apiKeyRequirement();

    /**
     * Returns whether a block naming this provider may set {@code base-url}, and whether it
     * must.
     *
     * <p>Core applies the answer before {@link #validate(LlmConfig)} is called, as it does for
     * {@link #apiKeyRequirement()}. A provider whose client has a default address, such as
     * the vendor's own, returns {@link KeyRequirement#OPTIONAL}; one with no default address,
     * such as a server the user runs, returns {@link KeyRequirement#MANDATORY}.
     *
     * @return the requirement, never null
     * @implSpec There is no default, for the reason given on {@link #apiKeyRequirement()}.
     */
    KeyRequirement baseUrlRequirement();

    /**
     * Returns whether this provider can build a {@code ModerationModel}, which decides
     * whether a block may set {@code moderation.enabled = true}.
     *
     * <p>Of the providers in this repository, OpenAI and Mistral can. Reporting it here rather
     * than rejecting it in {@link #validate(LlmConfig)} keeps the rule and its message in core,
     * beside the {@link #tokenEstimation()} rules, so every provider refuses the same
     * configuration in the same words.
     *
     * @return {@code true} if {@link #createModerationModel(LlmConfig)} can produce a model
     * @implSpec The default is {@code true}, which is not a claim that most providers
     *     moderate — it is what keeps a factory written against an earlier version working
     *     unchanged. Such a factory does not override this, so core lets the configuration
     *     through and the missing model is still caught a moment later, when
     *     {@link #createModerationModel(LlmConfig)} returns empty. Overriding it changes
     *     only which of the two messages the user sees, and how early.
     */
    default boolean supportsModeration() {
        return true;
    }

    /**
     * Checks anything this provider cannot support, beyond what the config record already
     * validates — most often a capability the configuration asks for and the provider lacks.
     *
     * <p>Rules that depend only on {@link #tokenEstimation()}, {@link #supportsModeration()},
     * {@link #apiKeyRequirement()} or {@link #baseUrlRequirement()} are applied by core, before
     * this method runs, and must not be restated here.
     *
     * @param config the validated configuration naming this provider
     * @throws ConfigValidationException if this provider cannot honour the configuration
     */
    void validate(LlmConfig config);

    /**
     * Builds the chat model, without listeners.
     *
     * <p>Core does not call this; it calls {@link #createChatModel(LlmConfig, ModelContext)}.
     * Implement it as {@code createChatModel(config, ModelContext.empty())}.
     *
     * @param config the validated configuration
     * @return the chat model, never null
     */
    ChatModel createChatModel(LlmConfig config);

    /**
     * Builds the chat model. Every bundle has one, so this never returns empty.
     *
     * @param config the validated configuration
     * @param context the listeners to pass to the model's builder
     * @return the chat model, never null
     * @implSpec The default calls {@link #createChatModel(LlmConfig)} and ignores the
     *     context, which is what keeps a factory written before {@code 0.4.0} working
     *     unchanged (ADR-0063). Such a factory builds a model without the listeners, and core
     *     refuses that model when the context had any. A factory that overrides this must
     *     pass {@link ModelContext#chatModelListeners()} to the builder, and the model's
     *     {@code listeners()} must report them. A factory that implements the one-argument
     *     form by calling this one and does not override this one fails here with an
     *     {@code IllegalStateException} that says so, rather than recursing.
     */
    default ChatModel createChatModel(LlmConfig config, ModelContext context) {
        return DefaultCreate.delegate(this, "createChatModel", () -> createChatModel(config));
    }

    /**
     * Builds the streaming chat model, without listeners.
     *
     * <p>Core does not call this; it calls
     * {@link #createStreamingChatModel(LlmConfig, ModelContext)}. Implement it as
     * {@code createStreamingChatModel(config, ModelContext.empty())}.
     *
     * @param config the validated configuration
     * @return the streaming model, or empty when {@code streaming} is false
     */
    Optional<StreamingChatModel> createStreamingChatModel(LlmConfig config);

    /**
     * Builds the streaming chat model, if the configuration asked for one.
     *
     * @param config the validated configuration
     * @param context the listeners to pass to the model's builder
     * @return the streaming model, or empty when {@code streaming} is false
     * @implSpec The default calls {@link #createStreamingChatModel(LlmConfig)}, for the
     *     reason given on {@link #createChatModel(LlmConfig, ModelContext)}. The streaming
     *     model takes the same listeners as the chat model.
     */
    default Optional<StreamingChatModel> createStreamingChatModel(
            LlmConfig config, ModelContext context) {
        return DefaultCreate.delegate(this, "createStreamingChatModel",
                () -> createStreamingChatModel(config));
    }

    /**
     * Builds the moderation model, without listeners.
     *
     * <p>Core does not call this; it calls
     * {@link #createModerationModel(LlmConfig, ModelContext)}. Implement it as
     * {@code createModerationModel(config, ModelContext.empty())}.
     *
     * @param config the validated configuration
     * @return the moderation model, or empty
     */
    Optional<ModerationModel> createModerationModel(LlmConfig config);

    /**
     * Builds the moderation model, if the configuration asked for one and this provider has
     * one.
     *
     * @param config the validated configuration
     * @param context the listeners to pass to the model's builder
     * @return the moderation model, or empty
     * @implSpec The default calls {@link #createModerationModel(LlmConfig)}, for the reason
     *     given on {@link #createChatModel(LlmConfig, ModelContext)}. A factory that
     *     overrides this must pass {@link ModelContext#moderationModelListeners()} to the
     *     builder.
     */
    default Optional<ModerationModel> createModerationModel(
            LlmConfig config, ModelContext context) {
        return DefaultCreate.delegate(this, "createModerationModel",
                () -> createModerationModel(config));
    }

    /**
     * Builds the token count estimator that token-window memory needs.
     *
     * <p>It takes no {@link ModelContext}: LangChain4j defines no listener for an estimator.
     *
     * @param config the validated configuration
     * @return the estimator, or empty when {@link #tokenEstimation()} is
     *     {@link TokenEstimation#ABSENT}
     */
    Optional<TokenCountEstimator> createTokenCountEstimator(LlmConfig config);
}
