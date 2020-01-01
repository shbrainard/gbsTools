import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class Config {
	
	private final Set<String> overhangs = new HashSet<>();
	private final char minQuality;

	public static Config loadFromFile(String pathToFile) throws IOException {
		try (BufferedReader reader = new BufferedReader(new FileReader(pathToFile))) {
			String line;
			char minQuality = 0;
			Set<String> overhangs = new HashSet<>();
			while ((line = reader.readLine()) != null) {
				String[] parsed = line.split("=");
				if (parsed[0].equals("minQuality")) {
					minQuality = parsed[1].charAt(0);
				} else {
					String[] rawOverhangs = parsed[1].split(",");
					int len = rawOverhangs[0].length();
					for (String overhang : rawOverhangs) {
						if (overhang.length() != len) {
							throw new IllegalStateException("All overhangs must be the same length");
						}
						overhangs.add(overhang);
					}
				}
			}
			return new Config(overhangs, minQuality);
		}
	}
	
	private Config(Set<String> overhangs, char minQuality) {
		this.overhangs.addAll(overhangs);
		this.minQuality = minQuality;
	}

	public Set<String> getOverhangs() {
		return overhangs;
	}

	public char getMinQuality() {
		return minQuality;
	}
}
