# Changelog

All notable changes to this project are documented in this file.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `it.adrian.code.platform.NativeAccess` cross-platform layer that selects
  the right backend (`WindowsAccess` or `LinuxAccess`) at runtime via
  reflective class loading, so the unused backend's native libraries are
  never initialised.
- Linux backend that talks to `/proc/<pid>/{maps,mem,comm,exe}` and uses
  `libc geteuid()` for the privilege check.
- `Pointer` is now `AutoCloseable`; the underlying handle / file descriptor
  is released on `close()` (use it in a try-with-resources block).
- Bulk I/O on `Pointer`: `readBytes(int)`, `writeBytes(byte[])`,
  `readString(int [, Charset])`, `writeString(String [, Charset])`,
  `readShort` / `writeShort`, `readByte` / `writeByte`.
- Configurable endianness via `Pointer.withByteOrder(ByteOrder)`.
- `Pointer.indirect32()` for chasing 32-bit pointers (32-bit targets).
- `ProcessUtil.listModules(int pid)` returns a cross-platform
  `List<ModuleInfo>` (name, full path, base address, size).
- Cross-platform AOB scanning via
  `SignatureUtil.findSignature(ProcessSession, …)` and
  `new SignatureManager(Pointer)` / `new SignatureManager(ProcessSession, String)`.
- Windows-only memory protection / allocation primitives:
  `NativeAccess.protect`, `allocate`, `free`, `queryProtection`
  (wrapping `VirtualProtectEx` / `VirtualAllocEx` / `VirtualFreeEx` /
  `VirtualQueryEx`). On Linux `queryProtection` reads the permissions
  column of `/proc/<pid>/maps`; `protect/allocate/free` throw
  `UnsupportedOperationException`.
- `Pointer.force()` returns a sibling pointer whose writes bypass page
  protection: on Windows it flips the affected pages to
  `PAGE_EXECUTE_READWRITE`, performs the write, then restores the original
  protection — so patches into a read-only `.text` section work. On Linux
  it is a no-op because `/proc/<pid>/mem` already ignores page protection
  for callers with `CAP_SYS_PTRACE`.
- Dedicated exception hierarchy under `it.adrian.code.exceptions`:
  `Mem4JException`, `PrivilegeException`, `ProcessNotFoundException`,
  `ModuleNotFoundException`, `MemoryAccessException`.
- `LICENSE` (MIT).
- CI matrix (`ubuntu-latest` + `windows-latest`).
- Sources jar and Javadoc jar are now produced as build artefacts so
  consumers see docs in their IDE.

### Changed
- **Breaking:** `Memory.readMemory` / `writeMemory` no longer truncate the
  offset to 32 bits — the full `long` range is used.
- **Breaking:** missing privileges and missing process now raise
  `PrivilegeException` / `ProcessNotFoundException` instead of showing a
  `MessageBox` and calling `System.exit(-1)`. The library is now safe to
  embed inside larger applications.
- **Breaking:** failed memory reads now throw `MemoryAccessException`
  instead of returning zero / garbage bytes silently.
- `SignatureManager` no longer closes its own handle in `finally` — the
  caller owns the lifecycle of the underlying `Pointer` / `ProcessSession`.

### Deprecated
- `Pointer(WinNT.HANDLE, com.sun.jna.Pointer)` constructor — use
  `Pointer.getBaseAddress(String)` or `new Pointer(ProcessSession, long)`.
- `Pointer.getModuleBaseAddress(int, String)` (returning a JNA pointer) —
  use `NativeAccess.get().getModuleBaseAddress(int, String)`.
- `ProcessUtil.getModule(int, String)` — use `ProcessUtil.listModules(int)`.
- `SignatureUtil.findSignature(WinNT.HANDLE, …)` and
  `SignatureUtil.readInt(WinNT.HANDLE, …)` — use the
  `ProcessSession`-based overloads.
- `new SignatureManager(WinNT.HANDLE, String, int)` — use
  `new SignatureManager(Pointer)` or
  `new SignatureManager(ProcessSession, String)`.

### Fixed
- `writeFloat` previously allocated and zero-initialised an 8-byte JNA
  `Memory` buffer but only wrote 4 bytes, then asked the kernel to write 4
  bytes from a buffer it had partially filled (a behaviour bug introduced
  in an earlier refactor). Bulk write now goes through a single
  `byte[]` that is always the exact size of the value.
- `jitpack.yml` pins the JitPack build to OpenJDK 11; previously it
  defaulted to JDK 8 and failed `--release 11`, breaking JitPack consumers.
- GitHub Actions workflow upgraded to `setup-java@v4` (removes the
  deprecated `set-output` warning) and granted `contents: write` so the
  Dependency Submission API no longer 403s.

## [1.0.0] - 2026-05-16

Initial published release. Windows-only memory manipulation via JNA.
