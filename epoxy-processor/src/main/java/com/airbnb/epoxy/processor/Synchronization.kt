package com.airbnb.epoxy.processor

import com.squareup.javapoet.JavaFile
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.OriginatingElementsHolder
import com.sun.tools.javac.code.Symbol
import com.sun.tools.javac.code.Type
import javax.annotation.processing.Filer
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.tools.StandardLocation
import kotlin.reflect.KClass

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

inline fun <R> synchronizedByElement(element: Element, block: () -> R): R {
    return if (synchronizationEnabled) {
        element.ensureLoaded()
        val name = if (element is TypeElement) element.qualifiedName else element.simpleName
        synchronized(name.mutex(), block)
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

    // already completed, can skip synchronization
    completer ?: return this

    synchronizedForTypeLookup {
        complete()
    }

    return this
}

fun <T : TypeMirror> T.ensureLoaded(): T {
    if (!synchronizationEnabled || this !is Type) return this

    tsym?.completer ?: return this

    synchronizedForTypeLookup {
        complete()
    }

    return this
}

val Element.enclosedElementsThreadSafe: List<Element>
    get() {
        return if (!synchronizationEnabled) {
            enclosedElements
        } else {
            ensureLoaded()
            synchronizedForTypeLookup {
                enclosedElements.onEach { it.ensureLoaded() }
            }
        }
    }

val ExecutableElement.parametersThreadSafe: List<VariableElement>
    get() {
        return if (!synchronizationEnabled || (this is Symbol.MethodSymbol && params != null)) {
            parameters
        } else {
            ensureLoaded()
            // After being initially loaded, parameters are lazily built into a list and stored
            // as a class field
            synchronizedForTypeLookup {
                parameters.onEach { it.ensureLoaded() }
            }
        }
    }

// Copied from javapoet and made threadsafe
fun JavaFile.writeSynchronized(filer: Filer) {
    val fileName =
        if (packageName.isEmpty()) typeSpec.name else packageName.toString() + "." + typeSpec.name
    val originatingElements = typeSpec.originatingElements

    // JavacFiler does not properly synchronize its "Set<FileObject> fileObjectHistory" field,
    // so parallel calls to createSourceFile can throw concurrent modification exceptions.
    val filerSourceFile = synchronized(filer) {
        filer.createSourceFile(
            fileName,
            *originatingElements.toTypedArray()
        )
    }

    try {
        filerSourceFile.openWriter().use { writer -> writeTo(writer) }
    } catch (e: Exception) {
        try {
            filerSourceFile.delete()
        } catch (ignored: Exception) {
        }
        throw e
    }
}

// Copied from kotlinpoet and made threadsafe
fun FileSpec.writeSynchronized(filer: Filer) {
    val originatingElements = members.asSequence()
        .filterIsInstance<OriginatingElementsHolder>()
        .flatMap { it.originatingElements.asSequence() }
        .toSet()

    val filerSourceFile = synchronized(filer) {
        filer.createResource(
            StandardLocation.SOURCE_OUTPUT,
            packageName,
            "$name.kt",
            *originatingElements.toTypedArray()
        )
    }

    try {
        filerSourceFile.openWriter().use { writer -> writeTo(writer) }
    } catch (e: Exception) {
        try {
            filerSourceFile.delete()
        } catch (ignored: Exception) {
        }
        throw e
    }
}

suspend fun RoundEnvironment.getElementsAnnotatedWith(logger: Logger, annotation: KClass<out Annotation>): Set<Element> {
    return logger.measure("get annotations: ${annotation.simpleName}"){
        getElementsAnnotatedWith(annotation.java).onEach { it.ensureLoaded() }
    }
}
