import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Demultiplexer {
	
	private static final int MIN_BARCODE_LEN = 4;
	static final int MAX_LINE_LEN = 400;
	static final int MAX_NUM_PERSIST_THREADS = 5; // this is sufficient to fully saturate i/o

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			System.out.println("Usage: <path to config file>. Barcode file specified in the config file should be tab-separated,"
					+ " with the first two entries being <barcode>\t<sampleName>");
			System.exit(-1);
		}
		Config config = Config.loadFromFile(args[0]);
		
		String popName = config.getPopulation();
		List<String> forwardFile = config.getSourceFileForward();
		List<String> reverseFile = config.getSourceFileReverse();
		String barcodeFile = config.getBarcodes();
		boolean alignmentFile = config.isAlign();

		// load barcodes
		PrefixTree barcodes = new PrefixTree(config);
		InputStream iisFwd = MultiFileInputStream.getStream(forwardFile); 
		InputStream iisRev = MultiFileInputStream.getStream(reverseFile);
		
		long approxLen = forwardFile.size() * new File(forwardFile.get(0)).length();
		ProgressTracker tracker = config.getPrintProgress() ? new ByteBasedProgressTracker(approxLen) 
				: new NoOpProgressTracker();
		RetainBehavior retainBehavior = RetainBehaviors.getRetainBehavior(config.getPercentToRetain(), 
				approxLen / 1024 * ByteBasedProgressTracker.GZIP_READ_PER_KB, config.retainByTruncating());
		
		ExecutorService progressThread = Executors.newSingleThreadExecutor();
		Future<?> progressPrinter = progressThread.submit(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				tracker.printProgress();
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		});
		
		Map<String, String> barcodeToSample = new HashMap<>();
		Map<String, OutputFile> barcodeToOutputFile = new HashMap<>();
		Set<String> barcodeSet = CopyBarcodes.loadBarcodeFile(barcodeFile, barcodes, barcodeToSample);
		barcodeToSample.forEach((barcode, sample) -> {
			try {
				barcodeToOutputFile.put(barcode, new OutputFile(popName, sample, alignmentFile, config.isAppend()));
			} catch (IOException e2) {
				e2.printStackTrace();
			}
		});
		int minEditDistance = CopyBarcodes.getMinEditDistance(barcodeSet);
		System.out.println("Min edit distance: " + minEditDistance);
		if (minEditDistance <= 2) {
			System.out.println("There is a risk of mis-fuzzing because the barcodes are too similar");
		}
		OutputStats stats = new OutputStats();
		long startTime = System.currentTimeMillis();
		
		// This inner loop gets called half a billion times, so there are some
		// unusual optimizations to minimize object allocations (looping & calling charAt vs substring, for instance)
		
		// read through forward-file, extract and attach barcodes to reverse file
		try (ReusingBufferedReader forward = new ReusingBufferedReader(new InputStreamReader(iisFwd));
				ReusingBufferedReader reverse = new ReusingBufferedReader(new InputStreamReader(iisRev));
				BufferedWriter debugOut = config.isDebugOut() ? new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream("debugOut.txt"))) : null;) {

			// both reading and writing to disk tends to buffer; build up enough 
			// work in the queue so that one thread can work while the other is flushing/filling the buffer
			// Generally, the writing thread takes longer than the reading thread
			int bufferSize = 150; 
			ArrayBlockingQueue<Read> availableReadPool = new ArrayBlockingQueue<>(bufferSize);
			ArrayBlockingQueue<Read> loadedReads = new ArrayBlockingQueue<>(bufferSize);
			for (int i = 0; i < bufferSize; i++) {
				availableReadPool.add(new Read());
			}
			int nPersistThreads = Math.min(Runtime.getRuntime().availableProcessors(), MAX_NUM_PERSIST_THREADS);
			CountDownLatch persistFinished = new CountDownLatch(nPersistThreads);
			ExecutorService exec = Executors.newFixedThreadPool(nPersistThreads + 1);
			Future<?> load = exec.submit(() -> {
				CopyBarcodes.doLoad(config.isFuzzyMatch(), config.isDebugOut(), retainBehavior, barcodes, stats, forward, reverse, availableReadPool, loadedReads);
			});
			List<Future<?>> persists = new ArrayList<>();
			for (int i = 0; i < nPersistThreads; i++) {
				persists.add(exec.submit(() -> {
					doWrite(barcodeToOutputFile, availableReadPool, loadedReads, persistFinished,
							debugOut, tracker);
				}));
			}
			
			// first .get call will return once the input files have been fully read.
			// Interrupt the persistence thread to tell it no more work is getting added to the queue
			// await the countdown latch to see when the outstanding work queue has been fully persisted
			// (we can't just call .get again, because that will immediately return and say the future was cancelled)
			load.get();
			
			// for test purposes, make sure the second thread has started before cancelling it
			if (System.currentTimeMillis() - startTime < 1000) {
				Thread.sleep(1000);
			}
			
			persists.forEach(persist -> persist.cancel(true));
			persistFinished.await();
			exec.shutdown();
		}
		progressPrinter.cancel(true);
		progressThread.shutdownNow();
		
		barcodeToOutputFile.values().forEach(file -> {
			try {
				file.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		long endTime = System.currentTimeMillis();
		long duration = (endTime - startTime);
		String timeStr;
		if (duration > 60000) {
			timeStr = duration / (60 * 1000) + " minutes";
		} else {
			timeStr = duration + " ms";
		}
		System.out.println("Ran with config: " + config);
		System.out.println("Finished, wrote " + stats.nWritten.get() + ", skipped " 
				+ stats.nSkipped.get() +  ", redacted " + stats.nRedacted.get() + ", fuzzed " + stats.nFuzzed.get() + " in " + timeStr);
		if (config.isDebugOut()) {
			System.out.println("Skipped " + stats.nSkippedDuplicate.get() + " due to non-unique fixes, " 
					+ stats.nSkippedQuality.get() + " due to quality scores, and " 
					+ stats.nSkippedMultipleBadReads.get() + " due to more than one character being off, and "
					+ stats.nSkippedHeader.get() + " due to a mismatched header");
			
			System.out.println("Totals per barcode:");
			barcodeToOutputFile.forEach((barcode, file) -> {
				System.out.println(barcodeToSample.get(barcode) + ": " + file.getNumWritten());
			});
		}
	}

	private static void doWrite(Map<String, OutputFile> barcodeToOutputFile, ArrayBlockingQueue<Read> availableReadPool,
			ArrayBlockingQueue<Read> loadedReads, CountDownLatch persistFinished, BufferedWriter debugOut, ProgressTracker tracker) {
		try {
			while (true) {
				Read read = loadedReads.take();
				persistBarcodedRead(barcodeToOutputFile, read, debugOut);
				tracker.noteProgress();
				availableReadPool.put(read);
			}
		} catch (InterruptedException e) {
			// stop reading, this is pretty hacky, but avoids having to check a conditional constantly
			try {
				while (!loadedReads.isEmpty()) {
					Read read = loadedReads.take();
					persistBarcodedRead(barcodeToOutputFile, read, debugOut);
				}
			} catch (InterruptedException | IOException e1) {
				e1.printStackTrace();
			} finally {
				persistFinished.countDown();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void persistBarcodedRead(Map<String, OutputFile> outputs, Read read, BufferedWriter debugOut) throws IOException {
		// only keep properly barcoded lines
		if (read.barcodeLen >= MIN_BARCODE_LEN) {
			String barcode = new String(read.forwardLineSet[1], 0, read.barcodeLen);
			OutputFile output = outputs.get(barcode);
			output.write(read, read.barcodeLen);
		} else if (read.fuzzedMatch != null) {
			OutputFile output = outputs.get(read.fuzzedMatch);
			output.write(read, read.fuzzedMatch.length());
		} else if (debugOut != null) {
			synchronized (debugOut) {
				debugOut.write(read.forwardLineSet[1], 0, read.lineLens[1]);
				debugOut.newLine();
				debugOut.write(read.forwardLineSet[3], 0, read.lineLens[3]);
				debugOut.newLine();
			}
		}
	}
}
