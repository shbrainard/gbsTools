import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;

public class ByteBasedProgressTracker implements ProgressTracker {

	private static int GZIP_READ_PER_KB = 45_000;
	private static int PERCENT_TO_TWO_DECIMALS = 10_000;
	
	private AtomicLong numWritten = new AtomicLong(0);
	private final long expected;
	private final long updateInterval;
	private long lastWritten = 0;
	private final PrintStream out;
	
	public ByteBasedProgressTracker(long numBytes) {
		this(numBytes, System.out);
	}
	
	public ByteBasedProgressTracker(long numBytes, PrintStream out) {
		expected = Math.max(1, (numBytes / 1024) * GZIP_READ_PER_KB);
		updateInterval = expected / PERCENT_TO_TWO_DECIMALS;
		this.out = out;
	}
	
	@Override
	public void noteProgress() {
		numWritten.incrementAndGet();
	}

	@Override
	public void printProgress() {
		long current = numWritten.get();
		if ((current - lastWritten) > updateInterval) {
			// use int math to get percent to two decimal places, then convert to an actual decimal
			double percent = (current * PERCENT_TO_TWO_DECIMALS / expected) / 100.0;
			out.println("Processed roughly " + percent + " percent (" + current + " reads)");
		}
	}

}
