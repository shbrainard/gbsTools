
public class LoadConfig {
	private final boolean fuzzyMatch;
	private final boolean debug;
	private final boolean reverseMissing;
	private final RetainBehavior retainBehavior;
	
	public LoadConfig(boolean fuzzyMatch, boolean debug, boolean reverseMissing, RetainBehavior retainBehavior) {
		this.fuzzyMatch = fuzzyMatch;
		this.debug = debug;
		this.reverseMissing = reverseMissing;
		this.retainBehavior = retainBehavior;
	}
	
	public boolean isFuzzyMatch() {
		return fuzzyMatch;
	}
	
	public boolean isDebug() {
		return debug;
	}
	
	public boolean isReverseMissing() {
		return reverseMissing;
	}
	
	public RetainBehavior getRetainBehavior() {
		return retainBehavior;
	}
}