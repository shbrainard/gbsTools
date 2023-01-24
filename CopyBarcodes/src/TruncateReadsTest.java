import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;

public class TruncateReadsTest {

	@Test
	public void smokeTest() throws Exception {
		DemultiplexerTest.setUpTestFiles();
		DemultiplexerTest.clearOldFiles();
		
		TruncateReads.main(new String[] {"file=testForward.gz", "max_read_length=3", "barcode_length=4"});
		checkOutput(7, "testForward.truncated.gz");
	}

	private void checkOutput(int maxLength, String file) throws Exception {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new GZIPInputStream(new FileInputStream(file))))) {
			String line;
			int maxLenFound = 0;
			int i = 0;
			while ((line = reader.readLine()) != null) {
				if (i % 2 == 1) {
					assert line.length() <= maxLength;
					if (line.length() > maxLenFound) {
						maxLenFound = line.length();
					}
				}
				i++;
			}
			assert maxLenFound == maxLength;
		}
	}
}
