import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Accepts arguments in standard java.util.Properties format:
 * One per line, <argumentName>=<argumentValue>, with list values (such as overhangs) delimited by commas.
 * 
 * Required values:
 * minQuality - the minimum quality score to prevent fuzzy matching values read with this quality
 * barcodeFile - the path to the file containing barcode information
 * sourceFileForward - the path to the file containing the forward reads (or an ordered list of paths that should be treated as concatenated input)
 * sourceFileReverse - the path to the file containing the reverse reads  (or an ordered list of paths that should be treated as concatenated input)
 * overhang - a comma-separated list of overhangs for this data
 * 
 * Optional values:
 * population - required for demultiplexing, the population to use in the output file names
 * align - true|false, whether the file should be output with .F|R.fq.gz, or R1|R2.fq.gz (default false, which outputs R1|R2)
 * append - if the output files already exist, should we append to it (default is false, we overwrite instead)
 * fuzzyMatch - should the program attempt to fuzzy match barcodes (default true)
 * debugOut - should the program generate a debug output file with all the reads that failed to be parsed
 * percentToRetain - should the program downsample the input to simulate a cheaper data-gathering run
 */
public class Config {
	private final Set<String> overhangs;
	private final char minQuality;
	private final boolean align;
	private final boolean append;
	private final boolean fuzzyMatch;
	private final boolean debugOut;
	private final String barcodes;
	private final List<String> sourceFileForward;
	private final List<String> sourceFileReverse;
	private final List<String> sourceFileInterleaved; // either forward/reverse or interleaved must be specified
	private final String population;
	
	// Used to simulate the results of a run where the company that creates the fastq files produced fewer reads (more reads = more money)
	// Typically used to see if a cheaper request to the magic reading company would produce the same quality final data in terms of finding
	// interesting points of comparison on the chromosomes
	private final int percentToRetain; 
	private final boolean retainByTruncating;
	
	private final boolean printProgress;
	
	public static Config loadOptions(String[] args) throws IOException {
		Map<String, String> properties = new HashMap<>();
		
		if (args.length < 2) {
			loadFromFile(args[0], properties);
		} else {
			loadFromCommandLine(args, properties);
		}
	
		return new Config(properties.getOrDefault("minQuality", "0").charAt(0), 
				Boolean.parseBoolean(properties.getOrDefault("align", "false")),
				Boolean.parseBoolean(properties.getOrDefault("append", "false")),
				Boolean.parseBoolean(properties.getOrDefault("fuzzyMatch", "true")),
				Boolean.parseBoolean(properties.getOrDefault("debugOut", "false")),
				properties.getOrDefault("barcodeFile", ""),
				getList(properties.getOrDefault("sourceFileForward", "")),
				getList(properties.getOrDefault("sourceFileReverse", "")),
				getList(properties.getOrDefault("sourceFileInterleaved", "")),
				properties.getOrDefault("population", ""),
				properties.getOrDefault("overhang", "").split(","),
				Integer.parseInt(properties.getOrDefault("percentToRetain", "100")),
				Boolean.parseBoolean(properties.getOrDefault("printProgress", "false")),
				Boolean.parseBoolean(properties.getOrDefault("retainByTruncating", "false")));
	}

	private static void loadFromFile(String file, Map<String, String> props) throws IOException {
		List<String> allPropLines = Files.readAllLines(Paths.get(file));
		for (String arg : allPropLines) {
			String[] parsed = arg.split("=");
			props.put(parsed[0], parsed[1]);
		}		
	}

	private static void loadFromCommandLine(String[] args, Map<String, String> props) {
		for (String arg : args) {
			String[] parsed = arg.split("=");
			props.put(parsed[0], parsed[1]);
		}		
	}
	
	public static Config loadFromFile(String pathToFile) throws IOException {
		Properties properties = new Properties();
		properties.load(new FileInputStream(pathToFile));
		
		return new Config(((String)properties.getOrDefault("minQuality", "0")).charAt(0), 
				Boolean.parseBoolean((String) properties.getOrDefault("align", "false")),
				Boolean.parseBoolean((String)properties.getOrDefault("append", "false")),
				Boolean.parseBoolean((String)properties.getOrDefault("fuzzyMatch", "true")),
				Boolean.parseBoolean((String)properties.getOrDefault("debugOut", "false")),
				(String)properties.getOrDefault("barcodeFile", ""),
				getList((String)properties.getOrDefault("sourceFileForward", "")),
				getList((String)properties.getOrDefault("sourceFileReverse", "")),
				getList((String)properties.getOrDefault("sourceFileInterleaved", "")),
				(String)properties.getOrDefault("population", ""),
				((String)properties.getOrDefault("overhang", "")).split(","),
				Integer.parseInt((String)properties.getOrDefault("percentToRetain", "100")),
				Boolean.parseBoolean((String)properties.getOrDefault("printProgress", "false")),
				Boolean.parseBoolean((String)properties.getOrDefault("retainByTruncating", "false")));
	}
	
	private static List<String> getList(String list) {
		String[] split = list.split(",", 0);
		List<String> result = new ArrayList<>(split.length);
		for (String str : split) {
			result.add(str);
		}
		return result;
	}
	
	public Config(char minQuality, boolean align, boolean append, boolean fuzzyMatch, boolean debugOut, String barcodes,
			List<String> sourceFileForward, List<String> sourceFileReverse, List<String> sourceFileInterleaved, String population,
			String[] overhangs, int percentToRetain, boolean printProgress, boolean retainByTruncating) {
		this.overhangs = new HashSet<>();
		for (String overhang : overhangs) {
			this.overhangs.add(overhang);
		}
		this.minQuality = minQuality;
		this.align = align;
		this.append = append;
		this.fuzzyMatch = fuzzyMatch;
		this.debugOut = debugOut;
		this.barcodes = barcodes;
		this.sourceFileForward = sourceFileForward;
		this.sourceFileReverse = sourceFileReverse;
		this.sourceFileInterleaved = sourceFileInterleaved;
		this.population = population;
		this.percentToRetain = percentToRetain;
		this.printProgress = printProgress;
		this.retainByTruncating = retainByTruncating;
	}

	public Set<String> getOverhangs() {
		return overhangs;
	}

	public char getMinQuality() {
		return minQuality;
	}

	public boolean isAlign() {
		return align;
	}

	public boolean isAppend() {
		return append;
	}

	public boolean isFuzzyMatch() {
		return fuzzyMatch;
	}

	public boolean isDebugOut() {
		return debugOut;
	}

	public String getBarcodes() {
		return barcodes;
	}

	public List<String> getSourceFileForward() {
		return sourceFileForward;
	}

	public List<String> getSourceFileReverse() {
		return sourceFileReverse;
	}
	
	public List<String> getSourceFileInterleaved() {
		return sourceFileInterleaved;
	}

	public String getPopulation() {
		return population;
	}
	
	public int getPercentToRetain() {
		return percentToRetain;
	}
	
	public boolean getPrintProgress() {
		return printProgress;
	}
	
	public boolean retainByTruncating() {
		return retainByTruncating;
	}
	
	@Override
	public String toString() {
		return "Config [overhangs=" + overhangs + ", minQuality=" + minQuality + ", align=" + align + ", append="
				+ append + ", fuzzyMatch=" + fuzzyMatch + ", debugOut=" + debugOut + ", barcodes=" + barcodes
				+ ", sourceFileForward=" + sourceFileForward + ", sourceFileReverse=" + sourceFileReverse
				+ ", sourceFileInterleaved=" + sourceFileInterleaved + ", population=" + population + ", percentToRetain=" + percentToRetain + ", printProgress=" + printProgress
				+ ", retainByTruncating=" + retainByTruncating + "]";
	}
}
