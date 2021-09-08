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

		String forwardFile = config.getSourceFileForward();
		String reverseFile = config.getSourceFileReverse();
		
		RetainBehavior retainBehavior = RetainBehaviors.getRetainBehavior(config.getPercentToRetain(), 
				new File(forwardFile).length() / 1024 * ByteBasedProgressTracker.GZIP_READ_PER_KB, config.retainByTruncating());

		truncateFile(forwardFile, retainBehavior);
		truncateFile(reverseFile, retainBehavior);
		System.out.println("Output stored in truncated_" + forwardFile + " and truncated_" + reverseFile);
	}
	
	private static void truncateFile(String fileName, RetainBehavior retainBehavior) throws Exception {
		
		try (ReusingBufferedReader in = new ReusingBufferedReader(new InputStreamReader(new GZIPInputStream(
				new FileInputStream(fileName))));
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream("truncated_" + fileName))))) {
			String forwardLine = "";
			while ((forwardLine = in.readLine()) != null) {
				if (!retainBehavior.keepRead()) {
					for (int i = 0; i < 3; i++) {
						in.readLine();
					}
				} else {
					out.write(forwardLine);
					out.newLine();
					for (int i = 0; i < 3; i++) {
						out.write(in.readLine());
						out.newLine();
					}
				}
			}
		}
	}
}
