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

import java.nio.file.Path;

/**
 * A configuration layer an application can write back to.
 *
 * <p>Reading a layer says nothing about whether it can be written, so this is a separate
 * interface: a base layer shipped inside a jar is read-only, and the layer holding a user's
 * own choices is not. {@link LlmRegistry#edit(WritableConfigSource)} takes one of these and
 * nothing else, so a layer that cannot be written cannot be named as a target by mistake.
 *
 * <p>Implement it for a layer the library does not know how to write — a row in a database, a
 * value in a configuration service. For a file, {@link ConfigSource#ofWritableFile(Path)}
 * gives you one already.
 *
 * @implSpec {@link #write(String)} replaces the layer's <strong>whole</strong> text, and the
 *     text it is given is what {@link #text()} must return afterwards. It is called at most
 *     once per {@link ConfigEdit#commit()}, with the registry's reload lock held, so it must
 *     not call back into the registry and must not block for an unbounded time.
 *     <p>Make it as close to atomic as the medium allows. A reader that catches half a write
 *     sees a broken layer: for the file implementation that means writing a temporary file
 *     and moving it into place, and for a database row it means one {@code UPDATE}.
 */
public interface WritableConfigSource extends ConfigSource {

    /**
     * Replaces this layer's entire text.
     *
     * @param text the new HOCON text, never {@code null}. It is unresolved: any
     *     {@code ${VAR}} it contains is written through as written, which is what keeps a
     *     secret out of the stored text.
     * @throws ConfigValidationException if the text cannot be stored. The edit is then rolled
     *     back, so nothing was published and no listener ran.
     */
    void write(String text);
}
