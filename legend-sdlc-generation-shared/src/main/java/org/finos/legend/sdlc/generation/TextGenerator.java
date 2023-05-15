// Copyright 2023 Goldman Sachs
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

package org.finos.legend.sdlc.generation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public abstract class TextGenerator<T extends GeneratedText>
{
    private static final Pattern LINEBREAK_PATTERN = Pattern.compile("\\R");

    private final Map<String, Object> parameters = new HashMap<>();
    private final GeneratorTemplate template;
    private String lineBreak;

    protected TextGenerator(GeneratorTemplate template, String defaultLineBreak)
    {
        this.template = Objects.requireNonNull(template);
        setLineBreak(defaultLineBreak);
    }

    protected TextGenerator(GeneratorTemplate template)
    {
        this(template, null);
    }

    public T generate()
    {
        String text = this.template.generateText(Collections.unmodifiableMap(this.parameters));
        return newGeneratedText(processLineBreaks(text));
    }

    protected void setLineBreak(String lineBreak)
    {
        if ((lineBreak != null) && !LINEBREAK_PATTERN.matcher(lineBreak).matches())
        {
            throw new IllegalArgumentException("Invalid line break: \"" + lineBreak + "\"");
        }
        this.lineBreak = lineBreak;
    }

    @SuppressWarnings("unchecked")
    protected <V> V getParameter(String key)
    {
        return (V) this.parameters.get(key);
    }

    @SuppressWarnings("unchecked")
    protected <V> V getOrComputeParameter(String key, Supplier<V> supplier)
    {
        return (V) this.parameters.computeIfAbsent(key, k -> supplier.get());
    }

    protected void setParameter(String key, Object value)
    {
        if (value == null)
        {
            unsetParameter(key);
        }
        else
        {
            this.parameters.put(key, value);
        }
    }

    protected void addToParameter(String key, Object value)
    {
        addAllToParameter(key, Collections.singletonList(value));
    }

    protected void addAllToParameter(String key, Object... values)
    {
        addAllToParameter(key, Arrays.asList(values));
    }

    protected void addAllToParameter(String key, Collection<?> values)
    {
        Collection<Object> collection;
        try
        {
            collection = getOrComputeParameter(key, ArrayList::new);
        }
        catch (ClassCastException e)
        {
            throw new IllegalStateException("Cannot add to parameter \"" + key + "\": not a collection", e);
        }
        collection.addAll(values);
    }

    protected void unsetParameter(String key)
    {
        this.parameters.remove(key);
    }

    protected void clearParameters()
    {
        this.parameters.clear();
    }

    protected abstract T newGeneratedText(String text);

    private String processLineBreaks(String text)
    {
        return (this.lineBreak == null) ? text : LINEBREAK_PATTERN.matcher(text).replaceAll(this.lineBreak);
    }
}
