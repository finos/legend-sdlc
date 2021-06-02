// Copyright 2021 Goldman Sachs
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

package org.finos.legend.sdlc.test.junit;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import junit.framework.TestCase;
import org.finos.legend.engine.test.runner.shared.JsonNodeComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public abstract class LegendSDLCTestCase extends TestCase
{
    // ALLOW_SINGLE_QUOTES option set to true as we are deserializing test data, which will have had escape characters removed
    // when deserializing tests contained in a mapping - See test in SingleQuoteInResultM2M.json
    protected static final ObjectMapper objectMapper = new ObjectMapper().configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES,true);

    private Logger logger;

    protected LegendSDLCTestCase()
    {
        super();
    }

    @Override
    public void setName(String name)
    {
        super.setName(name);
        this.logger = (name == null) ? null : LoggerFactory.getLogger(name);
    }

    @Override
    protected final void setUp() throws Exception
    {
        long start = System.nanoTime();
        this.logger.info("Setting up");
        try
        {
            super.setUp();
            doSetUp();
            long end = System.nanoTime();
            this.logger.info("Finished setting up ({}s)", formatNanosDuration(end - start));
        }
        catch (Throwable t)
        {
            try
            {
                doTearDown();
            }
            catch (Throwable suppress)
            {
                this.logger.error("Error attempting to clean up (suppressing)", suppress);
                t.addSuppressed(suppress);
            }
            long end = System.nanoTime();
            this.logger.error("Error setting up (" + formatNanosDuration(end - start) + "s)", t);
            throw t;
        }
    }

    @Override
    protected final void runTest() throws Exception
    {
        long start = System.nanoTime();
        this.logger.info("Starting test");
        try
        {
            doRunTest();
            long end = System.nanoTime();
            this.logger.info("SUCCESS ({}s)", formatNanosDuration(end - start));
        }
        catch (AssertionError e)
        {
            long end = System.nanoTime();
            this.logger.info("FAILURE ({}s)", formatNanosDuration(end - start));
            throw e;
        }
        catch (Throwable t)
        {
            long end = System.nanoTime();
            this.logger.info("ERROR ({}s)", formatNanosDuration(end - start));
            throw t;
        }
    }

    @Override
    protected final void tearDown() throws Exception
    {
        long start = System.nanoTime();
        this.logger.info("Tearing down");
        try
        {
            super.tearDown();
            doTearDown();
            long end = System.nanoTime();
            this.logger.info("Finished tearing down ({}s)", formatNanosDuration(end - start));
        }
        catch (Throwable t)
        {
            long end = System.nanoTime();
            this.logger.error("Error tearing down (" + formatNanosDuration(end - start) + "s)", t);
            throw t;
        }
    }

    protected abstract void doSetUp() throws Exception;

    protected abstract void doRunTest() throws Exception;

    protected abstract void doTearDown() throws Exception;

    protected Logger getLogger()
    {
        return this.logger;
    }

    protected void assertEquals(JsonNode expected, JsonNode actual)
    {
        assertEquals(expected, actual, false);
    }

    protected void assertEquals(JsonNode expected, JsonNode actual, boolean nullEqualsMissing)
    {
        if (notEqual(expected, actual, nullEqualsMissing))
        {
            assertEquals(serializeForFailureMessage(expected), serializeForFailureMessage(actual));
        }
    }

    protected void assertEquals(List<? extends JsonNode> expected, List<? extends JsonNode> actual)
    {
        assertEquals(expected, actual, false);
    }

    protected void assertEquals(List<? extends JsonNode> expected, List<? extends JsonNode> actual, boolean nullEqualsMissing)
    {
        if (notEqual(expected, actual, nullEqualsMissing))
        {
            assertEquals(serializeForFailureMessage(expected), serializeForFailureMessage(actual));
        }
    }

    protected void assertSetEquals(List<? extends JsonNode> expected, List<? extends JsonNode> actual)
    {
        assertSetEquals(expected, actual, false);
    }

    protected void assertSetEquals(List<? extends JsonNode> expected, List<? extends JsonNode> actual, boolean nullEqualsMissing)
    {
        assertEquals(toSetForComparison(expected, nullEqualsMissing), toSetForComparison(actual, nullEqualsMissing));
    }

    private boolean notEqual(Collection<? extends JsonNode> expected, Collection<? extends JsonNode> actual, boolean nullEqualsMissing)
    {
        if (expected.size() != actual.size())
        {
            return true;
        }
        Iterator<? extends JsonNode> expectedIter = expected.iterator();
        Iterator<? extends JsonNode> actualIter = actual.iterator();
        while (expectedIter.hasNext())
        {
            if (notEqual(expectedIter.next(), actualIter.next(), nullEqualsMissing))
            {
                return true;
            }
        }
        return false;
    }

    private boolean notEqual(JsonNode expected, JsonNode actual, boolean nullEqualsMissing)
    {
        return getComparator(nullEqualsMissing).compare(expected, actual) != 0;
    }

    private <N extends JsonNode> Set<N> toSetForComparison(List<N> nodes, boolean nullEqualsMissing)
    {
        if (nodes.isEmpty())
        {
            return Collections.emptySet();
        }

        Set<N> set = new TreeSet<>(getComparator(nullEqualsMissing));
        set.addAll(nodes);
        return set;
    }

    private JsonNodeComparator getComparator(boolean nullEqualsMissing)
    {
        return nullEqualsMissing ? JsonNodeComparator.NULL_MISSING_EQUIVALENT : JsonNodeComparator.MISSING_BEFORE_NULL;
    }

    private String serializeForFailureMessage(Collection<? extends JsonNode> nodes)
    {
        try
        {
            List<Object> values = new ArrayList<>(nodes.size());
            for (JsonNode node : nodes)
            {
                values.add(objectMapper.treeToValue(node, Object.class));
            }
            return objectMapper.writer(SerializationFeature.INDENT_OUTPUT, SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(values);
        }
        catch (JsonProcessingException e)
        {
            return nodes.toString();
        }
    }

    private String serializeForFailureMessage(JsonNode node)
    {
        try
        {
            Object value = objectMapper.treeToValue(node, Object.class);
            return objectMapper.writer(SerializationFeature.INDENT_OUTPUT, SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(value);
        }
        catch (JsonProcessingException e)
        {
            return node.toString();
        }
    }

    protected void delete(Path path)
    {
        // Get attributes
        BasicFileAttributes attributes;
        try
        {
            attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }
        catch (IOException e)
        {
            // Path does not exist or is not accessible - give up
            this.logger.debug("Could not get attributes for " + path + " - giving up trying to delete it", e);
            return;
        }

        // If path is a directory, delete the directory contents
        if (attributes.isDirectory())
        {
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(path))
            {
                dirStream.forEach(this::delete);
            }
            catch (Exception e)
            {
                this.logger.debug("Error deleting directory contents for " + path, e);
            }
        }

        // Delete path
        try
        {
            Files.delete(path);
        }
        catch (Exception e)
        {
            // Try to make it writable, and then try again to delete
            this.logger.debug("Error deleting " + path + " - will try again", e);
            if (!Files.isWritable(path))
            {
                if (!path.toFile().setWritable(true, false))
                {
                    this.logger.debug("Failed to make {} writable", path);
                }
            }
            try
            {
                Files.delete(path);
            }
            catch (Exception ee)
            {
                // Give up
                this.logger.debug("Error deleting " + path + " - giving up", ee);
            }
        }
    }

    private static String formatNanosDuration(long durationNanos)
    {
        if (durationNanos == 0)
        {
            return "0.000000000";
        }

        StringBuilder builder = new StringBuilder(20);
        builder.append(durationNanos);
        int decimalIndex = builder.length() - 9;
        int startIndex = (durationNanos < 0) ? 1 : 0;
        if (decimalIndex > startIndex)
        {
            builder.insert(decimalIndex, '.');
        }
        else if (decimalIndex < startIndex)
        {
            char[] chars = new char[-decimalIndex + 2];
            Arrays.fill(chars, '0');
            chars[1] = '.';
            builder.insert(startIndex, chars);
        }
        else
        {
            // decimalIndex == 0
            builder.insert(startIndex, "0.");
        }
        return builder.toString();
    }
}