package it.adrian.code.platform;

public abstract class ProcessSession {

    public final int pid;

    protected ProcessSession(int pid) {
        this.pid = pid;
    }
}
