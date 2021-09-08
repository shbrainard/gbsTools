
public interface RetainBehaviors {
  static RetainBehavior getRetainBehavior(int percentToKeep, long numReads, boolean truncate) {
	  if (percentToKeep == 100) {
		  return RetainBehavior.KEEP_ALL;
	  } else if (truncate) {
		  return new TruncateRetainBehavior(numReads * percentToKeep / 100);
	  } else {
		  return new RetainRandomSample(percentToKeep);
	  }
  }
}
