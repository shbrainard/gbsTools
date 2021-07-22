import java.util.concurrent.atomic.AtomicInteger;

public class OutputStats {
	final AtomicInteger nRedacted = new AtomicInteger(0);
	final AtomicInteger nWritten = new AtomicInteger(0);
	final AtomicInteger nFuzzed = new AtomicInteger(0);
	final AtomicInteger nSkipped = new AtomicInteger(0);
	final AtomicInteger nSkippedHeader = new AtomicInteger(0);
	final AtomicInteger nSkippedDuplicate = new AtomicInteger(0);
	final AtomicInteger nSkippedMultipleBadReads = new AtomicInteger(0);
	final AtomicInteger nSkippedQuality = new AtomicInteger(0);
}