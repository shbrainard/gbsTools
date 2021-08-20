import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

public class OutputFile {
	private final BufferedWriter forward;
	private final BufferedWriter reverse;
	private AtomicInteger nWritten = new AtomicInteger(0);

	public OutputFile(String pop, String sample, boolean alignmentFile, boolean append) throws IOException {
		String forwardName = pop + "_" + sample + (alignmentFile ? ".F" : ".R1") + ".fq.gz";
		String reverseName = pop + "_" + sample + (alignmentFile ? ".R" : ".R2")  + ".fq.gz";

		forward = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(forwardName, append))));
		reverse = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(reverseName, append))));
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
		nWritten.incrementAndGet();
	}

	public int getNumWritten() {
		return nWritten.get();
	}
}
