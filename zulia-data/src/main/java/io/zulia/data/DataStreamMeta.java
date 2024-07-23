package io.zulia.data;

import java.io.File;
import java.net.URLConnection;

public record DataStreamMeta(String contentType, String fileName) {

	public static DataStreamMeta fromFileName(String fileName) {
		return new DataStreamMeta(getContentTypeFromFilename(fileName), fileName);
	}

	public static DataStreamMeta fromFullPath(String filePath) {
		File file = new File(filePath);
		String fileName = file.getName();
		return new DataStreamMeta(getContentTypeFromFilename(fileName), fileName);
	}

	public static String getContentTypeFromFilename(String fileName) {
		if (isGzipExtension(fileName)) {
			fileName = fileName.substring(0, fileName.length() - 3);
		}

		return URLConnection.guessContentTypeFromName(fileName);
	}

	public static boolean isGzipExtension(String fileName) {
		return fileName.toLowerCase().endsWith(".gz");
	}
}
