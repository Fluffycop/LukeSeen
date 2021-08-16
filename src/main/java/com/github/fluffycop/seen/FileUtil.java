package com.github.fluffycop.seen;

import java.io.*;
import java.nio.file.Files;

public final class FileUtil {
    private FileUtil() {

    }

    static void copyFileToFile(final File src, final File dest) throws IOException {
        copyInputStreamToFile(new FileInputStream(src), dest);
        dest.setLastModified(src.lastModified());
    }

    static void copyInputStreamToFile(final InputStream in, final File dest)
            throws IOException {
        copyInputStreamToOutputStream(in, new FileOutputStream(dest));
    }

    static boolean isEmpty(final File file) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            return br.readLine() == null;
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    static void copyInputStreamToOutputStream(final InputStream in,
                                                     final OutputStream out) throws IOException {
        try {
            try {
                final byte[] buffer = new byte[1024];
                int n;
                while ((n = in.read(buffer)) != -1)
                    out.write(buffer, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    static String read(final File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()));
    }

    static void write(String content, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content);
        }
    }

    static String nameWithoutExtension(File file) {
        return file.getName().replaceFirst("[.][^.]+$", "");
    }
}