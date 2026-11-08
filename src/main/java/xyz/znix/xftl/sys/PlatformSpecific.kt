package xyz.znix.xftl.sys

import org.lwjgl.PointerBuffer
import org.lwjgl.system.*
import org.lwjgl.system.libffi.FFICIF
import org.lwjgl.system.libffi.FFIType
import org.lwjgl.system.libffi.LibFFI
import org.lwjgl.system.windows.WinBase
import org.lwjgl.system.windows.WindowsLibrary
import xyz.znix.xftl.game.SaveProfile
import java.nio.file.Files
import java.nio.file.Path

sealed interface PlatformSpecific {
    /**
     * The directory the user's saves should be stored in.
     */
    val saveGamePath: Path

    val saveProfilePath: Path get() = saveGamePath.resolve(SaveProfile.PROFILE_NAME)
    val modsDirectory: Path get() = saveGamePath.resolve("mods")

    /**
     * The path to a text file, containing the path of the ftl.dat file.
     */
    val ftlDatPathFile: Path get() = saveGamePath.resolve("ftl-path.txt")

    /**
     * The path to a directory, where the cache for modded XML files is stored.
     */
    val xmlCacheDirectory: Path get() = saveGamePath.resolve("mod-xml-cache")

    /**
     * Look through the OS's running processes, to find FTL.
     *
     * If it's running, find the path to it's ftl.dat file.
     */
    fun findRunningInstanceDat(): Path?

    companion object {
        @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
        @JvmField
        val INSTANCE: PlatformSpecific = when (Platform.get()) {
            Platform.WINDOWS -> WindowsPlatform()
            Platform.MACOSX -> MacPlatform()
            // FreeBSD works like Linux, as far as we need
            Platform.LINUX, Platform.FREEBSD -> LinuxPlatform()
        }
    }
}

@Suppress("PrivatePropertyName") // Windows function names
private class WindowsPlatform : PlatformSpecific {
    private val shell32 = WindowsLibrary("Shell32.dll")
    private val user32 = WindowsLibrary("User32.dll")
    private val kernel32 = WindowsLibrary("Kernel32.dll")
    private val psapi = WindowsLibrary("Psapi.dll")

    private val EnumWindows: Long
    private val EnumWindowsCIF: FFICIF
    private val WNDENUMPROC: FFICIF
    private val EnumWindowsProc: CallbackI

    private val GetClassNameA: Long
    private val GetClassNameACIF: FFICIF

    private val GetWindowThreadProcessId: Long
    private val GetWindowThreadProcessIdCIF: FFICIF

    private val OpenProcess: Long
    private val OpenProcessCIF: FFICIF

    private val CloseHandle: Long
    private val CloseHandleCIF: FFICIF

    private val QueryFullProcessImageNameA: Long
    private val QueryFullProcessImageNameACIF: FFICIF

    private lateinit var windowEnumTarget: (Int) -> Unit

    /**
     * The value of the Windows FOLDERID_SavedGames known folder.
     */
    private val userSavedGames: Path

    override val saveGamePath: Path

    init {
        // Call SHGetKnownFolderPath to find the SavedGames folder.
        // If the user has adjusted this path, this respects those changes.
        // Unfortunately, LWJGL doesn't bind this so we have to go through FFI.
        val fnGetKnownFolderPath = shell32.getFunctionAddress("SHGetKnownFolderPath")

        val abi = when {
            Pointer.BITS32 -> LibFFI.FFI_STDCALL
            else -> LibFFI.FFI_DEFAULT_ABI
        }

        MemoryStack.stackPush().use { mem ->
            // Set up the call
            val cif = FFICIF(mem.malloc(FFICIF.SIZEOF))

            val argTypes = mem.pointers(
                LibFFI.ffi_type_pointer, // rfid
                LibFFI.ffi_type_uint32,  // dwFlags
                LibFFI.ffi_type_pointer, // hToken
                LibFFI.ffi_type_pointer, // ppszPath
            )

            val prepResult = LibFFI.ffi_prep_cif(
                cif,
                abi,
                LibFFI.ffi_type_uint32, // Return HRESULT
                argTypes
            )
            check(prepResult == LibFFI.FFI_OK)

            // Make the call

            // FOLDERID_SavedGames
            val guid = mem.malloc(16)
            guid.putInt(0x4c5c32ff)
            guid.putShort(0xbb9d.toShort())
            guid.putShort(0x43b0.toShort())
            guid.put(0xb5.toByte())
            guid.put(0xb4.toByte())
            guid.put(0x2d.toByte())
            guid.put(0x72.toByte())
            guid.put(0xe5.toByte())
            guid.put(0x4e.toByte())
            guid.put(0xaa.toByte())
            guid.put(0xa4.toByte())
            guid.flip()

            val resultBuf = mem.malloc(4)
            val outStrPtr = mem.mallocPointer(1)
            val argsValues = mem.pointers(
                MemoryUtil.memAddress(guid),
                0, // Flags
                0, // 0 = Current user
                MemoryUtil.memAddress(outStrPtr),
            )
            val argsBuf = mem.mallocPointer(argsValues.remaining())
            for (i in 0 until argsValues.remaining()) {
                argsBuf.put(i, MemoryUtil.memAddress(argsValues) + MemoryStack.POINTER_SIZE * i)
            }

            LibFFI.ffi_call(cif, fnGetKnownFolderPath, resultBuf, argsBuf)

            // Handle the result
            val result = resultBuf.getInt()
            if (result != 0) {
                error("Failed to read save directory: HRESULT=$result (decimal)")
            }

            val stringPtr = outStrPtr.get(0)
            val pathString = MemoryUtil.memUTF16(stringPtr)
            userSavedGames = Path.of(pathString)

            // We're supposed to free the string pointer using CoTaskMemFree.
            // Don't bother because it's only a little memory leak, and it'd
            // be mildly inconvenient to do so.
        }

        saveGamePath = userSavedGames.resolve(if (isPackagedGame()) PACKAGED_NAME else "Project Wormhole")

        // Set up the stuff we need for instance scanning
        EnumWindows = user32.getFunctionAddress("EnumWindows")

        EnumWindowsCIF = allocateCIF(
            abi,
            LibFFI.ffi_type_uint8, // Return bool

            LibFFI.ffi_type_pointer, // WNDENUMPROC (callback)
            LibFFI.ffi_type_pointer, // LPARAM (user data)
        )

        WNDENUMPROC = allocateCIF(
            abi,
            LibFFI.ffi_type_uint8, // Return bool

            LibFFI.ffi_type_uint32, // HWND (window in question)
            LibFFI.ffi_type_pointer, // LPARAM (user data)
        )

        // Allocate a callback for EnumWindows. Do this once even though
        // it means we can't be thread-safe, since each time we do this
        // it allocates a little bit of memory.
        // (that might be meaningful, since it has to be VirtualProtect-ed).
        EnumWindowsProc = object : CallbackI {
            override fun getCallInterface(): FFICIF {
                return WNDENUMPROC
            }

            override fun callback(ret: Long, args: Long) {
                val windowHandle = MemoryUtil.memGetInt(MemoryUtil.memGetAddress(args))

                // Ignore the userdata arg.

                // Thread-unsafely run the actual callback
                windowEnumTarget(windowHandle)

                // Return true to continue enumeration
                MemoryUtil.memPutByte(ret, 1)
            }
        }

        GetClassNameA = user32.getFunctionAddress("GetClassNameA")
        GetClassNameACIF = allocateCIF(
            abi,
            LibFFI.ffi_type_uint32, // return int

            LibFFI.ffi_type_uint32, // HWND
            LibFFI.ffi_type_pointer, // LPSTR
            LibFFI.ffi_type_uint32, // int nMaxCount
        )

        GetWindowThreadProcessId = user32.getFunctionAddress("GetWindowThreadProcessId")
        GetWindowThreadProcessIdCIF = allocateCIF(
            abi,
            LibFFI.ffi_type_uint32, // return DWORD

            LibFFI.ffi_type_uint32, // HWND
            LibFFI.ffi_type_pointer, // LPDWORD out process id
        )

        OpenProcess = kernel32.getFunctionAddress("OpenProcess")
        OpenProcessCIF = allocateCIF(
            abi,
            LibFFI.ffi_type_uint32, // return HANDLE

            LibFFI.ffi_type_uint32, // DWORD dwDesiredAccess
            LibFFI.ffi_type_uint8, // bool bInheritHandle
            LibFFI.ffi_type_uint32, // DWORD dwProcessId
        )

        CloseHandle = kernel32.getFunctionAddress("CloseHandle")
        CloseHandleCIF = allocateCIF(
            abi,
            LibFFI.ffi_type_uint8, // return bool

            LibFFI.ffi_type_uint32, // HANDLE
        )

        QueryFullProcessImageNameA = kernel32.getFunctionAddress("QueryFullProcessImageNameA")
        QueryFullProcessImageNameACIF = allocateCIF(
            abi,
            LibFFI.ffi_type_uint8, // return bool

            LibFFI.ffi_type_uint32, // HANDLE
            LibFFI.ffi_type_uint32, // DWORD
            LibFFI.ffi_type_pointer, // LPSTR
            LibFFI.ffi_type_pointer, // PDWORD
        )
    }

    override fun findRunningInstanceDat(): Path? {
        var result: Path? = null

        windowEnumTarget = callback@{ hwnd ->
            val className = MemoryStack.stackPush().use { mem ->
                val returnResult = mem.calloc(4)

                val classNameBuf = mem.calloc(64)

                val argsBuf = mem.pointers(
                    MemoryUtil.memAddress(mem.ints(hwnd)),
                    mem.pointers(classNameBuf).address(),
                    MemoryUtil.memAddress(mem.ints(classNameBuf.remaining()))
                )

                LibFFI.ffi_call(GetClassNameACIF, GetClassNameA, returnResult, argsBuf)

                val classNameBytes = ByteArray(returnResult.getInt(0))
                classNameBuf.get(classNameBytes)
                String(classNameBytes, Charsets.UTF_8)
            }

            // SIL window classes start with 'SILWindowClass.' and then a random number
            // (this is why we can't use FindWindowA)
            // Do this filtering to avoid poking around in every process, as
            // that's just asking for an antivirus false positive.
            if (!className.startsWith("SILWindowClass."))
                return@callback

            // Find the process ID
            val pid = MemoryStack.stackPush().use { mem ->
                val returnResult = mem.calloc(4)
                val pid = mem.calloc(4)

                val argsBuf = mem.pointers(
                    MemoryUtil.memAddress(mem.ints(hwnd)),
                    mem.pointers(pid).address()
                )

                LibFFI.ffi_call(GetWindowThreadProcessIdCIF, GetWindowThreadProcessId, returnResult, argsBuf)

                val threadId = returnResult.getInt(0)
                if (threadId == 0) {
                    println("Found FTL window, but couldn't find it's process ID: err=${WinBase.GetLastError()}")
                    return@callback
                }

                pid.getInt(0)
            }

            // Open a process handle
            val handle = MemoryStack.stackPush().use { mem ->
                val returnResult = mem.calloc(4)
                val desiredAccess = 0x1000 // PROCESS_QUERY_LIMITED_INFORMATION

                val argsBuf = mem.pointers(
                    mem.ints(desiredAccess),
                    mem.ints(0),
                    mem.ints(pid),
                )

                LibFFI.ffi_call(OpenProcessCIF, OpenProcess, returnResult, argsBuf)
                returnResult.getInt(0)
            }

            // Find the image name
            val pathString: String = MemoryStack.stackPush().use { mem ->
                val returnResult = mem.calloc(4)

                val pathBuff = mem.calloc(256)
                val stringLen = mem.ints(pathBuff.remaining())

                val argsBuf = mem.pointers(
                    MemoryUtil.memAddress(mem.ints(handle)),
                    MemoryUtil.memAddress(mem.ints(0)),
                    mem.pointers(pathBuff).address(),
                    mem.pointers(stringLen).address(), // in/out
                )

                LibFFI.ffi_call(QueryFullProcessImageNameACIF, QueryFullProcessImageNameA, returnResult, argsBuf)

                if (returnResult.getInt(0) == 0) {
                    println("Found FTL window, but couldn't read the executable path: err=${WinBase.GetLastError()}")

                    // Don't return immediately, we still have to call CloseHandle
                    return@use ""
                }

                val bytes = ByteArray(stringLen.get(0))
                pathBuff.get(bytes)
                String(bytes, Charsets.UTF_8)
            }

            // Close the handle now we're done
            MemoryStack.stackPush().use { mem ->
                val returnResult = mem.calloc(4)
                val argsBuf = mem.pointers(mem.ints(handle))
                LibFFI.ffi_call(CloseHandleCIF, CloseHandle, returnResult, argsBuf)
            }

            // We used an empty path if QueryFullProcessImageNameA failed,
            // in which case we already printed an error.
            if (pathString == "") {
                return@callback
            }

            val path = Path.of(pathString)
            val datPath = path.parent.resolve("ftl.dat")

            if (!Files.isRegularFile(datPath)) {
                println("Found FTL window of class $className with PID $pid, path: '$pathString'")
                println(" ... but there was no ftl.dat file at '$datPath'")
                return@callback
            }

            result = datPath
        }

        // Call EnumWindows
        MemoryStack.stackPush().use { mem ->
            val returnResult = mem.calloc(4)

            val argsBuf = mem.pointers(
                mem.pointers(EnumWindowsProc.address()), // callback
                mem.pointers(0), // userdata, passed to callback
            )

            LibFFI.ffi_call(EnumWindowsCIF, EnumWindows, returnResult, argsBuf)

            val enumResult = returnResult.get(0).toInt()
            if (enumResult != 1) {
                println("Failed to enumerate windows (searching for FTL instance)")
                return null
            }
        }

        return result
    }

    private fun allocateCIF(abi: Int, returnType: FFIType, vararg args: FFIType): FFICIF {
        val cif = FFICIF.create()

        // argTypes isn't copied into the CIF, so it can't go on the stack.
        val argTypesPtr = MemoryUtil.getAllocator().calloc(args.size.toLong(), Pointer.POINTER_SIZE.toLong())
        val argTypes = PointerBuffer.create(argTypesPtr, args.size)

        for ((i, type) in args.withIndex()) {
            argTypes.put(i, type)
        }

        val prepResult = LibFFI.ffi_prep_cif(
            cif,
            abi,
            returnType,
            argTypes
        )
        check(prepResult == LibFFI.FFI_OK)

        return cif
    }
}

private class MacPlatform : PlatformSpecific {
    // The packaged game uses the usual place, since an app run from the
    // Finder is run in /. Otherwise, it's in the directory the game is run
    // from.
    override val saveGamePath: Path = when (isPackagedGame()) {
        true -> Path.of(System.getProperty("user.home")).resolve("Library/Application Support/$PACKAGED_NAME")
        false -> Path.of("cache")
    }

    init {
        // Where older versions kept the saves
        if (!isPackagedGame())
            moveOldSavesFromRepo()
    }

    private fun moveOldSavesFromRepo() {
        moveOldSaves(Path.of("fixme-wormhole-saves"), saveGamePath)
        moveOldSaves(
            Path.of(System.getProperty("user.home")).resolve("Library/Application Support/Project Wormhole"),
            saveGamePath
        )
    }

    override fun findRunningInstanceDat(): Path? {
        // TODO implement
        return null
    }
}

private class LinuxPlatform : PlatformSpecific {
    override val saveGamePath: Path

    init {
        val dataHome = System.getenv("XDG_DATA_HOME")?.let { Path.of(it) }
            ?: Path.of(System.getProperty("user.home")).resolve(".local/share")

        saveGamePath = dataHome.resolve(if (isPackagedGame()) PACKAGED_NAME else "ProjectWormhole")
    }

    override fun findRunningInstanceDat(): Path? {
        // Dig through /proc
        for (item in Files.list(Path.of("/proc"))) {
            if (!Files.isDirectory(item))
                continue

            // Ignore everything except process directories, which
            // are named from their PID.
            if (item.fileName.toString().toIntOrNull() == null)
                continue

            val exePath = try {
                Files.readSymbolicLink(item.resolve("exe"))
            } catch (_: Exception) {
                // We don't have permission to look at this on many processes
                continue
            }

            val exeName = exePath.fileName.toString()
            if (exeName != "FTL.amd64" && exeName != "FTL.x86")
                continue

            // Found it!
            val datPath = exePath.parent.resolve("ftl.dat")
            if (Files.isRegularFile(datPath))
                return datPath
        }

        return null
    }
}

/**
 * Move the saves from where an older version kept them, if there are any and
 * there's nothing in the new place yet. Mods in the mods order file which
 * were relative to the old mods directory are changed to absolute paths, so
 * they still load.
 */
private fun moveOldSaves(oldDir: Path, newDir: Path) {
    if (!Files.isDirectory(oldDir) || Files.exists(newDir))
        return

    val oldModsDir = oldDir.resolve("mods").toAbsolutePath()
    try {
        // The new directory may be relative, without a parent
        newDir.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        Files.move(oldDir, newDir)
    } catch (ex: Exception) {
        System.err.println("Failed to move the saves from '$oldDir' to '$newDir': ${ex.localizedMessage}")
        return
    }
    println("Moved the saves from '${oldDir.toAbsolutePath()}' to '$newDir'")

    val orderFile = newDir.resolve("mods/order.txt")
    if (!Files.isRegularFile(orderFile))
        return
    try {
        val lines = Files.readAllLines(orderFile).map { line ->
            if (line.isBlank() || line.startsWith("//") || Path.of(line).isAbsolute)
                return@map line
            oldModsDir.resolve(line).normalize().toString()
        }
        Files.write(orderFile, lines)
    } catch (ex: Exception) {
        System.err.println("Failed to update the mods in '$orderFile': ${ex.localizedMessage}")
    }
}

/**
 * The name the packaged game's saves directory has.
 */
private const val PACKAGED_NAME = "Chonkestrel"

private fun isPackagedGame(): Boolean = System.getProperty("xftl.packaged").toBoolean()

/**
 * The packaged game (made by tools/release/package.py), as opposed to the game run
 * from the repo. It's packaged with Java, the Chonkestrel mod and some
 * settings, as system properties.
 */
object PackagedGame {
    /**
     * Whether this is the packaged game. It keeps its saves in the usual
     * place for the OS, under the game's name.
     */
    val isPackaged: Boolean get() = isPackagedGame()

    /**
     * The packaged game's own mods, which it uses unless the player has set
     * up their own (see Datafile.loadWithMods).
     */
    val bundledModsDirectory: Path?
        get() = System.getProperty("xftl.bundled-mods")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
}
