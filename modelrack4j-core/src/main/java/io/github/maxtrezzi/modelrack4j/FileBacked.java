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
 * A layer whose text lives in a real file, and which therefore must be parsed through that
 * file rather than through its text.
 *
 * @implNote This exists so that {@code ConfigLoader.parse} can recognise more than one kind
 *     of file-backed source. {@code include "sibling.conf"} resolves relative to the file
 *     that contains the line, and only {@code parseFile} knows which file that is; handing
 *     the same bytes to {@code parseString} makes the includer fall back to the classpath,
 *     where an allow-missing include quietly finds nothing (ADR-0042). Validating a text
 *     about to be stored needs the same treatment, against the not-yet-committed file.
 */
interface FileBacked extends ConfigSource {

    /** @return the file to parse */
    Path file();
}
