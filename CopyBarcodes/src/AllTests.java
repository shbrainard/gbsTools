import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({ByteBasedProgressTrackerTest.class, ConfigTest.class, CopyBarcodesTest.class,
	DemultiplexerTest.class, DownsamplerTest.class, PrefixTreeTest.class, TruncateReadsTest.class})
public class AllTests {

}
