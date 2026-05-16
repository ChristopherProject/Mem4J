package it.adrian.code.platform.linux;

import it.adrian.code.platform.ProcessSession;

import java.io.IOException;
import java.io.RandomAccessFile;

public class LinuxProcessSession extends ProcessSession {

    final RandomAccessFile mem;

    LinuxProcessSession(int pid) throws IOException {
        super(pid);
        this.mem = new RandomAccessFile("/proc/" + pid + "/mem", "rw");
    }

    void close() throws IOException {
        mem.close();
    }
}
