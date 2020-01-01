import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
	
	private static class Read {
		String[] forwardLineSet = new String[4];
		String[] reverseLineSet = new String[4];
		int barcodeLen;
		String fuzzedMatch = null;
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 4) {
			System.out.println("Usage: <path to forward file, .gz> <path to reverse file, .gz> "
					+ "<path to barcode file, text>, <path to config file>, (optional) 'fuzzy|retain' to do fuzzy matching,"
					+ " or retain to keep unmatched lines as they are."
					+ "output is stored in reverseFile.barcoded.gz");
			System.exit(-1);
		}
		long startTime = System.currentTimeMillis();
		String forwardFile = args[0];
		String reverseFile = args[1];
		String barcodeFile = args[2];
		String configFile = args[3];
		boolean fuzzyMatch = args.length > 3 && args[3].equalsIgnoreCase("fuzzy");
		boolean debug = fuzzyMatch && args.length > 4 && args[4].equalsIgnoreCase("debug");
		boolean retain = args.length > 3 && args[3].equalsIgnoreCase("retain");
		String outputFileRev = reverseFile.substring(0, reverseFile.lastIndexOf(".gz")) + ".barcoded.reverse.gz";
		String outputFileFwd = forwardFile.substring(0, forwardFile.lastIndexOf(".gz")) + ".barcoded.forward.gz";
		
		// load barcodes
		PrefixTree barcodes = new PrefixTree(!retain, Config.loadFromFile(configFile));
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
		OutputStats stats = new OutputStats();
		
		// This inner loop gets called half a billion times, so there are some
		// unusual optimizations to minimize object allocations (looping & calling charAt vs substring, for instance)
		
		// read through forward-file, extract and attach barcodes to reverse file
		try (BufferedWriter outRev = new BufferedWriter(new OutputStreamWriter(
				new GZIPOutputStream(new FileOutputStream(outputFileRev))));
				BufferedWriter outFwd = retain ? null : new BufferedWriter(new OutputStreamWriter(
						new GZIPOutputStream(new FileOutputStream(outputFileFwd))));
				BufferedWriter debugOut = debug ? new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream("debugOut.txt"))) : null;
				BufferedReader forward = new BufferedReader(new InputStreamReader(iisFwd));
				BufferedReader reverse = new BufferedReader(new InputStreamReader(iisRev));) {

			// both reading and writing to disk tends to buffer; build up enough 
			// work in the queue so that one thread can work while the other is flushing/filling the buffer
			// Generally, the writing thread takes longer than the reading thread
			int bufferSize = 100; 
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
						loadRead(forward, reverse, forwardLine, read.forwardLineSet, read.reverseLineSet);
						read.barcodeLen = barcodes.findBarcodeLen(read.forwardLineSet[1]);
						if (read.barcodeLen >= MIN_BARCODE_LEN || retain) {
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
					}
				} catch (InterruptedException | IOException e) {
					e.printStackTrace();
				}
			});
			Future<?> persist = exec.submit(() -> {
				try {
					while (true) {
						Read read = loadedReads.take();
						persistBarcodedRead(fuzzyMatch, retain, outRev, outFwd, debugOut, read);
						availableReadPool.put(read);
					}
				} catch (InterruptedException e) {
					// stop reading, this is pretty hacky, but avoids having to check a conditional constantly
					try {
						while (!loadedReads.isEmpty()) {
							Read read = loadedReads.take();
							persistBarcodedRead(fuzzyMatch, retain,outRev, outFwd, debugOut, read);
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
					+ stats.nSkippedMultipleBadReads.get() + " due to more than one character being off");
		}
	}

	private static void persistBarcodedRead(boolean fuzzyMatch, boolean retain,
			BufferedWriter outRev, BufferedWriter outFwd, BufferedWriter debugOut, Read read) throws IOException {
		// only keep properly barcoded lines
		if (read.barcodeLen >= MIN_BARCODE_LEN || retain) {
			if (!retain) {
				outFwd.write(read.forwardLineSet[0]);
				outFwd.newLine();
				outFwd.write(read.forwardLineSet[1]);
				outFwd.newLine();
				outFwd.write(read.forwardLineSet[2]);
				outFwd.newLine();
				outFwd.write(read.forwardLineSet[3]);
				outFwd.newLine();
			}
			
			outRev.write(read.reverseLineSet[0]);
			outRev.newLine();
			// write the barcode
			for (int i = 0; i < read.barcodeLen; i++) {
				outRev.write(read.forwardLineSet[1].charAt(i));
			}
			outRev.write(read.reverseLineSet[1]);
			outRev.newLine();
			outRev.write(read.reverseLineSet[2]);
			outRev.newLine();
			// write the quality for the barcode
			for (int i = 0; i < read.barcodeLen; i++) {
				outRev.write(read.forwardLineSet[3].charAt(i));
			}
			outRev.write(read.reverseLineSet[3]);
			outRev.newLine();
		} else if (read.fuzzedMatch != null) {
			outFwd.write(read.forwardLineSet[0]);
			outFwd.newLine();
			outFwd.write(read.fuzzedMatch);
			outFwd.write(read.forwardLineSet[1].substring(read.fuzzedMatch.length()));
			outFwd.newLine();
			outFwd.write(read.forwardLineSet[2]);
			outFwd.newLine();
			outFwd.write(read.forwardLineSet[3]);
			outFwd.newLine();

			outRev.write(read.reverseLineSet[0]);
			outRev.newLine();
			// write the barcode
			outRev.write(read.fuzzedMatch);
			outRev.write(read.reverseLineSet[1]);
			outRev.newLine();
			outRev.write(read.reverseLineSet[2]);
			outRev.newLine();
			// write the quality for the barcode - uncorrected
			for (int i = 0; i < read.fuzzedMatch.length(); i++) {
				outRev.write(read.forwardLineSet[3].charAt(i));
			}
			outRev.write(read.reverseLineSet[3]);
			outRev.newLine();
		} else if (debugOut != null) {
			debugOut.write(read.forwardLineSet[1]);
			debugOut.newLine();
			debugOut.write(read.forwardLineSet[3]);
			debugOut.newLine();
		}
	}

	private static void loadRead(BufferedReader forward, BufferedReader reverse, String forwardLine,
			String[] forwardLineSet, String[] reverseLineSet) throws IOException {
		// read sequence id line, barcode line, delimiter, and quality
		forwardLineSet[0] = forwardLine;
		reverseLineSet[0] = reverse.readLine();
		forwardLineSet[1] = forward.readLine();
		reverseLineSet[1] = reverse.readLine();
		forwardLineSet[2] = forward.readLine();
		reverseLineSet[2] = reverse.readLine();
		forwardLineSet[3] = forward.readLine();
		reverseLineSet[3] = reverse.readLine();
	}
}
