package it.adrian.code.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Native;

/**
 * JNA bindings for {@code libproc.h} (macOS process info library).
 *
 * <p>Provides process enumeration, name lookup, and path resolution via macOS's
 * process library APIs.</p>
 *
 * @author Christopher Project
 */
public interface LibProc extends Library {
    LibProc INSTANCE = Native.load("c", LibProc.class);

    // proc_listpids flags
    int PROC_ALL_PIDS = 1;
    int PROC_PGRP_ONLY = 2;
    int PROC_TTY_ONLY = 3;
    int PROC_UID_ONLY = 4;
    int PROC_RUID_ONLY = 5;

    // proc_info_call_types
    int PROC_PIDPATHINFO = 12;
    int PROC_PIDVNODEPATHINFO = 9;

    /**
     * Lists all process IDs of the given type.
     *
     * <pre>
     *   int proc_listpids(uint32_t type, uint32_t typeinfo,
     *                     int *buf, int bufsize);
     * </pre>
     *
     * @param type Type of processes to list (e.g., {@code PROC_ALL_PIDS})
     * @param typeinfo Additional filter info (usually 0)
     * @param buf Array of int to fill with process IDs
     * @param bufsize Size of buffer in bytes
     * @return Number of bytes written, or 0 on error
     */
    int proc_listpids(int type, int typeinfo, int[] buf, int bufsize);

    /**
     * Returns the base name of the process corresponding to pid.
     *
     * <pre>
     *   int proc_name(int pid, void *buffer, uint32_t buffersize);
     * </pre>
     *
     * @param pid Process ID
     * @param buffer Destination buffer for process name
     * @param buffersize Size of buffer in bytes
     * @return Length of name written, or 0 on error
     */
    int proc_name(int pid, byte[] buffer, int buffersize);

    /**
     * Returns the full path for the executable of the process.
     *
     * <pre>
     *   int proc_pidpath(int pid, void *buffer, uint32_t buffersize);
     * </pre>
     *
     * @param pid Process ID
     * @param buffer Destination buffer for full path
     * @param buffersize Size of buffer in bytes
     * @return Length of path written (not including null terminator), or 0 on error
     */
    int proc_pidpath(int pid, byte[] buffer, int buffersize);

    /**
     * Returns the effective user ID of the current process.
     *
     * <pre>
     *   uid_t geteuid(void);
     * </pre>
     *
     * @return Effective UID (0 for root)
     */
    int geteuid();
}
