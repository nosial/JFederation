package net.nosial.jfederation.records;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A file submitted with scanned content, such as an image of a message (OFD 2.1 "Scanned files"). Every server
 * accepts files, a server that does not scan them, or does not scan them for the requester, ignores them. The server
 * determines the media type of a file from its content, so none is sent.
 *
 * @param fileName The name of the file, or {@code null}; the server reduces it to a safe form
 * @param content The content of the file, which must not be empty
 */
public record ScanFile(String fileName, byte[] content)
{
    /**
     * Compact constructor, a file must not be empty.
     */
    public ScanFile
    {
        Objects.requireNonNull(content, "content must not be null");
        if (content.length == 0)
        {
            throw new IllegalArgumentException("A scanned file must not be empty");
        }
    }

    /**
     * Creates a scanned file from the content of an unnamed file.
     *
     * @param content The content of the file, which must not be empty
     */
    public ScanFile(byte[] content)
    {
        this(null, content);
    }

    /**
     * Reads a scanned file from a local path, named after the file.
     *
     * @param path The path of the file
     * @return The scanned file
     * @throws UncheckedIOException if the file cannot be read
     * @throws IllegalArgumentException if the file is empty
     */
    public static ScanFile of(Path path)
    {
        try
        {
            return new ScanFile(path.getFileName() != null ? path.getFileName().toString() : null, Files.readAllBytes(path));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Failed to read the file " + path, e);
        }
    }
}
