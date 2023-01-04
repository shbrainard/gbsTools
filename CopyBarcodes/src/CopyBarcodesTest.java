import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;

public class CopyBarcodesTest {

	@Test
	public void smokeTest() throws Exception {
		setUpTestFiles();
		createTestConfig(false, false);
		CopyBarcodes.main(new String[] {"test.config"});
		checkOutput(2);
		createTestConfig(true, false);
		CopyBarcodes.main(new String[] {"test.config"});
		checkOutput(3);
		createTestConfig(true, true);
		CopyBarcodes.main(new String[] {"test.config"});
		checkOutput(3);
	}
	
	@Test
	public void testCommandLine() throws Exception {
		setUpTestFiles();
		CopyBarcodes.main(new String[] {"minQuality=I", "overhang=CAGC,CTGC", "sourceFileForward=testForward.gz", "barcodeFile=testBarcodes.txt",
				"sourceFileReverse=testBackwards.gz", "fuzzyMatch=false"});
		checkOutput(2);
	}
	
	@Test
	public void testMultiFile() throws Exception {
		setUpTestFiles();
		try (FileOutputStream out = new FileOutputStream("tf2.gz")) {
			Files.copy(Paths.get("testForward.gz"), out);
		}
		try (FileOutputStream out = new FileOutputStream("tr2.gz")) {
			Files.copy(Paths.get("testBackwards.gz"), out);
		}
		try (BufferedWriter out = new BufferedWriter(new FileWriter("test.config"))) {
			out.write("minQuality=I");
			out.newLine();
			out.write("overhang=CAGC,CTGC");
			out.newLine();
			out.write("sourceFileForward=testForward.gz,tf2.gz");
			out.newLine();
			out.write("barcodeFile=testBarcodes.txt");
			out.newLine();
			out.write("sourceFileReverse=testBackwards.gz,tr2.gz");
			out.newLine();
			out.write("fuzzyMatch=" + false);
			out.newLine();
		}
		CopyBarcodes.main(new String[] {"test.config"});
		checkOutput(4);
	}
	
	private void createTestConfig(boolean fuzzy, boolean debug) throws Exception {
		try (BufferedWriter out = new BufferedWriter(new FileWriter("test.config"))) {
			out.write("minQuality=I");
			out.newLine();
			out.write("overhang=CAGC,CTGC");
			out.newLine();
			out.write("sourceFileForward=testForward.gz");
			out.newLine();
			out.write("barcodeFile=testBarcodes.txt");
			out.newLine();
			out.write("sourceFileReverse=testBackwards.gz");
			out.newLine();
			out.write("fuzzyMatch=" + fuzzy);
			out.newLine();
			out.write("debugOut=" + debug);
			out.newLine();
		}
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
