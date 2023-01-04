import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class MultiFileInputStream extends InputStream {

	private final List<String> files;
	private int index = 0;
	private InflaterInputStream current = null;
	
	public static InputStream getStream(List<String> files) throws IOException {
		if (files.size() == 1) {
			return new GZIPInputStream(
					new FileInputStream(files.get(0)));
		}
		return new MultiFileInputStream(files);
	}

	private MultiFileInputStream(List<String> files) {
		this.files = files;
	}

	@Override
	public int read() throws IOException {
		setupStream();
		return current.read();
	}

	private void setupStream() throws IOException {
		if (current == null || current.available() == 0) {
			if (index < files.size()) {
				current = new GZIPInputStream(
						new FileInputStream(files.get(index)));
				index++;
			}
		}
	}
	
	@Override
	public void close() throws IOException {
		if (current != null) {
			current.close();
		}
	}
	
	
}
