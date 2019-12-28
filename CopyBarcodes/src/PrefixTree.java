import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PrefixTree {

	private static class Node {
		private final List<NodeLink> children;
		private boolean isBarcode;
		
		public Node() {
			this.children = new ArrayList<>();
			this.isBarcode = false;
		}
	}
	
	private static class NodeLink {
		private final char letter;
		private final Node node;
		
		public NodeLink(char letter, Node node) {
			this.letter = letter;
			this.node = node;
		}
	}
	
	private final Node root = new Node();
	
	// if the enzyme changes, these may need to change
	private final Set<String> overhangs = new HashSet<>();
	private final int MAX_BARCODE_LEN;
	private final int OVERHANG_LEN;
	private char[] fuzzyMatchStr;
	
	public PrefixTree(boolean includeOverhangs) {
		if (includeOverhangs) {
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
		for (NodeLink link : node.children) {
			if (link.letter == barcode.charAt(pos)) {
				addBarcodeRec(link.node, barcode, pos + 1);
				return;
			}
		}
		// didn't find the next letter, add a new link
		NodeLink newChild = new NodeLink(barcode.charAt(pos), new Node());
		node.children.add(newChild);
		addBarcodeRec(newChild.node, barcode, pos + 1);
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
		for (NodeLink link : node.children) {
			if (link.letter == read.charAt(pos)) {
				int len = findBarcodeLenRec(link.node, read, pos + 1);
				if (len > 0) {
					return len + 1;
				}
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
		for (NodeLink link : node.children) {
			if (link.letter == read.charAt(pos)) {
				int len = fuzzyMatchRec(link.node, read, quality, pos + 1, fuzzyMatch);
				if (len > 0) {
					fuzzyMatchStr[pos] = link.letter;
					return len + 1;
				}
			}
		}
		// if we found no match, see if we're a suitable candidate for fuzzy matching
		// require a unique match at the position to be a valid fuzzy match
		if (fuzzyMatch && quality.charAt(pos) < 'F') {
			int nFound = 0;
			int foundLen = 0;
			char foundChar = ' ';
			for (NodeLink link : node.children) {
				int len = fuzzyMatchRec(link.node, read, quality, pos + 1, false);
				if (len > 0) {
					foundLen = len;
					nFound++;
					foundChar = link.letter;
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
