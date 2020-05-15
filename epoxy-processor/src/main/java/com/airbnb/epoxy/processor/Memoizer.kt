package com.airbnb.epoxy.processor

import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.airbnb.epoxy.ModelView
import com.airbnb.epoxy.processor.GeneratedModelInfo.RESET_METHOD
import com.airbnb.epoxy.processor.GeneratedModelInfo.buildParamSpecs
import com.airbnb.epoxy.processor.Utils.isSubtype
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.Name
import javax.lang.model.element.Parameterizable
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

class Memoizer(val types: Types, val elements: Elements) {

    val epoxyModelClassAnnotation by lazy { EpoxyModelClass::class.className() }

    val epoxyDataBindingModelBaseClass: TypeElement by lazy {
        Utils.getElementByName(
            ClassNames.EPOXY_DATA_BINDING_MODEL,
            elements,
            types
        )
    }

    val epoxyModelClassElementUntyped by lazy {
        Utils.getElementByName(
            ClassNames.EPOXY_MODEL_UNTYPED,
            elements,
            types
        )
    }

    val viewType: TypeMirror by lazy {
        getTypeMirror(ClassNames.ANDROID_VIEW, elements, types)
    }

    private val methodsReturningClassType = mutableMapOf<Name, List<MethodInfo>>()

    fun getMethodsReturningClassType(classType: TypeMirror): List<MethodInfo> =
        synchronized(methodsReturningClassType) {
            val classElement = types.asElement(classType) as TypeElement
            methodsReturningClassType.getOrPut(classElement.qualifiedName) {

                classType.ensureLoaded()

                val superClassType = classElement.superclass
                superClassType.ensureLoaded()
                // Check for base Object class
                if (superClassType.kind == TypeKind.NONE) return@getOrPut emptyList()

                val methodInfos: List<MethodInfo> =
                    classElement.enclosedElementsThreadSafe.mapNotNull { subElement ->
                        val modifiers: Set<Modifier> = subElement.modifiers
                        if (subElement.kind !== ElementKind.METHOD ||
                            modifiers.contains(Modifier.PRIVATE) ||
                            modifiers.contains(Modifier.FINAL) ||
                            modifiers.contains(Modifier.STATIC)
                        ) {
                            return@mapNotNull null
                        }

                        val methodReturnType = (subElement.asType() as ExecutableType).returnType
                        if (methodReturnType != classType && !isSubtype(
                                classType,
                                methodReturnType,
                                types
                            )
                        ) {
                            return@mapNotNull null
                        }

                        val castedSubElement = subElement as ExecutableElement
                        val params: List<VariableElement> = castedSubElement.parametersThreadSafe
                        val methodName = subElement.getSimpleName().toString()
                        if (methodName == RESET_METHOD && params.isEmpty()) {
                            return@mapNotNull null
                        }
                        val isEpoxyAttribute = castedSubElement.getAnnotation(
                            EpoxyAttribute::class.java
                        ) != null

                        MethodInfo(
                            methodName,
                            modifiers,
                            buildParamSpecs(params),
                            castedSubElement.isVarArgs,
                            isEpoxyAttribute,
                            castedSubElement
                        )
                    }

                methodInfos + getMethodsReturningClassType(superClassType)
            }
        }

    private val classConstructors =
        mutableMapOf<Name, List<GeneratedModelInfo.ConstructorInfo>>()

    /**
     * Get information about constructors of the original class so we can duplicate them in the
     * generated class and call through to super with the proper parameters
     */
    fun getClassConstructors(classElement: TypeElement): List<GeneratedModelInfo.ConstructorInfo> =
        synchronized(classConstructors) {
            classConstructors.getOrPut(classElement.qualifiedName) {

                classElement
                    .enclosedElementsThreadSafe
                    .filter { subElement ->
                        subElement.kind == ElementKind.CONSTRUCTOR
                            && !subElement.modifiers.contains(Modifier.PRIVATE)
                    }
                    .map { subElement ->
                        val constructor = subElement as ExecutableElement
                        val params: List<VariableElement> = constructor.parametersThreadSafe

                        GeneratedModelInfo.ConstructorInfo(
                            subElement.getModifiers(),
                            buildParamSpecs(params),
                            constructor.isVarArgs
                        )
                    }
            }
        }

    private val validatedViewModelBaseElements = mutableMapOf<Name, TypeElement?>()
    fun validateViewModelBaseClass(
        baseModelType: TypeMirror,
        logger: Logger,
        viewName: Name
    ): TypeElement? =
        synchronized(validatedViewModelBaseElements) {
            val baseModelElement = types.asElement(baseModelType) as TypeElement
            validatedViewModelBaseElements.getOrPut(baseModelElement.qualifiedName) {

                baseModelType.ensureLoaded()
                if (!Utils.isEpoxyModel(baseModelType)) {
                    logger.logError(
                        "The base model provided to an %s must extend EpoxyModel, but was %s (%s).",
                        ModelView::class.java.simpleName, baseModelType, viewName
                    )
                    null
                } else if (!validateSuperClassIsTypedCorrectly(baseModelElement)) {
                    logger.logError(
                        "The base model provided to an %s must have View as its type (%s).",
                        ModelView::class.java.simpleName, viewName
                    )
                    null
                } else {
                    baseModelElement
                }
            }
        }

    /** The super class that our generated model extends from must have View as its only type.  */
    private fun validateSuperClassIsTypedCorrectly(classType: TypeElement): Boolean {
        val classElement = classType as? Parameterizable ?: return false

        val typeParameters = classElement.typeParameters
        if (typeParameters.size != 1) {
            // TODO: (eli_hart 6/15/17) It should be valid to have multiple or no types as long as they
            // are correct, but that should be a rare case
            return false
        }

        val typeParam = typeParameters[0]
        val bounds = typeParam.bounds
        if (bounds.isEmpty()) {
            // Any type is allowed, so View wil work
            return true
        }

        val typeMirror = bounds[0]
        return Utils.isAssignable(viewType, typeMirror, types) || types.isSubtype(
            typeMirror,
            viewType
        )
    }
}
