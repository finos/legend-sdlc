package org.finos.legend.sdlc.test.junit.junit4.model.mapping;

import org.finos.legend.sdlc.test.junit.pure.v1.AbstractMappingTest;
import org.junit.Test;

public class SourceToTargetM2M extends AbstractMappingTest
{
    @Test
    public void test1() throws Exception
    {
        runTest(1);
    }

    @Test
    public void test2() throws Exception
    {
        runTest(2);
    }

    @Override
    protected String getEntityPath()
    {
        return "model::mapping::SourceToTargetM2M";
    }
}
