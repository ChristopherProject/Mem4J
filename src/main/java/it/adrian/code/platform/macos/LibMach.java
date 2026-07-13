package it.adrian.code.platform.macos;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * JNA bindings for macOS Mach kernel APIs (subset for memory operations).
 *
 * <p>Provides low-level memory protection, allocation, and introspection via Mach ports.
 * These operations typically require appropriate entitlements and code signing.</p>
 *
 * @author Christopher Project
 */
public interface LibMach extends Library {
    LibMach INSTANCE = Native.load("System", LibMach.class);

    // Mach VM protection flags (VM_PROT_*)
    int VM_PROT_NONE = 0x00;
    int VM_PROT_READ = 0x01;
    int VM_PROT_WRITE = 0x02;
    int VM_PROT_EXECUTE = 0x04;
    int VM_PROT_DEFAULT = (VM_PROT_READ | VM_PROT_WRITE);
    int VM_PROT_ALL = (VM_PROT_READ | VM_PROT_WRITE | VM_PROT_EXECUTE);

    // Mach return codes
    int KERN_SUCCESS = 0;
    int KERN_INVALID_TASK = 4;
    int KERN_PROTECTION_FAILURE = 2;

    /**
     * Modifies the protection state of a memory region.
     *
     * <pre>
     *   kern_return_t mach_vm_protect(vm_map_t target_task,
     *                                  mach_vm_address_t address,
     *                                  mach_vm_size_t size,
     *                                  boolean_t set_maximum,
     *                                  vm_prot_t new_protection);
     * </pre>
     *
     * @param targetTask Target task port (typically from task_for_pid)
     * @param address Start address of region
     * @param size Size of region in bytes
     * @param setMaximum If true, sets the maximum protection; if false, sets current
     * @param newProtection New protection flags (VM_PROT_*)
     * @return KERN_SUCCESS on success, or error code
     */
    int mach_vm_protect(int targetTask, long address, long size, boolean setMaximum, int newProtection);

    /**
     * Allocates memory in a remote task.
     *
     * <pre>
     *   kern_return_t mach_vm_allocate(vm_map_t target_task,
     *                                   mach_vm_address_t *address,
     *                                   mach_vm_size_t size,
     *                                   int flags);
     * </pre>
     *
     * @param targetTask Target task port
     * @param address Pointer to address variable (input: hint or VM_FLAGS_ANYWHERE; output: allocated address)
     * @param size Size to allocate in bytes
     * @param flags Allocation flags (e.g., VM_FLAGS_ANYWHERE)
     * @return KERN_SUCCESS on success, or error code
     */
    int mach_vm_allocate(int targetTask, long[] address, long size, int flags);

    /**
     * Deallocates memory in a remote task.
     *
     * <pre>
     *   kern_return_t mach_vm_deallocate(vm_map_t target_task,
     *                                     mach_vm_address_t address,
     *                                     mach_vm_size_t size);
     * </pre>
     *
     * @param targetTask Target task port
     * @param address Start address to deallocate
     * @param size Size to deallocate in bytes
     * @return KERN_SUCCESS on success, or error code
     */
    int mach_vm_deallocate(int targetTask, long address, long size);

    /**
     * Reads data from a remote task's memory.
     *
     * <pre>
     *   kern_return_t mach_vm_read(vm_map_t target_task,
     *                               mach_vm_address_t address,
     *                               mach_vm_size_t size,
     *                               vm_offset_t *data,
     *                               mach_msg_type_number_t *dataCnt);
     * </pre>
     *
     * @param targetTask Target task port
     * @param address Address to read from
     * @param size Number of bytes to read
     * @param data Output: pointer to allocated buffer
     * @param dataCnt Output: number of bytes read
     * @return KERN_SUCCESS on success, or error code
     */
    int mach_vm_read(int targetTask, long address, long size, Pointer[] data, long[] dataCnt);

    /**
     * Writes data to a remote task's memory.
     *
     * <pre>
     *   kern_return_t mach_vm_write(vm_map_t target_task,
     *                                mach_vm_address_t address,
     *                                vm_offset_t data,
     *                                mach_msg_type_number_t dataCnt);
     * </pre>
     *
     * @param targetTask Target task port
     * @param address Address to write to
     * @param data Pointer to source data
     * @param dataCnt Number of bytes to write
     * @return KERN_SUCCESS on success, or error code
     */
    int mach_vm_write(int targetTask, long address, Pointer data, long dataCnt);

    /**
     * Returns the task port for a given process ID.
     *
     * <pre>
     *   kern_return_t task_for_pid(mach_port_t target_tport,
     *                               pid_t pid,
     *                               mach_port_t *t);
     * </pre>
     *
     * <p><strong>Requires entitlements:</strong> {@code com.apple.security.task.for-pid-allow}</p>
     *
     * @param targetTport Target task port (usually mach_task_self())
     * @param pid Process ID
     * @param task Output: task port for the process
     * @return KERN_SUCCESS on success, or error code (typically KERN_FAILURE if not entitled)
     */
    int task_for_pid(int targetTport, int pid, int[] task);

    /**
     * Returns the current task's port.
     *
     * <pre>
     *   mach_port_t mach_task_self(void);
     * </pre>
     *
     * @return Current task's port
     */
    int mach_task_self();
}
