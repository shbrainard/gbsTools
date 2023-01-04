import java.io.BufferedWriter;
import java.io.FileWriter;

import org.junit.Test;

public class ConfigTest {
	@Test
	public void testLoadList() throws Exception {
		createTestConfig();
		Config config = Config.loadOptions(new String[] {"test.config"});
		assert config.getSourceFileForward().size() == 2;
		assert config.getSourceFileReverse().size() == 2;
		
		config = Config.loadOptions(new String[] {"minQuality=I", "overhang=CAGC,CTGC", "sourceFileForward=testForward.gz,testForward2.gz", "barcodeFile=testBarcodes.txt",
				"sourceFileReverse=testBackwards.gz,testBack2.gz", "fuzzyMatch=false"});
		
		assert config.getSourceFileForward().size() == 2;
		assert config.getSourceFileReverse().size() == 2;
		
	}
	
	private void createTestConfig() throws Exception {
		try (BufferedWriter out = new BufferedWriter(new FileWriter("test.config"))) {
			out.write("minQuality=I");
			out.newLine();
			out.write("overhang=CAGC,CTGC");
			out.newLine();
			out.write("sourceFileForward=testForward.gz,testForward2.gz");
			out.newLine();
			out.write("barcodeFile=testBarcodes.txt");
			out.newLine();
			out.write("sourceFileReverse=testBackwards.gz,testBack2.gz");
			out.newLine();
		}
	}
}
