import java.util.Random;

public class RetainRandomSample implements RetainBehavior {
	private final Random rand = new Random();
	private final int percentToKeep;

	public RetainRandomSample(int percentToKeep) {
		this.percentToKeep = percentToKeep;
	}

	@Override
	public boolean keepRead() {
		return rand.nextInt(100) < percentToKeep;
	}
}
