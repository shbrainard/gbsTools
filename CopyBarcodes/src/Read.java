
public class Read {
	char[][] forwardLineSet = new char[4][CopyBarcodes.MAX_LINE_LEN];
	char[][] reverseLineSet = new char[4][CopyBarcodes.MAX_LINE_LEN];
	int[] lineLens = new int[8];
	int barcodeLen;
	String fuzzedMatch = null;
}