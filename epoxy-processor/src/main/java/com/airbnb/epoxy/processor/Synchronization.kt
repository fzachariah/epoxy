package com.airbnb.epoxy.processor

import com.sun.tools.javac.code.Symbol
import com.sun.tools.javac.code.Type
import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

class Mutex

var synchronizationEnabled = false
private val mutexMap = mutableMapOf<Any, Mutex>()

@Synchronized
fun Any.mutex() = mutexMap.getOrPut(this) { Mutex() }

inline fun <R> synchronizedByValue(value: Any, block: () -> R): R {
    return if (synchronizationEnabled) {
        synchronized(value.mutex(), block)
    } else {
        block()
    }
}

val typeLookupMutex = Mutex()
inline fun <R> synchronizedForTypeLookup(block: () -> R): R {
    return if (synchronizationEnabled) {
        synchronized(typeLookupMutex, block)
    } else {
        block()
    }
}

fun <T : Element> T.ensureLoaded(): T {
    if (!synchronizationEnabled || this !is Symbol) return this

    val completer = completer ?: return this

    synchronized(completer) {
        complete()
    }

    return this
}

fun <T : TypeMirror> T.ensureLoaded(): T {
    if (this !is Type) return this

    val completer = tsym?.completer ?: return this

    synchronized(completer) {
        complete()
    }

    return this
}