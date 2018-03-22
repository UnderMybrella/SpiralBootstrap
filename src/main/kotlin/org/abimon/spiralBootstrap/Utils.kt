package org.abimon.spiralBootstrap

import com.sun.javafx.application.PlatformImpl
import javafx.scene.control.Button
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.Job
import java.io.File
import java.io.FileFilter
import java.nio.file.Files
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.experimental.CoroutineContext

typealias ColorFX = javafx.scene.paint.Color

@Suppress("EXPECTED_DECLARATION_WITH_DEFAULT_PARAMETER")
public fun launchCoroutine(
        context: CoroutineContext = DefaultDispatcher,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        parent: Job? = null,
        block: suspend CoroutineScope.() -> Unit
): Job = kotlinx.coroutines.experimental.launch(context, start, parent, block)

fun File.iterate(includeDirs: Boolean = false, ignoreSymlinks: Boolean = true, filters: Array<FileFilter> = arrayOf(), maxDepth: Int = 10, depth: Int = 1): LinkedList<File> {
    val files = LinkedList<File>()

    if (isDirectory && listFiles() != null)
        for (f in listFiles()!!) {
            if(Files.isSymbolicLink(f.toPath()) && ignoreSymlinks)
                continue
            if(Files.isHidden(f.toPath()) || f.name.startsWith('.') || f.name.startsWith("__"))
                if(filters.any { filter -> !filter.accept(f) })
                    continue
            if (includeDirs || f.isFile)
                files.add(f)
            if (f.isDirectory)
                files.addAll(f.iterate(includeDirs, ignoreSymlinks, filters, maxDepth, depth + 1))
        }

    return files
}

var Button.disable: Boolean
    get() = isDisable
    set(value) { isDisable = value }

fun Button.waitForAction() {
    val wait = AtomicBoolean(true)

    this.setOnAction { wait.set(false) }

    while (wait.get()) Thread.sleep(100)
}

fun runOnJavaFX(runnable: Runnable) = PlatformImpl.runAndWait(runnable)
fun runOnJavaFX(block: () -> Unit) = PlatformImpl.runAndWait(block)