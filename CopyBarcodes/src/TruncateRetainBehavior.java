
public class TruncateRetainBehavior implements RetainBehavior {

	private long numReads = 0;
	private final long numReadsToKeep;

	public TruncateRetainBehavior(long numReadsToKeep) {
		this.numReadsToKeep = numReadsToKeep;
	}

	@Override
	public boolean keepRead() {
		numReads++;
		return numReads < numReadsToKeep;
	}

}
