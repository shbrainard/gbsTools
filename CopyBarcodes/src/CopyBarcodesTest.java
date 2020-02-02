import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;

public class CopyBarcodesTest {

	@Test
	public void smokeTest() throws Exception {
		setUpTestFiles();
		CopyBarcodes.main(new String[] {"testForward.gz", "testBackwards.gz", "testBarcodes.txt", "default.config"});
		checkOutput(2);
		CopyBarcodes.main(new String[] {"testForward.gz", "testBackwards.gz", "testBarcodes.txt",  "default.config", "fuzzy"});
		checkOutput(3);
		CopyBarcodes.main(new String[] {"testForward.gz", "testBackwards.gz", "testBarcodes.txt",  "default.config", "fuzzy", "debug"});
		checkOutput(3);
	}
	
	@Test
	public void testEditDistance() throws Exception {
		Set<String> strings = new HashSet<>();
		strings.add("AAA");
		strings.add("AAAA");
		strings.add("BBB");
		assert 3 == CopyBarcodes.getMinEditDistance(strings);
		
		strings.add("AABA");
		assert 1 == CopyBarcodes.getMinEditDistance(strings);
	}
	
	private void checkOutput(int numExpected) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new GZIPInputStream(new FileInputStream("testForward.interleaved.fq.gz"))));
		int nReads = 0;
		while (reader.readLine() != null) {
			nReads++;
			reader.readLine();
			reader.readLine();
			reader.readLine();
		}
		assert nReads == 2 * numExpected;
		reader.close();
	}

	// dummy test has one good, one bad, one fuzzed, one good
	private void setUpTestFiles() throws Exception {
		GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream("testForward.gz"));
		String header = "@A00589:100:HLKHHDMXX:1:1101:1217:1000:1:N:0:GACTAGGAGC+TAGTACAGGC\n";
		
		// write a good read
		out.write(header.getBytes());
		out.write("AAAACAGCAAACCCGGGTTTAAA\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFFFFFFFFFFFFFFFFFFFF\n".getBytes());
		
		// write a bad read
		out.write(header.getBytes());
		out.write("AAAAAAAAAAACCCGGGTTTAAA\n".getBytes());
		out.write("+\n".getBytes());
		out.write(",,,,,,,,,FFFFFFFFFFFFF\n".getBytes());
		
		// write a bad header
		out.write(header.getBytes());
		out.write("AAAACAGCAAACCCGGGTTTAAA\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFFFFFFFFFFFFFFFFFFFF\n".getBytes());
		
		// write a fuzzable read
		out.write(header.getBytes());
		out.write("AAAACCGCAAACCCGGGTTTAAA\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF,FFFFFFFFFFFFFFFFF\n".getBytes());
		
		// write a good read
		out.write(header.getBytes());
		out.write("AAAACAGCAAACCCGGGTTTAAA\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFFFFFFFFFFFFFFFFFFFF\n".getBytes());
		
		out.close();
		
		// write matching reverse reads
		out = new GZIPOutputStream(new FileOutputStream("testBackwards.gz"));
		out.write(header.getBytes());
		out.write("CCCCC\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF\n".getBytes());
		
		// hack the bad read to make the assert in checkOutput simpler
		out.write(header.getBytes());
		out.write("AAAAA\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF\n".getBytes());
		
		out.write("@A00589:100:HLKHHDMXX:1:1101:1218:1000:1:N:0:GACTAGGAGC+TAGTACAGGC\n".getBytes());
		out.write("CCCCC\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF\n".getBytes());
		
		out.write(header.getBytes());
		out.write("TTTTT\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF\n".getBytes());
		
		out.write(header.getBytes());
		out.write("GGGGG\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF\n".getBytes());
		out.close();
		
		FileOutputStream barcodeOut = new FileOutputStream("testBarcodes.txt");
		barcodeOut.write(("AAAA" + "\tfoo\n").getBytes());
		barcodeOut.close();
	}
}
