import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

public class CopyBarcodes {

	private static final int MIN_BARCODE_LEN = 4;
	private static final int MAX_LINE_LEN = 400;
	
	private static class Read {
		char[][] forwardLineSet = new char[4][MAX_LINE_LEN];
		char[][] reverseLineSet = new char[4][MAX_LINE_LEN];
		int[] lineLens = new int[8];
		int barcodeLen;
		String fuzzedMatch = null;
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 4) {
			System.out.println("Usage: <path to forward file, .gz> <path to reverse file, .gz> "
					+ "<path to barcode file, text>, <path to config file>, (optional) 'fuzzy' to do fuzzy matching,"
					+ " (optional) 'debug' to print debug information about unfuzzable lines."
					+ "output is stored in <forwardFile>.interleaved.barcoded.gz");
			System.exit(-1);
		}
		
		String forwardFile = args[0];
		String reverseFile = args[1];
		String barcodeFile = args[2];
		String configFile = args[3];
		boolean fuzzyMatch = args.length > 4 && args[4].equalsIgnoreCase("fuzzy");
		boolean debug = fuzzyMatch && args.length > 5 && args[5].equalsIgnoreCase("debug");
		String outputFile = forwardFile.substring(0, forwardFile.lastIndexOf(".gz")) + ".interleaved.barcoded.gz";
		
		// load barcodes
		PrefixTree barcodes = new PrefixTree(Config.loadFromFile(configFile));
		String line = "";
		InflaterInputStream iisFwd = new GZIPInputStream(
				new FileInputStream(forwardFile));
		InflaterInputStream iisRev = new GZIPInputStream(
				new FileInputStream(reverseFile));
		
		Set<String> barcodeSet = new HashSet<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(barcodeFile))) {
			while ((line = reader.readLine()) != null) {
				int index = line.indexOf("\t");
				if (index > 0) {
					barcodes.addBarcode(line.substring(0, index));
					barcodeSet.add(line.substring(0, index));
				}
			}
		}
		int minEditDistance = getMinEditDistance(barcodeSet);
		System.out.println("Min edit distance: " + minEditDistance);
		if (minEditDistance <= 2) {
			System.out.println("There is a risk of mis-fuzzing because the barcodes are too similar");
		}
		OutputStats stats = new OutputStats();
		long startTime = System.currentTimeMillis();
		
		// This inner loop gets called half a billion times, so there are some
		// unusual optimizations to minimize object allocations (looping & calling charAt vs substring, for instance)
		
		// read through forward-file, extract and attach barcodes to reverse file
		try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
				new GZIPOutputStream(new FileOutputStream(outputFile))));
				BufferedWriter debugOut = debug ? new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream("debugOut.txt"))) : null;
				ReusingBufferedReader forward = new ReusingBufferedReader(new InputStreamReader(iisFwd));
				ReusingBufferedReader reverse = new ReusingBufferedReader(new InputStreamReader(iisRev));) {

			// both reading and writing to disk tends to buffer; build up enough 
			// work in the queue so that one thread can work while the other is flushing/filling the buffer
			// Generally, the writing thread takes longer than the reading thread
			int bufferSize = 150; 
			ArrayBlockingQueue<Read> availableReadPool = new ArrayBlockingQueue<>(bufferSize);
			ArrayBlockingQueue<Read> loadedReads = new ArrayBlockingQueue<>(bufferSize);
			for (int i = 0; i < bufferSize; i++) {
				availableReadPool.add(new Read());
			}
			CountDownLatch persistFinished = new CountDownLatch(1);
			ExecutorService exec = Executors.newFixedThreadPool(2);
			Future<?> load = exec.submit(() -> {
				try {
					String forwardLine = "";
					while ((forwardLine = forward.readLine()) != null) {
						Read read = availableReadPool.take();
						if (loadRead(forward, reverse, forwardLine, read)) {
							read.barcodeLen = barcodes.findBarcodeLen(read.forwardLineSet[1]);
							if (read.barcodeLen >= MIN_BARCODE_LEN) {
								stats.nWritten.getAndIncrement();
							} else if (fuzzyMatch) {
								String fuzzedMatch = barcodes.fuzzyMatch(read.forwardLineSet[1],
										read.forwardLineSet[3], debug ? stats : null);
								if (fuzzedMatch.length() >= MIN_BARCODE_LEN) {
									stats.nFuzzed.getAndIncrement();
									read.fuzzedMatch = fuzzedMatch;
								} else {
									read.fuzzedMatch = null; // clear from possible previous run
									stats.nSkipped.getAndIncrement();
								}
							} else {
								stats.nSkipped.getAndIncrement();
							}
							loadedReads.put(read);
						} else {
							read.barcodeLen = 0;
							read.fuzzedMatch = null;
							stats.nSkipped.getAndIncrement();
							stats.nSkippedHeader.getAndIncrement();
						}
					}
				} catch (InterruptedException | IOException e) {
					e.printStackTrace();
				}
			});
			Future<?> persist = exec.submit(() -> {
				try {
					while (true) {
						Read read = loadedReads.take();
						persistBarcodedRead(fuzzyMatch, out, debugOut, read);
						availableReadPool.put(read);
					}
				} catch (InterruptedException e) {
					// stop reading, this is pretty hacky, but avoids having to check a conditional constantly
					try {
						while (!loadedReads.isEmpty()) {
							Read read = loadedReads.take();
							persistBarcodedRead(fuzzyMatch, out, debugOut, read);
						}
					} catch (InterruptedException | IOException e1) {
						e1.printStackTrace();
					} finally {
						persistFinished.countDown();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			
			// first .get call will return once the input files have been fully read.
			// Interrupt the persistence thread to tell it no more work is getting added to the queue
			// await the countdown latch to see when the outstanding work queue has been fully persisted
			// (we can't just call .get again, because that will immediately return and say the future was cancelled)
			load.get();
			
			// for test purposes, make sure the second thread has started before cancelling it
			if (System.currentTimeMillis() - startTime < 1000) {
				Thread.sleep(1000);
			}
			
			persist.cancel(true);
			persistFinished.await();
			exec.shutdown();
		}
		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		String timeStr;
		if (duration > 60000) {
			timeStr = duration / (60 * 1000) + " minutes";
		} else {
			timeStr = duration + " ms";
		}
		System.out.println("Finished, wrote " + stats.nWritten.get() + ", skipped " 
				+ stats.nSkipped.get() + ", fuzzed " + stats.nFuzzed.get() + " in " + timeStr);
		if (debug) {
			System.out.println("Skipped " + stats.nSkippedDuplicate.get() + " due to non-unique fixes, " 
					+ stats.nSkippedQuality.get() + " due to quality scores, and " 
					+ stats.nSkippedMultipleBadReads.get() + " due to more than one character being off, and "
					+ stats.nSkippedHeader.get() + " due to a mismatched header");
		}
	}

	// edit distance not allowing for adds/deletions (only allowing for edits that are made during fuzzing)
	public static int getMinEditDistance(Set<String> barcodeSet) {
		int minDistance = Integer.MAX_VALUE;
		for (String s1 : barcodeSet) {
			for (String s2 : barcodeSet) {
				if (s1 != s2 // yup, we want this kind of equals comparison
						&& s1.length() == s2.length()) {
					int editDistance = 0;
					for (int i = 0; i < s1.length(); i++) {
						if (s1.charAt(i) != s2.charAt(i)) {
							editDistance++;
						}
					}
					minDistance = Math.min(minDistance, editDistance);
				}
			}
		}
		return minDistance;
	}

	private static void persistBarcodedRead(boolean fuzzyMatch,
			BufferedWriter out, BufferedWriter debugOut, Read read) throws IOException {
		// only keep properly barcoded lines
		if (read.barcodeLen >= MIN_BARCODE_LEN) {
			out.write(read.forwardLineSet[0], 0, read.lineLens[0]);
			out.newLine();
			out.write(read.forwardLineSet[1], 0, read.lineLens[1]);
			out.newLine();
			out.write(read.forwardLineSet[2], 0, read.lineLens[2]);
			out.newLine();
			out.write(read.forwardLineSet[3], 0, read.lineLens[3]);
			out.newLine();
			
			out.write(read.reverseLineSet[0], 0, read.lineLens[4]);
			out.newLine();
			// write the barcode
			for (int i = 0; i < read.barcodeLen; i++) {
				out.write(read.forwardLineSet[1][i]);
			}
			out.write(read.reverseLineSet[1], 0, read.lineLens[5]);
			out.newLine();
			out.write(read.reverseLineSet[2], 0, read.lineLens[6]);
			out.newLine();
			// write the quality for the barcode
			for (int i = 0; i < read.barcodeLen; i++) {
				out.write(read.forwardLineSet[3][i]);
			}
			out.write(read.reverseLineSet[3], 0, read.lineLens[7]);
			out.newLine();
		} else if (read.fuzzedMatch != null) {
			out.write(read.forwardLineSet[0], 0, read.lineLens[0]);
			out.newLine();
			out.write(read.fuzzedMatch);
			out.write(read.forwardLineSet[1], read.fuzzedMatch.length(), read.lineLens[1] - read.fuzzedMatch.length());
			out.newLine();
			out.write(read.forwardLineSet[2], 0, read.lineLens[2]);
			out.newLine();
			out.write(read.forwardLineSet[3], 0, read.lineLens[3]);
			out.newLine();

			out.write(read.reverseLineSet[0], 0, read.lineLens[4]);
			out.newLine();
			// write the barcode
			out.write(read.fuzzedMatch);
			out.write(read.reverseLineSet[1], 0, read.lineLens[5]);
			out.newLine();
			out.write(read.reverseLineSet[2], 0, read.lineLens[6]);
			out.newLine();
			// write the quality for the barcode - uncorrected
			for (int i = 0; i < read.fuzzedMatch.length(); i++) {
				out.write(read.forwardLineSet[3][i]);
			}
			out.write(read.reverseLineSet[3], 0, read.lineLens[7]);
			out.newLine();
		} else if (debugOut != null) {
			debugOut.write(read.forwardLineSet[1], 0, read.lineLens[1]);
			debugOut.newLine();
			debugOut.write(read.forwardLineSet[3], 0, read.lineLens[3]);
			debugOut.newLine();
		}
	}

	private static boolean loadRead(ReusingBufferedReader forward, ReusingBufferedReader reverse, String forwardLine,
			Read read) throws IOException {
		// read sequence id line, barcode line, delimiter, and quality
		read.forwardLineSet[0] = forwardLine.toCharArray();
		read.lineLens[0] = forwardLine.length();
		read.lineLens[1] = forward.readLine(read.forwardLineSet[1]);
		read.lineLens[2] = forward.readLine(read.forwardLineSet[2]);
		read.lineLens[3] = forward.readLine(read.forwardLineSet[3]);
		read.lineLens[4] = reverse.readLine(read.reverseLineSet[0]);
		read.lineLens[5] = reverse.readLine(read.reverseLineSet[1]);
		read.lineLens[6] = reverse.readLine(read.reverseLineSet[2]);
		read.lineLens[7] = reverse.readLine(read.reverseLineSet[3]);
		
		return checkHeaders(read);
	}

	// verify the headers match on x & y
	private static boolean checkHeaders(Read read) {
		int posFwd = 0;
		int nSplitsFound = 0;
		while (posFwd < read.lineLens[0] && nSplitsFound < 5) {
			if (read.forwardLineSet[0][posFwd] == ':') {
				nSplitsFound++;
			}
			posFwd++;
		}
		if (nSplitsFound < 5) {
			return false;
		}
		int posRev = 0;
		nSplitsFound = 0;
		while (posRev < read.lineLens[4] && nSplitsFound < 5) {
			if (read.reverseLineSet[0][posRev] == ':') {
				nSplitsFound++;
			}
			posRev++;
		}
		if (nSplitsFound < 5) {
			return false;
		}
		while (posFwd < read.lineLens[0] && posRev < read.lineLens[4] && nSplitsFound < 7) {
			if (read.forwardLineSet[0][posFwd] != read.reverseLineSet[0][posRev]) {
				return false;
			}
			if (read.reverseLineSet[0][posRev] == ':') {
				nSplitsFound++;
			}
			posFwd++;
			posRev++;
		}
		return true;
	}
}
