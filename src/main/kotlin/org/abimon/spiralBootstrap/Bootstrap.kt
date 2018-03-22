package org.abimon.spiralBootstrap

import com.google.gson.GsonBuilder
import javafx.application.Application
import org.abimon.spiralBootstrap.view.JavaFxView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.util.*
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


object Bootstrap {
    var builder = GsonBuilder()
    var gson = builder.create()
    val version: String by longInitialiser { Bootstrap::class.java.protectionDomain.codeSource.location.openStream().use { stream -> stream.hash("SHA-512") } }

    val versionBytes: ByteArray by lazy { version.toByteArray(Charsets.UTF_8) }

    @JvmStatic
    fun main(args: Array<String>) {
        if(args.any { arg -> arg.startsWith("installer_for=") }) {
            val configFile = File(args.first { arg -> arg.startsWith("installer_for=") }.split('=', limit = 2).last())
            if (!configFile.exists())
                return System.err.println("ERR: $configFile does not exist")

            val config = FileReader(configFile).use { reader -> gson.fromJson(reader, SpiralMod::class.java) }
            val resultingFilename = args.firstOrNull { arg -> arg.startsWith("jar_name=") }?.split('=', limit = 2)?.last() ?: "${config.name}-Installer.jar"
            val tmpFile = File.createTempFile(UUID.randomUUID().toString(), ".jar")

            Bootstrap::class.java.protectionDomain.codeSource.location.openStream().use { stream ->
                FileOutputStream(tmpFile).use { out ->
                    stream.copyTo(out)
                }
            }

            val jarFile = JarFile(tmpFile)

            FileOutputStream(File(resultingFilename)).use { out ->
                val zipStream = ZipOutputStream(out)

                jarFile.entries().asSequence().forEach { entry ->
                    zipStream.putNextEntry(entry)
                    jarFile.getInputStream(entry).use { stream -> stream.copyTo(zipStream) }
                    zipStream.closeEntry()
                }

                zipStream.putNextEntry(ZipEntry("mod.json"))
                FileInputStream(configFile).use { stream -> stream.copyTo(zipStream) }
                zipStream.close()
            }

            return
        }

        if (System.getProperty("os.name").contains("OS X")) {
            System.setProperty("apple.laf.useScreenMenuBar", "true")
        }

        Application.launch(JavaFxView::class.java)
    }
}