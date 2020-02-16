import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;

public class DemultiplexerTest {

	@Test
	public void demultiplexTest() throws Exception {
		setUpTestFiles();
		// clear old output now that we don't overwrite
		new File("pop_foo.R1.fq.gz").delete();
		new File("pop_foo.R2.fq.gz").delete();
		new File("pop_foo.F.fq.gz").delete();
		new File("pop_foo.R.fq.gz").delete();
		new File("pop_bar.R1.fq.gz").delete();
		new File("pop_bar.R2.fq.gz").delete();
		new File("pop_bar.F.fq.gz").delete();
		new File("pop_bar.R.fq.gz").delete();
		
		Demultiplexer.main(new String[] {"pop", "testForward.gz", "testBackwards.gz", "testBarcodes.txt", "default.config"});
		checkOutput(2, "foo", ".R1.fq.gz");
		checkOutput(1, "bar", ".R2.fq.gz");
		Demultiplexer.main(new String[] {"pop", "testForward.gz", "testBackwards.gz", "testBarcodes.txt", "default.config", "-alignFile"});
		checkOutput(2, "foo", ".F.fq.gz");
		checkOutput(1, "bar", ".R.fq.gz");
	}

	private void checkOutput(int numExpected, String sample, String suffix) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new GZIPInputStream(new FileInputStream("pop_" + sample + suffix))));
		int nReads = 0;
		while (reader.readLine() != null) {
			nReads++;
			reader.readLine();
			reader.readLine();
			reader.readLine();
		}
		assert nReads == numExpected;
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

		// write a fuzzable read
		out.write(header.getBytes());
		out.write("AAAACCGCAAACCCGGGTTTAAA\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF,FFFFFFFFFFFFFFFFF\n".getBytes());

		// write a good read
		out.write(header.getBytes());
		out.write("CCCCCAGCAAACCCGGGTTTAAA\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFFFFFFFFFFFFFFFFFFFF\n".getBytes());

		out.close();

		// write matching reverse reads
		out = new GZIPOutputStream(new FileOutputStream("testBackwards.gz"));
		out.write(header.getBytes());
		out.write("CCCCC\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF\n".getBytes());
		
		out.write(header.getBytes());
		out.write("GGGGG\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF\n".getBytes());

		out.write(header.getBytes());
		out.write("GGGGG\n".getBytes());
		out.write("+\n".getBytes());
		out.write("FFFFF\n".getBytes());
		out.close();

		FileOutputStream barcodeOut = new FileOutputStream("testBarcodes.txt");
		barcodeOut.write(("AAAA" + "\tfoo\n").getBytes());
		barcodeOut.write(("CCCC" + "\tbar\tstuff\n").getBytes());
		barcodeOut.close();
	}
}
