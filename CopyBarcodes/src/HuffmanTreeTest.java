import org.junit.Test;

public class HuffmanTreeTest {

	@Test
	public void testTree() throws Exception {
		HuffmanTree tree = new HuffmanTree(true);
		tree.addBarcode("CGA");
		tree.addBarcode("GCAGCAGC");
		
		int len = tree.findBarcodeLen("CGAT");
		assert len == 0; // no overhang
		
		len = tree.findBarcodeLen("CGACAGCT");
		assert len == 3; 
		
		len = tree.findBarcodeLen("GCAGCAGCCAGCT");
		assert len == 8; 
		
		len = tree.findBarcodeLen("GCAGCAGCACAGCT");
		assert len == 0; // too long before overhang
	}
	
	@Test
	public void testFastTree() throws Exception {
		HuffmanTree tree = new HuffmanTree(false);
		tree.addBarcode("CGA");
		tree.addBarcode("GCAGCAGC");
		
		int len = tree.findBarcodeLen("CGAT");
		assert len == 3; // no overhang
		
		len = tree.findBarcodeLen("CGACAGCT");
		assert len == 3; 
		
		len = tree.findBarcodeLen("GCAGCAGCCAGCT");
		assert len == 8; 
		
		len = tree.findBarcodeLen("GCAGCAGCACAGCT");
		assert len == 8; // too long before overhang
	}
	
	@Test
	public void testFuzz() throws Exception {
		HuffmanTree tree = new HuffmanTree(true);
		tree.addBarcode("CGA");
		tree.addBarcode("GCA");
		tree.addBarcode("GCT");
		
		String match = tree.fuzzyMatch("CGACAGCT", "FFFFFFFFFFF");
		assert match.equals("CGA");
		
		match = tree.fuzzyMatch("CGTCAGCT", "FFFFFFFFFFF");
		assert match.equals("");
		
		match = tree.fuzzyMatch("CGTCAGCT", "FF,FFFFFFFF");
		assert match.equals("CGA");
		
		match = tree.fuzzyMatch("GCGCAGCT", "FF,FFFFFFFF");
		assert match.equals("");
		
		
	}
}
