import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Downsampler {
	
	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			System.out.println("Usage: <path to config file>.");
			System.exit(-1);
		}
		Config config = Config.loadFromFile(args[0]);
		
		if (config.getSourceFileForward().size() == 0) {
			if (config.getSourceFileInterleaved().size() != 1) {
				throw new IllegalArgumentException("Downsampling does not support multiple input files at once");
			}
			String sourceFileInterleaved = config.getSourceFileInterleaved().get(0);
			RetainBehavior retainBehavior = RetainBehaviors.getRetainBehavior(config.getPercentToRetain(), 
					new File(sourceFileInterleaved).length() / 1024 * ByteBasedProgressTracker.GZIP_READ_PER_KB / 2, config.retainByTruncating());
			String result = truncateFile(sourceFileInterleaved, 8, retainBehavior);
			System.out.println("Output stored in " + result);
		} else {
			if (config.getSourceFileForward().size() != 1) {
				throw new IllegalArgumentException("Downsampling does not support multiple input files at once");
			}
			String forwardFile = config.getSourceFileForward().get(0);
			String reverseFile = config.getSourceFileReverse().get(0);
			
			RetainBehavior retainBehavior = RetainBehaviors.getRetainBehavior(config.getPercentToRetain(), 
					new File(forwardFile).length() / 1024 * ByteBasedProgressTracker.GZIP_READ_PER_KB, config.retainByTruncating());
			String result1 = truncateFile(forwardFile, 4, retainBehavior);
			String result2 = truncateFile(reverseFile, 4, retainBehavior);
			System.out.println("Output stored in " + result1 + " and " + result2);
		}
	}
	
	private static String truncateFile(String fileName, int rowsPerRead, RetainBehavior retainBehavior) throws Exception {
		File inputFile = new File(fileName);
		File outputFile = new File(inputFile.getParent(), "truncated_" + inputFile.getName());
		
		try (ReusingBufferedReader in = new ReusingBufferedReader(new InputStreamReader(new GZIPInputStream(
				new FileInputStream(inputFile))));
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(
						new FileOutputStream(outputFile))))) {
			String forwardLine = "";
			while ((forwardLine = in.readLine()) != null) {
				if (!retainBehavior.keepRead()) {
					for (int i = 0; i < rowsPerRead - 1; i++) {
						in.readLine();
					}
				} else {
					out.write(forwardLine);
					out.newLine();
					for (int i = 0; i < rowsPerRead - 1; i++) {
						out.write(in.readLine());
						out.newLine();
					}
				}
			}
		}
		return outputFile.getCanonicalPath();
	}
}
