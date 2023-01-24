import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class TruncateReads {

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("Usage: <path to config file> OR specify all flags on command line."
					+ "output is stored in <filename>.truncated.gz");
			System.exit(-1);
		}
		
		TruncateReadsConfig config = TruncateReadsConfig.loadOptions(args);
		for (File f : config.getFiles()) {
			truncateRead(f, config.getReadLength());
		}
		
		System.out.println("Truncated " + config.getFiles().size() + " files.");
	}

	private static void truncateRead(File f, int maxReadLength) throws IOException {
		String outFile = f.getCanonicalPath().substring(0, f.getCanonicalPath().lastIndexOf(".")) + ".truncated.gz";
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(f))));
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(outFile))))) {
			String line;
			int i = 0;
			while ((line = in.readLine()) != null) {
				if (i % 2 == 1 && line.length() > maxReadLength) {
					line = line.substring(0, maxReadLength);
				}
				out.write(line);
				out.newLine();
				i++;
				i = i % 4;
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
