package com.airbnb.epoxy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

interface Asyncable {
    val logger: Logger
    val coroutineScope: CoroutineScope
    val coroutinesEnabled: Boolean

    suspend fun <T, R : Any> Iterable<T>.map(
        tag: String,
        parallel: Boolean = true,
        transform: suspend (T) -> R?
    ): List<R> {
        val parallelize = parallel && coroutinesEnabled
        return logger.measure(tag, numItems = count(), isParallel = parallelize) {
            if (!parallelize) {
                mapNotNull {
                    try {
                        transform(it)
                    } catch (e: Exception) {
                        logger.logError(e, "$tag failed")
                        null
                    }
                }
            } else {
                this@map.map {
                    coroutineScope.async { transform(it) }
                }.awaitAndLog(tag)
                    .filterNotNull()
            }
        }
    }

    suspend fun <T> Iterable<T>.forEach(
        tag: String,
        parallel: Boolean = true,
        block: suspend (T) -> Unit
    ) {
        val parallelize = parallel && coroutinesEnabled
        logger.measure(tag, numItems = count(), isParallel = parallelize) {
            if (!parallelize) {
                forEach {
                    try {
                        block(it)
                    } catch (e: Exception) {
                        logger.logError(e, "$tag failed")
                    }
                }
            } else {
                map {
                    coroutineScope.async { block(it) }
                }.awaitAndLog(tag)
            }
        }
    }

    suspend fun <K, V> Map<K, V>.forEach(
        tag: String,
        parallel: Boolean = true,
        block: suspend (K, V) -> Any?
    ) {
        val parallelize = parallel && coroutinesEnabled
        logger.measure(tag, numItems = size, isParallel = parallelize) {
            if (!parallelize) {
                forEach {
                    try {
                        block(it.key, it.value)
                    } catch (e: Exception) {
                        logger.logError(e, "$tag failed")
                    }
                }
            } else {
                map { (k, v) ->
                    coroutineScope.async { block(k, v) }
                }.awaitAndLog(tag)
            }
        }
    }

    suspend fun <K, V, R : Any> Map<K, V>.map(
        tag: String,
        parallel: Boolean = true,
        transform: suspend (K, V) -> R?
    ): List<R> {
        val parallelize = parallel && coroutinesEnabled
        return logger.measure(tag, numItems = count(), isParallel = parallelize) {
            if (!parallelize) {
                mapNotNull {
                    try {
                        transform(it.key, it.value)
                    } catch (e: Exception) {
                        logger.logError(e, "$tag failed")
                        null
                    }
                }
            } else {
                this@map.map {
                    coroutineScope.async { transform(it.key, it.value) }
                }.awaitAndLog(tag)
                    .filterNotNull()
            }
        }
    }

    private suspend fun <T> List<Deferred<T>>.awaitAndLog(tag: String): List<T?> {
        return map {
            try {
                it.await()
            } catch (e: Exception) {
                logger.logError(e, "$tag failed")
                null
            }
        }
    }
}