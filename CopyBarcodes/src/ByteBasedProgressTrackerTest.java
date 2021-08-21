import java.io.PrintStream;

import org.junit.jupiter.api.Test;

public class ByteBasedProgressTrackerTest {
	
	private boolean wroteOutput = false;
	private double percent;

	@Test
	public void testProgress() {
		PrintStream checker = new PrintStream(System.out) {
			@Override
			public void println(String str) {
				wroteOutput = true;
				int start = "Processed roughly ".length();
				percent = Double.parseDouble(str.substring(start, str.indexOf(" ", start + 1)));
			}
		};
		ByteBasedProgressTracker tracker = new ByteBasedProgressTracker(2048, checker);
		tracker.printProgress();
		assert !wroteOutput;
		tracker.noteProgress();
		tracker.printProgress();
		assert !wroteOutput;
		for (int i = 0; i < 100; i++) {
			tracker.noteProgress();
		}
		tracker.printProgress();
		assert wroteOutput;
		assert percent >  0;
		assert percent < 0.5;
	}
}
