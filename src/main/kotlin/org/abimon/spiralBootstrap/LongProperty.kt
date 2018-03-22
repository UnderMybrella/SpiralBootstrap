package org.abimon.spiralBootstrap

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class LongProperty<out T: Any>(initializer: () -> T) : ReadOnlyProperty<Any?, T> {
    @Volatile private lateinit var value: T

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = runBlocking {
        while(!::value.isInitialized)
            delay(100)

        return@runBlocking value
    }

    init {
        launchCoroutine {
            value = initializer()
        }
    }
}