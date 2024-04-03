package org.finos.legend.sdlc.test.junit.junit4.model;

import org.finos.legend.sdlc.test.junit.pure.v1.AbstractMappingTest;
import org.junit.Test;

public class TestMyMapping extends AbstractMappingTest
{
    @Test
    public void test1() throws Exception
    {
        runTest(1);
    }

    @Override
    protected String getEntityPath()
    {
        return "model::MyMapping";
    }
}
