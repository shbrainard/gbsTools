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
	private final char minQuality;
	
	private char[] fuzzyMatchStr;
	
	public PrefixTree(boolean includeOverhangs, Config config) {
		if (includeOverhangs) {
			// if the enzyme changes, these may need to change
			overhangs.addAll(config.getOverhangs());
			OVERHANG_LEN = overhangs.iterator().next().length();
		} else {
			overhangs.add("");
			OVERHANG_LEN = 0;
		}
		minQuality = config.getMinQuality();
		MAX_BARCODE_LEN = 8 + OVERHANG_LEN;
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
	
	private static class FuzzyMatchReason {
		boolean highQuality = false;
		boolean duplicate = false;
	}
	
	public String fuzzyMatch(String read, String quality, OutputStats stats) {
		FuzzyMatchReason fuzzyMatchReason = new FuzzyMatchReason();
		int len = fuzzyMatchRec(root, read, quality, 0, true, stats == null ? null : fuzzyMatchReason);
		if (len > 0) {
			return new String(fuzzyMatchStr, 0, len - 1 - OVERHANG_LEN);
		} else if (stats != null) {
			if (fuzzyMatchReason.duplicate) {
				stats.nSkippedDuplicate.getAndIncrement();
			} else if (fuzzyMatchReason.highQuality) {
				stats.nSkippedQuality.getAndIncrement();
			} else {
				stats.nSkippedMultipleBadReads.getAndIncrement();
			}
		}
		return "";
	}
	
	private int fuzzyMatchRec(Node node, String read, String quality, int pos,
			boolean fuzzyMatch, FuzzyMatchReason reason) {
		if (node.isBarcode) {
			return 1;
		}
		if (pos == MAX_BARCODE_LEN) { // assume read length is always greater than 12
			return 0; 
		}
		Node link = node.children[read.charAt(pos) - 65];
		if (link != null) {
			int len = fuzzyMatchRec(link, read, quality, pos + 1, fuzzyMatch, reason);
			if (len > 0) {
				fuzzyMatchStr[pos] = read.charAt(pos);
				return len + 1;
			}
		}
		// if we found no match, see if we're a suitable candidate for fuzzy matching
		// require a unique match at the position to be a valid fuzzy match
		// the exception to the 'unique match' is in the overhang - we don't actually
		// care which overhang it is.
		if (fuzzyMatch && quality.charAt(pos) < minQuality) {
			int nBarcodesFound = 0;
			int foundLen = 0;
			char foundChar = ' ';
			for (int i = 0; i < node.children.length; i++) {
				if (node.children[i] != null) {
					int len = fuzzyMatchRec(node.children[i], read, quality, pos + 1, false, reason);
					if (len > 0) {
						foundLen = len;
						foundChar = (char) (i + 'A');
						if (len > OVERHANG_LEN) {
							nBarcodesFound++;
						}
					}
				}
			}
			if (nBarcodesFound <= 1 && foundLen > 0) {
				fuzzyMatchStr[pos] = foundChar;
				return foundLen + 1;
			} else if (reason != null && nBarcodesFound > 1) {
				reason.duplicate = true;
			}
		} else if (reason != null && quality.charAt(pos) >= 'F') {
			reason.highQuality = true;
		}
		return 0; 
	}
	
}
