package com.airbnb.epoxy.processor

import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

class Memoizer(val types: Types, val elements:Elements) {

    val epoxyDataBindingModelBaseClass: TypeElement by lazy {
        Utils.getElementByName(
            ClassNames.EPOXY_DATA_BINDING_MODEL,
            elements,
            types
        )
    }
}