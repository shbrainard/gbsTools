import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

public class CopyBarcodes {

	private static final int MIN_BARCODE_LEN = 4;

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.out.println("Usage: <path to forward file, .gz> <path to reverse file, .gz> "
					+ "<path to barcode file, text>, (optional) 'fuzzy|retain' to do fuzzy matching,"
					+ " or retain to keep unmatched lines as they are."
					+ "output is stored in reverseFile.barcoded.gz");
			System.exit(-1);
		}
		long startTime = System.currentTimeMillis();
		String forwardFile = args[0];
		String reverseFile = args[1];
		String barcodeFile = args[2];
		boolean fuzzyMatch = args.length > 3 && args[3].equalsIgnoreCase("fuzzy");
		boolean retain = args.length > 3 && args[3].equalsIgnoreCase("retain");
		String outputFileRev = reverseFile.substring(0, reverseFile.lastIndexOf(".gz")) + ".barcoded.reverse.gz";
		String outputFileFwd = forwardFile.substring(0, forwardFile.lastIndexOf(".gz")) + ".barcoded.forward.gz";
		
		// load barcodes
		HuffmanTree barcodes = new HuffmanTree(!retain);
		String line = "";
		InflaterInputStream iisFwd = new GZIPInputStream(
				new FileInputStream(forwardFile));
		InflaterInputStream iisRev = new GZIPInputStream(
				new FileInputStream(reverseFile));
		
		try (BufferedReader reader = new BufferedReader(new FileReader(barcodeFile))) {
			while ((line = reader.readLine()) != null) {
				int index = line.indexOf("\t");
				if (index > 0) {
					barcodes.addBarcode(line.substring(0, index));
				}
			}
		}
		long nSkipped = 0, nLines = 0, nFuzzed = 0;
		
		// This inner loop gets called half a billion times, so there are some
		// unusual optimizations to minimize object allocations (looping & calling charAt vs substring, for instance)
		
		// read through forward-file, extract and attach barcodes to reverse file
		try (BufferedWriter outRev = new BufferedWriter(new OutputStreamWriter(
				new GZIPOutputStream(new FileOutputStream(outputFileRev))));
				BufferedWriter outFwd = retain ? null : new BufferedWriter(new OutputStreamWriter(
						new GZIPOutputStream(new FileOutputStream(outputFileFwd))));
				BufferedReader forward = new BufferedReader(new InputStreamReader(iisFwd));
				BufferedReader reverse = new BufferedReader(new InputStreamReader(iisRev));) {

			String forwardLine = "";
			String[] forwardLineSet = new String[4];
			String[] reverseLineSet = new String[4];
			
			while ((forwardLine = forward.readLine()) != null) {
				// read sequence id line, barcode line, delimiter, and quality
				forwardLineSet[0] = forwardLine;
				reverseLineSet[0] = reverse.readLine();
				forwardLineSet[1] = forward.readLine();
				reverseLineSet[1] = reverse.readLine();
				forwardLineSet[2] = forward.readLine();
				reverseLineSet[2] = reverse.readLine();
				forwardLineSet[3] = forward.readLine();
				reverseLineSet[3] = reverse.readLine();

				// search for the barcode
				int barcodeLen = barcodes.findBarcodeLen(forwardLineSet[1]);
				
				// only keep properly barcoded lines
				if (barcodeLen >= MIN_BARCODE_LEN || retain) {
					nLines++;
					if (!retain) {
						outFwd.write(forwardLineSet[0]);
						outFwd.newLine();
						outFwd.write(forwardLineSet[1]);
						outFwd.newLine();
						outFwd.write(forwardLineSet[2]);
						outFwd.newLine();
						outFwd.write(forwardLineSet[3]);
						outFwd.newLine();
					}
					
					outRev.write(reverseLineSet[0]);
					outRev.newLine();
					// write the barcode
					for (int i = 0; i < barcodeLen; i++) {
						outRev.write(forwardLineSet[1].charAt(i));
					}
					outRev.write(reverseLineSet[1]);
					outRev.newLine();
					outRev.write(reverseLineSet[2]);
					outRev.newLine();
					// write the quality for the barcode
					for (int i = 0; i < barcodeLen; i++) {
						outRev.write(forwardLineSet[3].charAt(i));
					}
					outRev.write(reverseLineSet[3]);
					outRev.newLine();
				} else {
					if (fuzzyMatch) {
						String fuzzedMatch = barcodes.fuzzyMatch(forwardLineSet[1],
								forwardLineSet[3]);
						if (fuzzedMatch.length() >= MIN_BARCODE_LEN) {
							nFuzzed++;
							outFwd.write(forwardLineSet[0]);
							outFwd.newLine();
							outFwd.write(fuzzedMatch);
							outFwd.write(forwardLineSet[1].substring(fuzzedMatch.length()));
							outFwd.newLine();
							outFwd.write(forwardLineSet[2]);
							outFwd.newLine();
							outFwd.write(forwardLineSet[3]);
							outFwd.newLine();
							
							outRev.write(reverseLineSet[0]);
							outRev.newLine();
							// write the barcode
							outRev.write(fuzzedMatch);
							outRev.write(reverseLineSet[1]);
							outRev.newLine();
							outRev.write(reverseLineSet[2]);
							outRev.newLine();
							// write the quality for the barcode - uncorrected
							for (int i = 0; i < barcodeLen; i++) {
								outRev.write(forwardLineSet[3].charAt(i));
							}
							outRev.write(reverseLineSet[3]);
							outRev.newLine();
						}
					} else {
						nSkipped++;
					}
				}
			}
		}
		long endTime = System.currentTimeMillis();
		System.out.println("Finished, wrote " + nLines + ", skipped " 
				+ nSkipped + ", fuzzed " + nFuzzed + " in " + (endTime - startTime) / (60 * 1000) + " minutes");
	}
}
