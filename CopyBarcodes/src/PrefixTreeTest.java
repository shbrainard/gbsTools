import org.junit.Test;

public class PrefixTreeTest {
	
	@Test
	public void testTree() throws Exception {
		PrefixTree tree = new PrefixTree(Config.loadFromFile("default.config"));
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
		PrefixTree tree = new PrefixTree(Config.loadFromFile("default.config"));
		tree.addBarcode("CGA");
		tree.addBarcode("GCA");
		tree.addBarcode("GCT");
		
		OutputStats stats = new OutputStats();
		
		String match = tree.fuzzyMatch("CGACAGCT".toCharArray(), "FFFFFFFFFFF".toCharArray(), null);
		assert match.equals("CGA");
		
		match = tree.fuzzyMatch("CGTCAGCT".toCharArray(), "FFFFFFFFFFF".toCharArray(), stats);
		assert match.equals("");
		assert stats.nSkippedQuality.get() == 1;
		
		match = tree.fuzzyMatch("CGTCAGCT".toCharArray(), "FF,FFFFFFFF".toCharArray(), null);
		assert match.equals("CGA");
		
		match = tree.fuzzyMatch("GCGCAGCT".toCharArray(), "FF,FFFFFFFF".toCharArray(), stats);
		assert match.equals("");
		assert stats.nSkippedDuplicate.get() == 1;
	}
}
