import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Usage:
 * file=<path to file to truncate>
 * directory=<path to directory, where all files should be truncated>
 * max_read_length=<maximum length of the read to keep>
 * barcode_length=<length of the barcode prepended to the reads>
 * 
 * All argument must be specific, except for file and directory - exactly one of those must be specified
 * 
 * This assumes that each read is in the format
 * <header line>
 * <read with barcode>
 * <metadata line>
 * <quality scores>
 * 
 * and the reads and quality scores will be truncated. The output is stored in <filename>.truncated.gz
 */
public class TruncateReadsConfig {
	
	private final String file;
	private final String directory;
	private final int readLength;

	public TruncateReadsConfig(String file, String directory, int readLength) {
		this.file = file;
		this.directory = directory;
		this.readLength = readLength;
		if (!(file.length() > 0 ^ directory.length() > 0)) {
			throw new IllegalArgumentException("Exactly one of file, directory must be specified");
		}
	}

	public static TruncateReadsConfig loadOptions(String[] args) {
		Map<String, String> properties = new HashMap<>();

		if (args.length < 2) {
			try {
				loadFromFile(args[0], properties);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			loadFromCommandLine(args, properties);
		}

		return new TruncateReadsConfig(properties.getOrDefault("file", ""), 
				properties.getOrDefault("directory", ""),
				Integer.parseInt(properties.getOrDefault("max_read_length", "0")) + Integer.parseInt(properties.getOrDefault("barcode_length",  "0")));
	}
	
	private static void loadFromFile(String file, Map<String, String> props) throws IOException {
		List<String> allPropLines = Files.readAllLines(Paths.get(file));
		for (String arg : allPropLines) {
			String[] parsed = arg.split("=");
			props.put(parsed[0].toLowerCase(), parsed.length > 1 ? parsed[1] : "");
		}		
	}

	private static void loadFromCommandLine(String[] args, Map<String, String> props) {
		for (String arg : args) {
			String[] parsed = arg.split("=");
			props.put(parsed[0].toLowerCase(), parsed[1]);
		}		
	}

	public List<File> getFiles() {
		List<File> result = new ArrayList<>();
		if (file.length() > 0) {
			result.add(new File(file));
		} else {
			File dir = new File(directory);
			for (File f : dir.listFiles()) {
				result.add(f);
			}
		}
		return result;
	}

	public int getReadLength() {
		return readLength;
	}

}
