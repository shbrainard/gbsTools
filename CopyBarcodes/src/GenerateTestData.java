import java.io.FileOutputStream;
import java.util.zip.GZIPOutputStream;

public class GenerateTestData {
	
	static String[] barcodes = new String[] {"AAAA", "ACTG", "AGTCA", "GCTAC", "CTGACAGT", "TAGCG"};
	
	public static void main(String[] args) throws Exception {
		GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream("forward.gz"));
		for (int i = 0; i < 1000000; i++) {
			out.write("Header\n".getBytes());
			out.write((barcodes[i % barcodes.length] + "CAGCAAACCCGGGTTTAAA\n").getBytes());
			out.write("+\n".getBytes());
			out.write("FFFFFFFFFFFFFFFFFFFF\n".getBytes());
		}
		out.close();
		
		out = new GZIPOutputStream(new FileOutputStream("reverse.gz"));
		for (int i = 0; i < 1000000; i++) {
			out.write("Header\n".getBytes());
			out.write("CAGCAAACCCGGGTTTAAA\n".getBytes());
			out.write("+\n".getBytes());
			out.write("FFFFFFFFFFFFFFFFFFFF\n".getBytes());
		}
		out.close();
		FileOutputStream barcodeOut = new FileOutputStream("barcodes.txt");
		for (String s : barcodes) {
			barcodeOut.write((s + "\tfoo\n").getBytes());
		}
		barcodeOut.close();
	}
}
