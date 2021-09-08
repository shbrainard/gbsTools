import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

import org.junit.Test;

public class DownsamplerTest {
	@Test
	public void demultiplexTest() throws Exception {
		DemultiplexerTest.setUpTestFiles();
		DemultiplexerTest.clearOldFiles();
		
		DemultiplexerTest.createTestConfig(true, "percentToRetain=100");
		Downsampler.main(new String[] {"test.config"});
		checkOutput(3, true, "testForward.gz");
		checkOutput(3, true, "testBackwards.gz");
		
		DemultiplexerTest.createTestConfig(true, "percentToRetain=10");
		Downsampler.main(new String[] {"test.config"});
		checkOutput(3, false, "testForward.gz");
		checkOutput(3, false, "testBackwards.gz");
		
		DemultiplexerTest.createTestConfig(true, "percentToRetain=50", "retainByTruncating=true");
		Downsampler.main(new String[] {"test.config"});
		checkOutput(1, false, "testForward.gz");
		checkOutput(1, false, "testBackwards.gz");
	}
	
	private void checkOutput(int numExpected, boolean exact, String suffix) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new GZIPInputStream(new FileInputStream("truncated_" + suffix))));
		int nReads = 0;
		while (reader.readLine() != null) {
			nReads++;
			reader.readLine();
			reader.readLine();
			reader.readLine();
		}
		assert exact ? nReads == numExpected : nReads < numExpected;
		reader.close();
	}
}
