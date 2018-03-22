package org.abimon.spiralBootstrap.view

import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.ButtonType
import javafx.scene.input.KeyCode
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority
import javafx.stage.DirectoryChooser
import javafx.stage.Modality
import javafx.stage.Stage
import org.abimon.spiral.core.objects.archives.WAD
import org.abimon.spiral.core.utils.copyWithProgress
import org.abimon.spiralBootstrap.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import kotlin.system.measureNanoTime


class JavaFxView : Application() {
    companion object {
        val DATA_WAD_NAME = "data.wad"
        val PATCH_WAD_NAME = "patch.wad"

        val LOCALISED_WAD_NAMES = arrayOf("data_us.wad", "data_ch.wad", "data_jp.wad")

        val WAD_COMPILER_NAME = "wad_compiler.txt"

        val WAD_BUTTON_TYPES = HashMap<String, ButtonType>()
        val DO_NOT_BACKUP = HashSet<String>()

        val SHA_512_REGEX = "[a-fA-F0-9]{128}".toRegex()

        val BYTES = arrayOf(
                "T" to 1000L * 1000 * 1000 * 1000,
                "G" to 1000L * 1000 * 1000,
                "M" to 1000L * 1000,
                "K" to 1000L
        )
    }

    val width = 280.0
    val height = 300.0
    val root = GridPane()
    val scene: Scene = Scene(root)
    lateinit var primaryStage: Stage

    override fun start(primaryStage: Stage) {
        this.primaryStage = primaryStage

        root.hgap = 8.0
        root.vgap = 8.0
        root.padding = Insets(5.0)

//        val cons1 = ColumnConstraints()
//        cons1.hgrow = Priority.NEVER

        for (i in 0 until 1) {
            val column = ColumnConstraints()
            column.hgrow = Priority.ALWAYS
            root.columnConstraints.add(column)
        }

//        val rcons1 = RowConstraints()
//        rcons1.vgrow = Priority.NEVER

//        val rcons2 = RowConstraints()
//        rcons2.vgrow = Priority.ALWAYS
//
//        root.getRowConstraints().add(rcons2)

        val label = Label("SPIRAL Bootstrapper")
        val labelSeparator = Separator()
        val selectInstallDir = Button("Select Installation Directory")
        val install = Button("Install")
        val closeSeparator = Separator()
        val close = Button("Close")

        selectInstallDir.setOnAction { event ->
            val selector = DirectoryChooser()
            val selectedDir = selector.showDialog(scene.window)
            launchCoroutine {
                if (selectedDir != null) {
                    val allSubfiles = selectedDir.iterate()
                    val dataWadFile = allSubfiles.firstOrNull { file -> file.name == "dr2_$DATA_WAD_NAME" }
                            ?: allSubfiles.firstOrNull { file -> file.name == "dr1_$DATA_WAD_NAME" }

                    if (dataWadFile != null) {
                        checkForAndBackup(dataWadFile)

                        val game = dataWadFile.name.substringBefore('_')
                        val patchWadFile = allSubfiles.firstOrNull { file -> file.name == "${game}_$PATCH_WAD_NAME" }
                        val localisedWadFile = allSubfiles.firstOrNull { file -> LOCALISED_WAD_NAMES.any { lang -> file.name == "${game}_$lang" } }

                        val patchWad = patchWadFile?.let { file -> WAD { FileInputStream(file) } }
                        val localisedWad = localisedWadFile?.let { file -> WAD { FileInputStream(file) } }
                        if (patchWad != null) {
                            checkForAndBackup(patchWadFile)

                            println("Patch WAD present at $patchWadFile")

                            //We already have a patch wad, so we need to do some checks
                            if (localisedWad != null) {
                                checkForAndBackup(localisedWadFile)

                                println("Patch WAD -> Localised WAD present at $localisedWadFile")
                                //We also have a localised wad, so we need to check if we compiled the patch file, and if not switch

                                val compilerVersionPatch = patchWad.files.firstOrNull { entry -> entry.name == WAD_COMPILER_NAME }
                                val compilerVersionLocalised = localisedWad.files.firstOrNull { entry -> entry.name == WAD_COMPILER_NAME }
                                val spiralCompiledPatchWad = compilerVersionPatch?.let { entry -> entry.inputStream.use { stream -> String(stream.readBytes(), Charsets.UTF_8) } }?.matches(SHA_512_REGEX) == true
                                val spiralCompiledLocalisedWad = compilerVersionLocalised?.let { entry -> entry.inputStream.use { stream -> String(stream.readBytes(), Charsets.UTF_8) } }?.matches(SHA_512_REGEX) == true

                                if (spiralCompiledPatchWad) {
                                    if (!spiralCompiledLocalisedWad) {

                                    }
                                }
                            } else {
                                println("No localised WAD present; we'll probably need a fresh compile")
                            }
                        }

                        return@launchCoroutine
                    }

                    runOnJavaFX {
                        val alert = Alert(AlertType.ERROR, "The directory you selected does not contain either a DR1 or DR2 installation", ButtonType.OK)
                        alert.showAndWait()
                    }
                }
            }
        }
        close.setOnAction { event -> Platform.exit() }

        GridPane.setHalignment(label, HPos.CENTER)
        GridPane.setHalignment(selectInstallDir, HPos.CENTER)
        GridPane.setHalignment(install, HPos.CENTER)
        GridPane.setHalignment(close, HPos.CENTER)

        root.add(label, 0, 0)
        root.add(labelSeparator, 0, 1)
        root.add(selectInstallDir, 0, 2)
        root.add(install, 0, 3)
        root.add(closeSeparator, 0, 4)
        root.add(close, 0, 5)

        //root.isGridLinesVisible = true
        primaryStage.scene = scene
        primaryStage.show()
    }

    fun selectionTest() {
        val lbl = Label("Name:")
        val field = TextField()
        val view = ListView<String>()
        val okBtn = Button("OK")
        val closeBtn = Button("Close")

        view.items.addAll(arrayOf("Danganronpa: Trigger Happy Havoc", "Danganronpa 2: Goodbye Despair", "Danganronpa V3: Killing Harmony"))

        field.setOnKeyPressed { event ->
            if (event.code == KeyCode.ENTER) {
                val text = field.text
                view.items.add(text)
                field.text = ""
            }
        }

        okBtn.setOnAction { event ->
            println(view.selectionModel.selectedItems)
        }

        closeBtn.setOnAction { event ->
            Platform.exit()
        }

        GridPane.setHalignment(okBtn, HPos.RIGHT)

        root.add(lbl, 0, 0)
        root.add(field, 1, 0, 3, 1)
        root.add(view, 0, 1, 4, 2)
        root.add(okBtn, 2, 3)
        root.add(closeBtn, 3, 3)
    }

    fun checkForAndBackup(file: File) {
        val backupFile = File(file.absolutePath + ".backup")

        if (file.absolutePath in DO_NOT_BACKUP)
            return

        if (backupFile.exists() && (backupFile.length() > 0 && file.length() > 0))
            return

        var shouldWeBackup: Optional<ButtonType> = Optional.empty()
        runOnJavaFX {
            val shouldBackup = Alert(AlertType.CONFIRMATION, "No backup file detected for ${file.name}; should we back it up?", ButtonType.YES, ButtonType.NO)
            shouldWeBackup = shouldBackup.showAndWait()
        }

        if (shouldWeBackup.isPresent) {
            val buttonPressed = shouldWeBackup.get()
            when (buttonPressed) {
                ButtonType.YES -> {
                    val popup = GridPane()
                    val popupScene: Scene = Scene(popup, ColorFX.rgb(0, 0, 0, 0.0))

                    popup.hgap = 8.0
                    popup.vgap = 8.0
                    popup.padding = Insets(5.0)

                    for (i in 0 until 2) {
                        val column = ColumnConstraints()
                        column.hgrow = Priority.ALWAYS
                        popup.columnConstraints.add(column)
                    }

                    val dialog: Stage = run {
                        var tmpDialog: Stage? = null

                        runOnJavaFX {
                            tmpDialog = Stage()
                            tmpDialog?.initModality(Modality.APPLICATION_MODAL)
                            tmpDialog?.initOwner(primaryStage)
                        }

                        return@run tmpDialog!!
                    }

                    val label = Label("Backing up ${file.name} (${file.length().formatAsBytes()})")

                    val progressBar = ProgressBar(0.0)
                    val progressIndicator = ProgressIndicator(-1.0)

                    val finishButton = Button("Backing Up...")
                    finishButton.disable = true

                    GridPane.setHalignment(progressBar, HPos.LEFT)
                    GridPane.setHalignment(finishButton, HPos.LEFT)

                    popup.add(label, 0, 0, 2, 1)
                    popup.add(progressBar, 0, 1)
                    popup.add(progressIndicator, 1, 1, 1, 2)
                    popup.add(finishButton, 0, 2)


                    runOnJavaFX {
                        dialog.scene = popupScene
                        dialog.show()
                    }

                    val fileSize = file.length()

                    val timeTaken = measureNanoTime {
                        FileInputStream(file).use { inStream ->
                            FileOutputStream(backupFile).use { outStream ->
                                inStream.copyWithProgress(outStream) { copied ->
                                    runOnJavaFX {
                                        progressBar.progress = copied.toDouble() / fileSize
                                        progressIndicator.progress = copied.toDouble() / fileSize
                                    }
                                }
                            }
                        }
                    }

                    println("Backing up $file; Finished in $timeTaken ns")

                    runOnJavaFX {
                        finishButton.disable = false
                        finishButton.text = "Finish"
                    }

                    finishButton.waitForAction()

                    runOnJavaFX {
                        backupFile.delete()
                        dialog.close()
                    }
                }
                ButtonType.NO -> DO_NOT_BACKUP.add(file.absolutePath)
                else -> println("ERR: $buttonPressed is not a valid button?")
            }
        }
    }

    fun Long.formatAsBytes(): String {
        for ((prefix, bytesPer) in BYTES) {
            val whole = this / bytesPer
            if (whole > 0)
                return "$whole.${(((this / (bytesPer / 1000)) % 1000) / 10).toString().padStart(2, '0')} ${prefix}B"
        }

        return "$this B"
    }
}