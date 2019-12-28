import java.util.HashSet;
import java.util.Set;

public class PrefixTree {
	private static class Node {
		// index by offset from 'A', which has an int value of 65
		private final Node[] children = new Node[20];
		private boolean isBarcode = false;
	}
	
	private final Node root = new Node();	
	private final Set<String> overhangs = new HashSet<>();
	private final int MAX_BARCODE_LEN;
	private final int OVERHANG_LEN;
	private char[] fuzzyMatchStr;
	
	public PrefixTree(boolean includeOverhangs) {
		if (includeOverhangs) {
			// if the enzyme changes, these may need to change
			overhangs.add("CAGC");
			overhangs.add("CTGC");
			OVERHANG_LEN = 4;
			MAX_BARCODE_LEN = 12;
		} else {
			overhangs.add("");
			OVERHANG_LEN = 0;
			MAX_BARCODE_LEN = 8;
		}
		fuzzyMatchStr = new char[MAX_BARCODE_LEN];
	}

	
	public void addBarcode(String barcode) {
		for (String overhang : overhangs) {
			addBarcodeRec(root, barcode + overhang, 0);
		}
	}
	
	private void addBarcodeRec(Node node, String barcode, int pos) {
		if (pos == barcode.length()) {
			node.isBarcode = true;
			return;
		}
		Node link = node.children[barcode.charAt(pos) - 65];
		if (link != null) {
			addBarcodeRec(link, barcode, pos + 1);
			return;
		}
		// didn't find the next letter, add a new link
		Node newChild = new Node();
		node.children[barcode.charAt(pos) - 65] = newChild;
		addBarcodeRec(newChild, barcode, pos + 1);
	}

	public int findBarcodeLen(String read) {
		int len = findBarcodeLenRec(root, read, 0);
		return len == 0 ? len : len - 1 - OVERHANG_LEN; // trim overhang and an extra length for root
	}

	private int findBarcodeLenRec(Node node, String read, int pos) {
		if (node.isBarcode) {
			return 1;
		}
		if (pos == MAX_BARCODE_LEN) { // assume read length is always greater than 12
			return 0; 
		}
		Node link = node.children[read.charAt(pos) - 65];
		if (link != null) {
			int len = findBarcodeLenRec(link, read, pos + 1);
			if (len > 0) {
				return len + 1;
			}
		}
		return 0; 
	}
	
	public String fuzzyMatch(String read, String quality) {
		int len = fuzzyMatchRec(root, read, quality, 0, true);
		if (len > 0) {
			return new String(fuzzyMatchStr, 0, len - 1 - OVERHANG_LEN);
		}
		return "";
	}
	
	private int fuzzyMatchRec(Node node, String read, String quality, int pos,
			boolean fuzzyMatch) {
		if (node.isBarcode) {
			return 1;
		}
		if (pos == MAX_BARCODE_LEN) { // assume read length is always greater than 12
			return 0; 
		}
		Node link = node.children[read.charAt(pos) - 65];
		if (link != null) {
			int len = fuzzyMatchRec(link, read, quality, pos + 1, fuzzyMatch);
			if (len > 0) {
				fuzzyMatchStr[pos] = read.charAt(pos);
				return len + 1;
			}
		}
		// if we found no match, see if we're a suitable candidate for fuzzy matching
		// require a unique match at the position to be a valid fuzzy match
		if (fuzzyMatch && quality.charAt(pos) < 'F') {
			int nFound = 0;
			int foundLen = 0;
			char foundChar = ' ';
			for (int i = 0; i < node.children.length; i++) {
				if (node.children[i] != null) {
					int len = fuzzyMatchRec(node.children[i], read, quality, pos + 1, false);
					if (len > 0) {
						foundLen = len;
						nFound++;
						foundChar = (char) (i + 'A');
					}
				}
			}
			if (nFound == 1) {
				fuzzyMatchStr[pos] = foundChar;
				return foundLen + 1;
			}
		}
		return 0; 
	}
	
}
