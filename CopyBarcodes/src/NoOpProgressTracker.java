
public class NoOpProgressTracker implements ProgressTracker {

	@Override
	public void noteProgress() {
		// do nothing
	}

	@Override
	public void printProgress() {
		// do nothing
	}

}
