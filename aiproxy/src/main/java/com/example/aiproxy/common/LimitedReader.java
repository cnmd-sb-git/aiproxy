package com.example.aiproxy.common;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class LimitedReader extends FilterInputStream {
    private long remaining;
    private boolean exceeded = false;

    public static final String ERR_LIMITED_READER_EXCEEDED = "Limited reader exceeded";

    public LimitedReader(InputStream in, long limit) {
        super(in);
        this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
        if (exceeded) {
            throw new IOException(ERR_LIMITED_READER_EXCEEDED);
        }
        if (remaining <= 0) {
            exceeded = true; // Mark as exceeded for subsequent calls
            throw new IOException(ERR_LIMITED_READER_EXCEEDED);
        }
        int b = super.read();
        if (b != -1) {
            remaining--;
        } else {
             // End of stream, but limit not necessarily exceeded.
             // If remaining was 0 and we hit EOF, it's fine.
             // If remaining was >0 and we hit EOF, it's also fine.
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (exceeded) {
            throw new IOException(ERR_LIMITED_READER_EXCEEDED);
        }
        if (remaining <= 0) {
            exceeded = true;
            throw new IOException(ERR_LIMITED_READER_EXCEEDED);
        }
        if (len > remaining) {
            len = (int) remaining;
        }
        int n = super.read(b, off, len);
        if (n > 0) {
            remaining -= n;
        } else if (n == -1) {
            // EOF
        }
        return n;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public long skip(long n) throws IOException {
        if (n > remaining) {
            n = remaining;
        }
        long skipped = super.skip(n);
        remaining -= skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        int available = super.available();
        return (long)available > remaining ? (int) remaining : available;
    }

    public boolean hasExceeded() {
        return exceeded;
    }

    public long getRemaining() {
        return remaining;
    }
}
