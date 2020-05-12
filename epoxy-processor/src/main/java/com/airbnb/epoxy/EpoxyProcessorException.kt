package com.airbnb.epoxy

internal class EpoxyProcessorException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)