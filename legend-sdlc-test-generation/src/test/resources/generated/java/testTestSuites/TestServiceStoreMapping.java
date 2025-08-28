package testTestSuites;

import org.finos.legend.sdlc.test.junit.pure.v1.AbstractMappingTest;
import org.junit.Test;

public class TestServiceStoreMapping extends AbstractMappingTest
{
    @Test
    public void test1() throws Exception
    {
        runTest(1);
    }

    @Override
    protected String getEntityPath()
    {
        return "testTestSuites::ServiceStoreMapping";
    }
}
