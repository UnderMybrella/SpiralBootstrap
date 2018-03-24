package org.abimon.spiralBootstrap.view

import com.github.kittinunf.fuel.Fuel
import com.google.gson.JsonParseException
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
import org.abimon.spiral.core.objects.customWAD
import org.abimon.spiral.core.utils.copyWithProgress
import org.abimon.spiralBootstrap.*
import java.io.*
import java.util.*
import java.util.zip.ZipFile
import kotlin.system.measureNanoTime


class JavaFxView : Application() {
    companion object {
        val DATA_WAD_NAME = "data.wad"
        val PATCH_WAD_NAME = "patch.wad"
        val KEYBOARD_WAD_NAME = "data_keyboard.wad"

        val LOCALISED_WAD_NAMES = arrayOf("data_us.wad", "data_ch.wad", "data_jp.wad")
        val LOCALISED_KEYBOARD_WAD_NAMES = arrayOf("data_keyboard_us.wad", "data_keyboard_ch.wad", "data_keyboard_jp.wad")

        val WAD_COMPILER_NAME = "wad_compiler.txt"

        val WAD_BUTTON_TYPES = HashMap<String, ButtonType>()
        val DO_NOT_BACKUP = HashSet<String>()

        val SHA_512_REGEX = "[a-fA-F0-9]{128}".toRegex()

        val LOGO_PATH = "DrCommon/data/all/cg/aglogo.tga"
        val DEFAULT_LOGO = "75f61bdd4151b707faa72e7c914f22a5a919feaf4b99ee1f4c1b0d7d49fe814ad68982f4a46858fcb19005af5f70fb8476b38d40ed4c7e3fc7cbb51a20b7e691"
        val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.13; rv:59.0) Gecko/20100101 Firefox/59.0"

        val BYTES = arrayOf(
                "T" to 1000L * 1000 * 1000 * 1000,
                "G" to 1000L * 1000 * 1000,
                "M" to 1000L * 1000,
                "K" to 1000L
        )
    }

    val mod: SpiralMod
    val modFile: File

    val width = 280.0
    val height = 300.0
    val root = GridPane()
    val scene: Scene = Scene(root)
    lateinit var primaryStage: Stage
    lateinit var modInstallationFile: File

    var modKeyboardFile: File? = null
    var modKeyboardLocalisationFile: File? = null

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

        val label = Label("Installer for \"${mod.name}\"")
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
                        val game = dataWadFile.name.substringBefore('_')
                        if (mod.requiredGame != null && mod.requiredGame?.toUpperCase() != game.toUpperCase()) {
                            runOnJavaFX {
                                val alert = Alert(AlertType.ERROR, "\"${mod.name}\" requires ${mod.requiredGame?.toUpperCase()}, and you selected ${game.toUpperCase()}.\nPlease select the correct installation.", ButtonType.OK)
                                alert.showAndWait()
                            }

                            return@launchCoroutine
                        }

                        checkForAndBackup(dataWadFile)

                        //TODO: Change how localised files are detected, because this will break if there are different language wads
                        val patchWadFile = allSubfiles.firstOrNull { file -> file.name == "${game}_$PATCH_WAD_NAME" }
                        val localisedWadFile = allSubfiles.firstOrNull { file -> LOCALISED_WAD_NAMES.any { lang -> file.name == "${game}_$lang" } }

                        val keyboardWadFile = allSubfiles.firstOrNull { file -> file.name == "${game}_$KEYBOARD_WAD_NAME" }
                        val localisedKeyboardWadFile = allSubfiles.firstOrNull { file -> LOCALISED_KEYBOARD_WAD_NAMES.any { lang -> file.name == "${game}_$lang" } }

                        val patchWad = patchWadFile?.let { file -> WAD { FileInputStream(file) } }
                        val localisedWad = localisedWadFile?.let { file -> WAD { FileInputStream(file) } }

                        if (keyboardWadFile != null)
                            checkForAndBackup(keyboardWadFile)

                        if (localisedKeyboardWadFile != null)
                            checkForAndBackup(localisedKeyboardWadFile)

                        if (patchWad != null) {
                            checkForAndBackup(patchWadFile)

                            println("Patch WAD present at $patchWadFile")

                            //We already have a patch wad, so we need to do some checks
                            if (localisedWad != null) {
                                checkForAndBackup(localisedWadFile)

                                println("Patch WAD -> Localised WAD present at $localisedWadFile")
                                //We also have a localised wad, so we need to check if we compiled it, and if not switch

                                val compilerVersionLocalised = localisedWad.files.firstOrNull { entry -> entry.name == WAD_COMPILER_NAME }

                                val spiralCompiledLocalisedWad = compilerVersionLocalised?.let { entry -> entry.inputStream.use { stream -> String(stream.readBytes(), Charsets.UTF_8) } }?.matches(SHA_512_REGEX) == true

                                if (!spiralCompiledLocalisedWad) {
                                    println("Patch WAD -> Localisation WAD -> Not compiled by us")
                                    //The localised wad is all we care about; if it wasn't compiled by us we switch it
                                    val tmp = File("${UUID.randomUUID()}.wad")
                                    tmp.deleteOnExit()

                                    patchWadFile.renameTo(tmp)
                                    localisedWadFile.renameTo(patchWadFile)
                                    tmp.renameTo(localisedWadFile)

                                    tmp.delete()
                                }

                                //At this point, we can use the localised wad file as our installation point
                                println()
                                modInstallationFile = localisedWadFile
                                modKeyboardFile = keyboardWadFile
                                modKeyboardLocalisationFile = localisedKeyboardWadFile

                                runOnJavaFX { Alert(AlertType.INFORMATION, "Installation directory set").showAndWait() }
                            } else {
                                //If we have a patch wad, but no localised wad, ask the user about whether they have an old game

                                var selectedOption = Optional.empty<ButtonType>()
                                runOnJavaFX {
                                    val alert = Alert(AlertType.CONFIRMATION, "No localisation wad could be found (${game}_data_us).\nDo you have an older copy of the game?", ButtonType.YES, ButtonType.NO, ButtonType.CANCEL)
                                    alert.buttonTypes.sorted()
                                    selectedOption = alert.showAndWait()
                                }

                                val option = selectedOption.orElse(ButtonType.CANCEL)

                                when (option) {
                                    ButtonType.YES -> {
                                        //The user has an older copy of the game, so we should use the main data wad
                                        //TODO: Check if older copies can load the patch file

                                        modInstallationFile = dataWadFile
                                        modKeyboardFile = keyboardWadFile
                                        modKeyboardLocalisationFile = localisedKeyboardWadFile
                                        runOnJavaFX { Alert(AlertType.INFORMATION, "Installation directory set").showAndWait() }
                                    }
                                    ButtonType.NO -> {
                                        //The user has a current copy of the game, so we should compile and use a localisation wad

                                        val emptyWad = customWAD {
                                            major = 1
                                            minor = 1

                                            add(WAD_COMPILER_NAME, Bootstrap.versionBytes.size.toLong()) { ByteArrayInputStream(Bootstrap.versionBytes) }
                                        }

                                        var languageOptional: Optional<ButtonType> = Optional.empty()
                                        val englishButton = getButtonForName("US", ButtonBar.ButtonData.OTHER)
                                        val japaneseButton = getButtonForName("JP", ButtonBar.ButtonData.OTHER)
                                        val traditionalChineseButton = getButtonForName("CH", ButtonBar.ButtonData.OTHER)

                                        runOnJavaFX {
                                            val languageAlert = Alert(AlertType.CONFIRMATION, "Please select your language: \n* English (US)\n* Japanese (JP)\n* Traditional Chinese (CH)", englishButton, japaneseButton, traditionalChineseButton)
                                            languageOptional = languageAlert.showAndWait()
                                        }

                                        val suffix = languageOptional.map { button ->
                                            when (button) {
                                                englishButton -> return@map "us"
                                                japaneseButton -> return@map "jp"
                                                traditionalChineseButton -> return@map "ch"
                                                else -> return@map null
                                            }
                                        }.orElse(null) ?: return@launchCoroutine

                                        val resultingFile = File(dataWadFile.parentFile, "${game}_data_$suffix.wad") //TODO: Ask user for language
                                        FileOutputStream(resultingFile).use(emptyWad::compile)

                                        modInstallationFile = resultingFile
                                        modKeyboardFile = keyboardWadFile
                                        modKeyboardLocalisationFile = localisedKeyboardWadFile

                                        runOnJavaFX { Alert(AlertType.INFORMATION, "Installation directory set").showAndWait() }
                                    }
                                    ButtonType.CANCEL -> return@launchCoroutine
                                    else -> println("ERR: $option is not a valid button?")
                                }
                            }
                        } else {
                            //No patch wad means that we're likely new to this installation thing

                            if (localisedWadFile != null) {
                                //Fortunately, if we have a localised wad file, it's dead easy; rename them and move on
                                checkForAndBackup(localisedWadFile)

                                localisedWadFile.renameTo(File(localisedWadFile.parent, "${game}_$PATCH_WAD_NAME")) //Same name for both games thank god

                                //Then, we compile a small empty WAD to the localisation file
                                val emptyWad = customWAD {
                                    major = 1
                                    minor = 1

                                    add(WAD_COMPILER_NAME, Bootstrap.versionBytes.size.toLong()) { ByteArrayInputStream(Bootstrap.versionBytes) }
                                }
                                FileOutputStream(localisedWadFile).use(emptyWad::compile)

                                modInstallationFile = localisedWadFile
                                modKeyboardFile = keyboardWadFile
                                modKeyboardLocalisationFile = localisedKeyboardWadFile

                                runOnJavaFX { Alert(AlertType.INFORMATION, "Installation directory set").showAndWait() }
                            } else {
                                //This is the worst timeline; we have neither a patch wad or a localisation wad.
                                //It's practically guaranteed that this is an old copy of the game, and it's unknown if that can load a patch wad file
                                //Therefore, we get to do the clunky load and compile
                                //TODO: Check if older copies can load the patch file

                                modInstallationFile = dataWadFile
                                modKeyboardFile = keyboardWadFile
                                modKeyboardLocalisationFile = localisedKeyboardWadFile
                                runOnJavaFX { Alert(AlertType.INFORMATION, "Installation directory set").showAndWait() }
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
        install.setOnAction { event ->
            if (!::modInstallationFile.isInitialized) {
                val alert = Alert(AlertType.WARNING, "You haven't selected a directory to install to")
                alert.showAndWait()

                return@setOnAction
            }

            launchCoroutine {
                val downloadSize = if (modFile.exists()) -1 else run {
                    val (_, response) = Fuel.head(mod.zipUrl).header("User-Agent" to USER_AGENT).response()

                    return@run response.contentLength
                }

                runOnJavaFX {
                    val confirmAlert = Alert(AlertType.CONFIRMATION, buildString {
                        append("Installing ")
                        appendln(mod.name)

                        append("To ")
                        appendln(modInstallationFile.name)

                        if(downloadSize == -1L) {
                            append("Mod Size (Local): ")
                            appendln(modFile.length().formatAsBytes())
                        } else {
                            append("Mod Size (Download): ")
                            appendln(downloadSize.formatAsBytes())
                        }
                    })

                    val response = confirmAlert.showAndWait()
                    response.ifPresent { button ->
                        if (button == ButtonType.OK) {
                            launchCoroutine { compile() }
                        }
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

    fun compile() {
        if (!modFile.exists()) {
            val (_, headResponse) = Fuel.head(mod.zipUrl).header("User-Agent" to USER_AGENT).response()

            val popup = GridPane()
            val popupScene: Scene = Scene(popup)

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

            val downloadSize = headResponse.contentLength.toDouble()
            val label = Label("Downloading ${mod.name} (${headResponse.contentLength.formatAsBytes()})")

            val progressBar = ProgressBar(0.0)
            val progressIndicator = ProgressIndicator(-1.0)

            val finishButton = Button("Downloading...")
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

            val timeTaken = measureNanoTime {
                Fuel.download(mod.zipUrl).header("User-Agent" to USER_AGENT).destination { _, _ -> modFile }.progress { readBytes, totalBytes ->
                    runOnJavaFX {
                        progressBar.progress = readBytes.toDouble() / totalBytes.toDouble()
                        progressIndicator.progress = readBytes.toDouble() / totalBytes.toDouble()
                    }
                }.response()
            }

            println("Downloaded ${mod.zipUrl}; Finished in $timeTaken ns")

            runOnJavaFX {
                finishButton.disable = false
                finishButton.text = "Finish"
            }

            finishButton.waitForAction()

            runOnJavaFX { dialog.close() }
        }

        if (!modFile.exists()) {
            runOnJavaFX {
                val error = Alert(AlertType.ERROR, "No mod file is present, did the download fail?")
                error.showAndWait()
            }

            return
        }

        val tmpWad = File("${UUID.randomUUID()}.wad")
        tmpWad.deleteOnExit()
        modInstallationFile.renameTo(tmpWad)

        val wad = WAD { FileInputStream(tmpWad) }
        if (wad == null) {
            tmpWad.renameTo(modInstallationFile)

            runOnJavaFX {
                val error = Alert(AlertType.ERROR, "${modInstallationFile.name} is not a wad file; something has gone very wrong")
                error.showAndWait()
            }

            return
        }

        val tmpKeyboardWad = modKeyboardFile?.let { File("${UUID.randomUUID()}.wad") }
        tmpKeyboardWad?.deleteOnExit()
        modKeyboardFile?.renameTo(tmpKeyboardWad)

        val keyboardWad = tmpKeyboardWad?.let { file -> WAD { FileInputStream(file) } }

        val tmpKeyboardLocalisedWad = modKeyboardLocalisationFile?.let { File("${UUID.randomUUID()}.wad") }
        tmpKeyboardLocalisedWad?.deleteOnExit()
        modKeyboardLocalisationFile?.renameTo(tmpKeyboardLocalisedWad)

        val localisedKeyboardWad = tmpKeyboardLocalisedWad?.let { file -> WAD { FileInputStream(file) } }

        val introTmp = File("${UUID.randomUUID()}.tga")
        introTmp.deleteOnExit()

        try {
            val zipFile = ZipFile(modFile)
            val entries = zipFile.entries().toList()

            val custom = customWAD {
                add(wad) //Base game stuff

                if (LOGO_PATH !in files || files[LOGO_PATH]!!.second().use { stream -> stream.hash("SHA-512") == DEFAULT_LOGO }) {
                    val spiralIntro = Bootstrap::class.java.getResourceAsStream("/aglogo.tga")
                    if (spiralIntro != null) {
                        FileOutputStream(introTmp).use { out -> spiralIntro.use { stream -> stream.copyTo(out) } }

                        add(LOGO_PATH, introTmp) //Compile the SPIRAL logo as a base asset; if the installed mod wants to override it then that's fine
                        println("Added SPIRAL Logo")
                    } else {
                        println("Went to add SPIRAL Logo, but it's not present")
                    }
                } else {
                    println("Custom Logo in use")
                }

                //Add all the new entries from the mod
                entries.forEach { entry ->
                    add(entry.name, entry.size) { zipFile.getInputStream(entry) }
                }

                add(WAD_COMPILER_NAME, Bootstrap.versionBytes.size.toLong()) { ByteArrayInputStream(Bootstrap.versionBytes) }
            }

            val customKeyboardWad = keyboardWad?.let { kbWad ->
                if (entries.none { entry -> kbWad.files.any { kbEntry -> kbEntry.name == entry.name } })
                    return@let null

                customWAD {
                    add(kbWad)

                    // *ONLY* add entries that are already there
                    entries.forEach { entry ->
                        if(entry.name in files)
                            add(entry.name, entry.size) { zipFile.getInputStream(entry) }
                    }
                }
            }

            val customLocalisedKeyboardWad = localisedKeyboardWad?.let { kbWad ->
                if (entries.none { entry -> kbWad.files.any { kbEntry -> kbEntry.name == entry.name } })
                    return@let null

                customWAD {
                    add(kbWad)

                    entries.forEach { entry ->
                        if(entry.name in files)
                            add(entry.name, entry.size) { zipFile.getInputStream(entry) }
                    }
                }
            }

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

            val numberOfWadEntries = custom.files.size.toDouble()
            val numberOfKeyboardEntries = customKeyboardWad?.files?.size?.toDouble() ?: 0.0
            val numberOfLocalisedKeyboardEntries = customLocalisedKeyboardWad?.files?.size?.toDouble() ?: 0.0
            val numberOfEntriesTotal =  + (customKeyboardWad?.files?.size?.toDouble() ?: 0.0) + (customLocalisedKeyboardWad?.files?.size?.toDouble() ?: 0.0)
            val label = Label("Installing ${mod.name} (${numberOfEntriesTotal.toInt()} entries to compile)")

            val progressBar = ProgressBar(0.0)
            val progressIndicator = ProgressIndicator(-1.0)

            val finishButton = Button("Compiling...")
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

            val timeTaken = measureNanoTime {
                FileOutputStream(modInstallationFile).use { outStream ->
                    custom.compileWithProgress(outStream) { _, index ->
                        runOnJavaFX {
                            progressBar.progress = (index + 1).toDouble() / numberOfEntriesTotal
                            progressIndicator.progress = (index + 1).toDouble() / numberOfEntriesTotal
                        }
                    }
                }

                if (customKeyboardWad != null) {
                    FileOutputStream(modKeyboardFile).use { outStream ->
                        customKeyboardWad.compileWithProgress(outStream) { _, index ->
                            runOnJavaFX {
                                progressBar.progress = (index + 1 + numberOfWadEntries) / numberOfEntriesTotal
                                progressIndicator.progress = (index + 1 + numberOfWadEntries) / numberOfEntriesTotal
                            }
                        }
                    }
                }

                if (customLocalisedKeyboardWad != null) {
                    FileOutputStream(modKeyboardLocalisationFile).use { outStream ->
                        customLocalisedKeyboardWad.compileWithProgress(outStream) { _, index ->
                            runOnJavaFX {
                                progressBar.progress = (index + 1 + numberOfWadEntries + numberOfKeyboardEntries) / numberOfEntriesTotal
                                progressIndicator.progress = (index + 1 + numberOfWadEntries + numberOfKeyboardEntries) / numberOfEntriesTotal
                            }
                        }
                    }
                }
            }

            println("Installing to $modInstallationFile; Finished in $timeTaken ns")

            runOnJavaFX {
                finishButton.disable = false
                finishButton.text = "Finish"
            }

            finishButton.waitForAction()

            runOnJavaFX { dialog.close() }
        } catch (th: Throwable) {
            th.printStackTrace()

            modInstallationFile.delete()
            tmpWad.renameTo(modInstallationFile)

            modKeyboardFile?.delete()
            tmpKeyboardWad?.renameTo(modKeyboardFile)

            modKeyboardLocalisationFile?.delete()
            tmpKeyboardLocalisedWad?.renameTo(modKeyboardLocalisationFile)

            runOnJavaFX {
                val errorLog = File("Error-${System.currentTimeMillis()}.log")
                PrintStream(errorLog).use(th::printStackTrace)

                val error = Alert(AlertType.ERROR, "An error has occured and the installation has been cancelled.\nAn error log has been written to $errorLog, please report this to UnderMybrella")
                error.showAndWait()
            }
        } finally {
            introTmp.delete()
            tmpWad.delete()
            tmpKeyboardWad?.delete()
            tmpKeyboardLocalisedWad?.delete()
        }
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

                    runOnJavaFX { dialog.close() }
                }
                ButtonType.NO -> DO_NOT_BACKUP.add(file.absolutePath)
                else -> println("ERR: $buttonPressed is not a valid button?")
            }
        }
    }

    fun getButtonForName(name: String, type: ButtonBar.ButtonData): ButtonType {
        if(name !in WAD_BUTTON_TYPES)
            WAD_BUTTON_TYPES[name] = ButtonType(name, type)

        return WAD_BUTTON_TYPES[name]!!
    }

    fun Long.formatAsBytes(): String {
        for ((prefix, bytesPer) in BYTES) {
            val whole = this / bytesPer
            if (whole > 0)
                return "$whole.${(((this / (bytesPer / 1000)) % 1000) / 10).toString().padStart(2, '0')} ${prefix}B"
        }

        return "$this B"
    }

    init {
        try {
            val stream = Bootstrap::class.java.getResourceAsStream("/mod.json")
            if (stream == null) {
                val alert = Alert(AlertType.ERROR, "No mod config file found!")
                alert.showAndWait()

                System.err.println("No mod config file")
            }

            mod = InputStreamReader(stream).use { reader -> Bootstrap.gson.fromJson(reader, SpiralMod::class.java) }
            modFile = File("SPIRAL Mod - ${mod.name} v${mod.version}.zip")
        } catch (parse: JsonParseException) {
            val baos = ByteArrayOutputStream()
            val stream = PrintStream(baos)
            parse.printStackTrace(stream)

            val stackTrace = String(baos.toByteArray())

            val alert = Alert(AlertType.ERROR, stackTrace)
            alert.showAndWait()

            error(stackTrace)
        }
    }
}