package com.airbnb.epoxy

import com.squareup.javapoet.TypeName

data class ControllerModelField(
    val fieldName: String,
    var typeName: TypeName,
    val packagePrivate: Boolean
)
