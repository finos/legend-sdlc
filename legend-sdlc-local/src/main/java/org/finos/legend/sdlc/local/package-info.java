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

/**
 * Local and embedded use of Legend SDLC — the contract for IDE-plugin authors (re-architecture section 4). Just as
 * the backend SPI is the contract for backend authors, this package is the published, stable surface for tooling
 * that works on a local checkout with no server and no container: a plain {@code main}, a Maven plugin, or another
 * product's JVM (an IntelliJ or LSP process is exactly that JVM).
 *
 * <p>The surface is two-tiered, matching the two forms of section 4.4:
 *
 * <ul>
 * <li>{@link org.finos.legend.sdlc.local.LocalModel} — the <b>entities-only</b> tier (Form 2, a Legend model
 * embedded in a larger non-Legend project): open a directory, read and edit entities, validate. No deployment
 * environment of any kind is involved.</li>
 * <li>{@link org.finos.legend.sdlc.local.ManagedLocalModel} — the <b>structure-aware</b> tier (Form 1, a fully
 * managed project edited via the IDE): adds project configuration updates, taking the deployment's
 * {@link org.finos.legend.sdlc.project.structure.extension.ProjectStructureExtensionProvider} as an explicit input
 * and degrading deliberately when it is absent.</li>
 * </ul>
 *
 * <p>{@link org.finos.legend.sdlc.local.LocalModelDiscovery} enumerates the models in a checkout;
 * {@link org.finos.legend.sdlc.local.LocalProjectFileAccessProvider} is the storage underneath, usable directly by
 * tooling that wants the file-level SPI rather than the model façade. Validation is reported as data
 * ({@link org.finos.legend.sdlc.local.LocalModelDiagnostic}): SDLC diagnoses configuration and layout; model
 * semantics belong to the Engine.
 *
 * <p>The dependency footprint is deliberately lean and shade-friendly: the SDLC layers L0–L3 (model, shared,
 * entity serialization, storage SPI, structure, core) and their two libraries, Jackson (transitively, for
 * {@code project.json} and entity JSON) and Eclipse Collections. No Dropwizard, Guice, JAX-RS, pac4j, or any
 * backend or server code — plugins shade what they see here and fight nothing else. Types from L0
 * ({@code Entity}, {@code EntityChange}, {@code ProjectDependency}, …) and the structure/extension interfaces from
 * L2 appear in these signatures by design; they are part of the contract.
 *
 * <p>All handles follow the same lifecycle and threading rules: {@code open → use → close}, explicit
 * {@code refresh()} to reconcile with external changes to the working tree, no thread safety per handle (callers
 * serialize), full independence between handles.
 */
package org.finos.legend.sdlc.local;
