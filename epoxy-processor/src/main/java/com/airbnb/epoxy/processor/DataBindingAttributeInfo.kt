package com.airbnb.epoxy.processor

import com.airbnb.epoxy.processor.Utils.removeSetPrefix
import javax.lang.model.element.ExecutableElement

internal class DataBindingAttributeInfo(
    modelInfo: DataBindingModelInfo,
    setterMethod: ExecutableElement,
    hashCodeValidator: HashCodeValidator
) : AttributeInfo() {

    init {
        fieldName = removeSetPrefix(setterMethod.simpleName.toString())
        typeMirror = setterMethod.parametersThreadSafe[0].asType()
        rootClass = modelInfo.generatedName.simpleName()
        packageName = modelInfo.generatedName.packageName()
        useInHash = !modelInfo.enableDoNotHash ||
            hashCodeValidator.implementsHashCodeAndEquals(typeMirror)
        ignoreRequireHashCode = true
        generateSetter = true
        generateGetter = true
        hasFinalModifier = false
        isPackagePrivate = false
        isGenerated = true
    }
}
