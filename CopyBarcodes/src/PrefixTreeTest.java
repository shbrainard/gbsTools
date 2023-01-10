import org.junit.Test;

public class PrefixTreeTest {
	
	@Test
	public void testTree() throws Exception {
		PrefixTree tree = new PrefixTree(Config.loadOptions(new String[] {"default.config"}));
		tree.addBarcode("CGA");
		tree.addBarcode("GCAGCAGC");
		
		int len = tree.findBarcodeLen("CGAT".toCharArray());
		assert len == 0; // no overhang
		
		len = tree.findBarcodeLen("CGACAGCT".toCharArray());
		assert len == 3; 
		
		len = tree.findBarcodeLen("GCAGCAGCCAGCT".toCharArray());
		assert len == 8; 
		
		len = tree.findBarcodeLen("GCAGCAGCACAGCT".toCharArray());
		assert len == 0; // too long before overhang
	}
	
	@Test
	public void testFuzz() throws Exception {
		//overhang=CAGC,CTGC
		PrefixTree tree = new PrefixTree(Config.loadOptions(new String[] {"default.config"}));
		tree.addBarcode("CGA");
		tree.addBarcode("GCA");
		tree.addBarcode("GCT");
		
		OutputStats stats = new OutputStats();
		
		String match = tree.fuzzyMatch("CGACAGCT".toCharArray(), "FFFFFFFFFFF".toCharArray(), null);
		assert match.equals("CGA");
		
		match = tree.fuzzyMatch("CGTCAGCT".toCharArray(), "FFJFFFFFFFF".toCharArray(), stats);
		assert match.equals("");
		assert stats.nSkippedQuality.get() == 1;
		
		match = tree.fuzzyMatch("CGTCAGCT".toCharArray(), "FFFFFFFFFFF".toCharArray(), null);
		assert match.equals("CGA");
		
		match = tree.fuzzyMatch("GCGCAGCT".toCharArray(), "FFFFFFFFFFF".toCharArray(), stats);
		assert match.equals("");
		assert stats.nSkippedDuplicate.get() == 1;
	}
}
