// Copyright 2026 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.sdlc.local;

import java.util.Objects;

/**
 * A validation finding on a local model, as data (re-architecture section 4.5): what is wrong, how bad it is, and —
 * when the finding is about a particular file — where. Diagnostics cover what the SDLC owns: the project
 * configuration and the layout of entity files within the project structure. Model semantics (whether the entities
 * compile, whether references resolve) are the Engine's concern and are never diagnosed here.
 */
public class LocalModelDiagnostic
{
    public enum Severity
    {
        /**
         * The model violates its configuration or layout contract; operations relying on the violated part will
         * fail or misbehave.
         */
        ERROR,

        /**
         * Something is irregular but operations remain reliable.
         */
        WARNING
    }

    public enum Category
    {
        /**
         * The project configuration ({@code project.json}): parseability, structure version, ids, dependencies.
         */
        CONFIGURATION,

        /**
         * The arrangement of files against the project structure.
         */
        LAYOUT,

        /**
         * An individual entity file: deserializability, path/package agreement.
         */
        ENTITY
    }

    private final Severity severity;
    private final Category category;
    private final String message;
    private final String filePath;

    LocalModelDiagnostic(Severity severity, Category category, String message, String filePath)
    {
        this.severity = Objects.requireNonNull(severity);
        this.category = Objects.requireNonNull(category);
        this.message = Objects.requireNonNull(message);
        this.filePath = filePath;
    }

    public Severity getSeverity()
    {
        return this.severity;
    }

    public Category getCategory()
    {
        return this.category;
    }

    public String getMessage()
    {
        return this.message;
    }

    /**
     * Project-relative path of the file the finding is about, or null when the finding is not about a particular
     * file.
     *
     * @return file path or null
     */
    public String getFilePath()
    {
        return this.filePath;
    }

    @Override
    public String toString()
    {
        return "<" + this.severity + " " + this.category + ((this.filePath == null) ? "" : (" " + this.filePath)) + ": " + this.message + ">";
    }
}
