package org.abimon.spiralBootstrap

import javafx.application.Application
import org.abimon.spiralBootstrap.view.JavaFxView

object Bootstrap {
    @JvmStatic
    fun main(args: Array<String>) {
        if (System.getProperty("os.name").contains("OS X")) {
            System.setProperty("apple.laf.useScreenMenuBar", "true")
        }

        Application.launch(JavaFxView::class.java)
    }
}