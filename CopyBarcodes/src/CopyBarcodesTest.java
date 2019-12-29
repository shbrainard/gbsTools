import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;

public class CopyBarcodesTest {

	@Test
	public void smokeTest() throws Exception {
		setUpTestFiles();
		CopyBarcodes.main(new String[] {"testForward.gz", "testBackwards.gz", "testBarcodes.txt"});
		checkOutput(2, true);
		CopyBarcodes.main(new String[] {"testForward.gz", "testBackwards.gz", "testBarcodes.txt", "retain"});
		checkOutput(4, false);
		CopyBarcodes.main(new String[] {"testForward.gz", "testBackwards.gz", "testBarcodes.txt", "fuzzy"});
		checkOutput(3, true);
		CopyBarcodes.main(new String[] {"testForward.gz", "testBackwards.gz", "testBarcodes.txt", "fuzzy", "debug"});
		checkOutput(3, true);
	}
	
	private void checkOutput(int numExpected, boolean checkFwd) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new GZIPInputStream(new FileInputStream("testForward.barcoded.forward.gz"))));
		int nReads = 0;
		if (checkFwd) {
			while (reader.readLine() != null) {
				nReads++;
				reader.readLine();
				reader.readLine();
				reader.readLine();
			}
			assert nReads == numExpected;
		}
		reader.close();
		reader = new BufferedReader(new InputStreamReader(
				new GZIPInputStream(new FileInputStream("testBackwards.barcoded.reverse.gz"))));
		nReads = 0;
		while (reader.readLine() != null) {
			nReads++;
			String read = reader.readLine();
			assert read.startsWith("AAAA");
			reader.readLine();
			reader.readLine();
		}
		reader.close();
		assert nReads == numExpected;
	}

	// dummy test has one good, one bad, one fuzzed, one good
	private void setUpTestFiles() throws Exception {
		GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream("testForward.gz"));
		
		// write a good read
		out.write("Header\n".getBytes());
		out.write("AAAACAGCAAACCCGGGTTTAAA\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFFFFFFFFFFFFFFFFFFFF\n".getBytes());
		
		// write a bad read
		out.write("Header\n".getBytes());
		out.write("AAAAAAAAAAACCCGGGTTTAAA\n".getBytes());
		out.write("+\n".getBytes());
		out.write(",,,,,,,,,FFFFFFFFFFFFF\n".getBytes());
		
		// write a fuzzable read
		out.write("Header\n".getBytes());
		out.write("AAAACCGCAAACCCGGGTTTAAA\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF,FFFFFFFFFFFFFFFFF\n".getBytes());
		
		// write a good read
		out.write("Header\n".getBytes());
		out.write("AAAACAGCAAACCCGGGTTTAAA\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFFFFFFFFFFFFFFFFFFFF\n".getBytes());
		
		out.close();
		
		// write matching reverse reads
		out = new GZIPOutputStream(new FileOutputStream("testBackwards.gz"));
		out.write("Header\n".getBytes());
		out.write("CCCCC\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF\n".getBytes());
		
		// hack the bad read to make the assert in checkOutput simpler
		out.write("Header\n".getBytes());
		out.write("AAAAA\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF\n".getBytes());
		
		out.write("Header\n".getBytes());
		out.write("TTTTT\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF\n".getBytes());
		
		out.write("Header\n".getBytes());
		out.write("GGGGG\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF\n".getBytes());
		out.close();
		
		FileOutputStream barcodeOut = new FileOutputStream("testBarcodes.txt");
		barcodeOut.write(("AAAA" + "\tfoo\n").getBytes());
		barcodeOut.close();
	}
}
