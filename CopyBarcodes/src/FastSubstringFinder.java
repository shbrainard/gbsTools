import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class FastSubstringFinder {

	static int readAheadPos;
	
	// pass in <path to input file> <string to match>, and the output will go to the console (which can be piped to a file)
	public static void main(String[] args) throws Exception {
		BufferedReader in = new BufferedReader(new FileReader(args[0]));
		String pattern = args[1];
		
		char[] readAhead = new char[pattern.length() - 1];
		readAheadPos = readAhead.length;
		char[] cbuf = new char[1];
		int overallPos = 0;
		while (read(in, cbuf, readAhead)) {
			overallPos++;
			if (cbuf[0] == pattern.charAt(0)) {
				if (!fillReadAheadBuffer(readAhead, in)) {
					break;
				}
				if (matches(in, readAhead, pattern)) {
					System.out.println(overallPos + " ");
				}
			}
		}
	}

	private static boolean fillReadAheadBuffer(char[] readAhead, BufferedReader in) throws IOException {
		if (readAheadPos < readAhead.length) {
			System.arraycopy(readAhead, readAheadPos, readAhead, 0, readAhead.length - readAheadPos);
		}
		int len = in.read(readAhead, readAhead.length - readAheadPos, readAheadPos);
		if (len != readAheadPos) {
			return false;
		}
		readAheadPos = 0;
		return true;
	}

	private static boolean matches(BufferedReader in, char[] readAhead, String pattern) {
		for (int i = 0; i < readAhead.length; i++) {
			if (pattern.charAt(i + 1) != readAhead[i]) {
				return false;
			}
		}
		return true;
	}

	private static boolean read(BufferedReader in, char[] cbuf, char[] readAhead) throws IOException {
		if (readAheadPos < readAhead.length) {
			cbuf[0] = readAhead[readAheadPos];
			readAheadPos++;
			return true;
		} else {
			return in.read(cbuf, 0, 1) > 0;
		}
	}
}
