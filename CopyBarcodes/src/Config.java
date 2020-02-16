import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Accepts arguments in standard java.util.Properties format:
 * One per line, <argumentName>=<argumentValue>, with list values (such as overhangs) delimited by commas.
 * 
 * Required values:
 * minQuality - the minimum quality score to prevent fuzzy matching values read with this quality
 * barcodeFile - the path to the file containing barcode information
 * sourceFileForward - the path to the file containing the forward reads
 * sourceFileReverse - the path to the file containing the reverse reads
 * overhang - a comma-separated list of overhangs for this data
 * 
 * Optional values:
 * population - required for demultiplexing, the population to use in the output file names
 * align - true|false, whether the file should be output with .F|R.fq.gz, or R1|R2.fq.gz (default false, which outputs R1|R2)
 * append - if the output files already exist, should we append to it (default is false, we overwrite instead)
 * fuzzyMatch - should the program attempt to fuzzy match barcodes (default true)
 * debugOut - should the program generate a debug output file with all the reads that failed to be parsed
 */
public class Config {
	
	private final Set<String> overhangs;
	private final char minQuality;
	private final boolean align;
	private final boolean append;
	private final boolean fuzzyMatch;
	private final boolean debugOut;
	private final String barcodes;
	private final String sourceFileForward;
	private final String sourceFileReverse;
	private final String population;
	
	public static Config loadFromFile(String pathToFile) throws IOException {
		Properties properties = new Properties();
		properties.load(new FileInputStream(pathToFile));
		
		return new Config(((String)properties.getOrDefault("minQuality", "0")).charAt(0), 
				Boolean.parseBoolean((String) properties.getOrDefault("align", "false")),
				Boolean.parseBoolean((String)properties.getOrDefault("append", "false")),
				Boolean.parseBoolean((String)properties.getOrDefault("fuzzyMatch", "true")),
				Boolean.parseBoolean((String)properties.getOrDefault("debugOut", "false")),
				(String)properties.getOrDefault("barcodeFile", ""),
				(String)properties.getOrDefault("sourceFileForward", ""),
				(String)properties.getOrDefault("sourceFileReverse", ""),
				(String)properties.getOrDefault("population", ""),
				((String)properties.getOrDefault("overhang", "")).split(","));
	}
	


	public Config(char minQuality, boolean align, boolean append, boolean fuzzyMatch, boolean debugOut, String barcodes,
			String sourceFileForward, String sourceFileReverse, String population,
			String[] overhangs) {
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
		this.population = population;
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

	public String getSourceFileForward() {
		return sourceFileForward;
	}

	public String getSourceFileReverse() {
		return sourceFileReverse;
	}

	public String getPopulation() {
		return population;
	}

	@Override
	public String toString() {
		return "Config [overhangs=" + overhangs + ", minQuality=" + minQuality + ", align=" + align + ", append="
				+ append + ", fuzzyMatch=" + fuzzyMatch + ", debugOut=" + debugOut + ", barcodes=" + barcodes
				+ ", sourceFileForward=" + sourceFileForward + ", sourceFileReverse=" + sourceFileReverse
				+ ", population=" + population + "]";
	}
}
