import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPOutputStream;

public class OutputFile {
	private final BufferedWriter forward;
	private final BufferedWriter reverse;

	public OutputFile(String pop, String sample) throws IOException {
		String forwardName = pop + "_" + sample + ".F.fq.gz";
		String reverseName = pop + "_" + sample + ".R.fq.gz";

		forward = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(forwardName))));
		reverse = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(reverseName))));
	}

	public void close() throws IOException {
		forward.close();
		reverse.close();
	}

	public synchronized void write(Read read, int matchedLen) throws IOException {
		forward.write(read.forwardLineSet[0], 0, read.lineLens[0]);
		forward.newLine();
		forward.write(read.forwardLineSet[1], matchedLen, read.lineLens[1] - matchedLen);
		forward.newLine();
		forward.write(read.forwardLineSet[2], 0, read.lineLens[2]);
		forward.newLine();
		forward.write(read.forwardLineSet[3], matchedLen, read.lineLens[3] - matchedLen);
		forward.newLine();
		
		reverse.write(read.reverseLineSet[0], 0, read.lineLens[4]);
		reverse.newLine();
		reverse.write(read.reverseLineSet[1], 0, read.lineLens[5]);
		reverse.newLine();
		reverse.write(read.reverseLineSet[2], 0, read.lineLens[6]);
		reverse.newLine();
		reverse.write(read.reverseLineSet[3], 0, read.lineLens[7]);
		reverse.newLine();
	}
}
