package legend.demo;

import org.finos.legend.sdlc.test.junit.pure.v1.AbstractMappingTest;
import org.junit.Test;

public class TestSingleQuoteInResultM2M extends AbstractMappingTest
{
    @Test
    public void test1() throws Exception
    {
        runTest(1);
    }

    @Override
    protected String getEntityPath()
    {
        return "legend::demo::SingleQuoteInResultM2M";
    }
}
