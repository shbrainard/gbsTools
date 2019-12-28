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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

public class CopyBarcodes {

	private static final int MIN_BARCODE_LEN = 4;
	
	private static class OutputStats {
		final AtomicInteger nWritten = new AtomicInteger(0);
		final AtomicInteger nFuzzed = new AtomicInteger(0);
		final AtomicInteger nSkipped = new AtomicInteger(0);
	}
	
	private static class Read {
		String[] forwardLineSet = new String[4];
		String[] reverseLineSet = new String[4];
		int barcodeLen;
	}

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
		PrefixTree barcodes = new PrefixTree(!retain);
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
						persistBarcodedRead(fuzzyMatch, retain, barcodes, stats, outRev, outFwd, read.forwardLineSet, read.reverseLineSet,
								read.barcodeLen);
						availableReadPool.put(read);
					}
				} catch (InterruptedException e) {
					// stop reading, this is pretty hacky, but avoids having to check a conditional constantly
					try {
						while (!loadedReads.isEmpty()) {
							Read read = loadedReads.take();
							int barcodeLen = barcodes.findBarcodeLen(read.forwardLineSet[1]);
							persistBarcodedRead(fuzzyMatch, retain, barcodes, stats, outRev, outFwd, read.forwardLineSet, read.reverseLineSet,
									barcodeLen);
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
			persist.cancel(true);
			persistFinished.await();
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
	}

	private static void persistBarcodedRead(boolean fuzzyMatch, boolean retain, PrefixTree barcodes, OutputStats stats,
			BufferedWriter outRev, BufferedWriter outFwd, String[] forwardLineSet, String[] reverseLineSet,
			int barcodeLen) throws IOException {
		// only keep properly barcoded lines
		if (barcodeLen >= MIN_BARCODE_LEN || retain) {
			stats.nWritten.getAndIncrement();
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
					stats.nFuzzed.getAndIncrement();
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
				stats.nSkipped.getAndIncrement();
			}
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
