package com.airbnb.epoxy

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeName
import java.util.regex.Pattern
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.MirroredTypeException
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

internal object Utils {
    private val PATTERN_STARTS_WITH_SET =
        Pattern.compile("set[A-Z]\\w*")
    const val EPOXY_MODEL_TYPE = "com.airbnb.epoxy.EpoxyModel<?>"
    const val UNTYPED_EPOXY_MODEL_TYPE = "com.airbnb.epoxy.EpoxyModel"
    const val EPOXY_MODEL_WITH_HOLDER_TYPE = "com.airbnb.epoxy.EpoxyModelWithHolder<?>"
    const val EPOXY_VIEW_HOLDER_TYPE = "com.airbnb.epoxy.EpoxyViewHolder"
    const val EPOXY_HOLDER_TYPE = "com.airbnb.epoxy.EpoxyHolder"
    const val ANDROID_VIEW_TYPE = "android.view.View"
    const val EPOXY_CONTROLLER_TYPE = "com.airbnb.epoxy.EpoxyController"
    const val VIEW_CLICK_LISTENER_TYPE = "android.view.View.OnClickListener"
    const val VIEW_LONG_CLICK_LISTENER_TYPE = "android.view.View.OnLongClickListener"
    const val VIEW_CHECKED_CHANGE_LISTENER_TYPE =
        "android.widget.CompoundButton.OnCheckedChangeListener"
    const val GENERATED_MODEL_INTERFACE = "com.airbnb.epoxy.GeneratedModel"
    const val MODEL_CLICK_LISTENER_TYPE = "com.airbnb.epoxy.OnModelClickListener"
    const val MODEL_LONG_CLICK_LISTENER_TYPE = "com.airbnb.epoxy.OnModelLongClickListener"
    const val MODEL_CHECKED_CHANGE_LISTENER_TYPE =
        "com.airbnb.epoxy.OnModelCheckedChangeListener"
    const val ON_BIND_MODEL_LISTENER_TYPE = "com.airbnb.epoxy.OnModelBoundListener"
    const val ON_UNBIND_MODEL_LISTENER_TYPE = "com.airbnb.epoxy.OnModelUnboundListener"
    const val WRAPPED_LISTENER_TYPE = "com.airbnb.epoxy.WrappedEpoxyModelClickListener"
    const val WRAPPED_CHECKED_LISTENER_TYPE =
        "com.airbnb.epoxy.WrappedEpoxyModelCheckedChangeListener"
    const val DATA_BINDING_MODEL_TYPE = "com.airbnb.epoxy.DataBindingEpoxyModel"
    const val ON_VISIBILITY_STATE_MODEL_LISTENER_TYPE =
        "com.airbnb.epoxy.OnModelVisibilityStateChangedListener"
    const val ON_VISIBILITY_MODEL_LISTENER_TYPE =
        "com.airbnb.epoxy.OnModelVisibilityChangedListener"

    @JvmStatic
    @Throws(EpoxyProcessorException::class)
    fun throwError(msg: String?, vararg args: Any?) {
        throw EpoxyProcessorException(String.format(msg!!, *args))
    }

    fun getClass(name: ClassName): Class<*>? {
        return try {
            Class.forName(name.reflectionName())
        } catch (e: ClassNotFoundException) {
            null
        } catch (e: NoClassDefFoundError) {
            null
        }
    }

    fun getAnnotationClass(name: ClassName): Class<out Annotation>? {
        return try {
            @Suppress("UNCHECKED_CAST")
            getClass(name) as Class<out Annotation>?
        } catch (e: ClassCastException) {
            null
        }
    }

    fun getElementByName(
        name: ClassName,
        elements: Elements,
        types: Types
    ): TypeElement {
        return getElementByNameNullable(name, elements, types)
            ?: error("No element found for $name")
    }

    fun getElementByNameNullable(
        name: ClassName,
        elements: Elements,
        types: Types
    ): TypeElement? {
        val canonicalName = name.reflectionName().replace("$", ".")
        return getElementByNameNullable(
            canonicalName,
            elements,
            types
        ) as TypeElement?
    }

    fun getElementByName(
        name: String,
        elements: Elements,
        types: Types
    ): Element {
        return getElementByNameNullable(name, elements, types)
            ?: error("Could not by element with name $name")
    }

    fun getElementByNameNullable(
        name: String?,
        elements: Elements,
        types: Types
    ): Element? {
        return try {
            elements.getTypeElement(name)
        } catch (mte: MirroredTypeException) {
            types.asElement(mte.typeMirror)
        }
    }

    fun getClassName(className: String?): ClassName {
        return ClassName.bestGuess(className)
    }

    @JvmStatic
    fun buildEpoxyException(
        msg: String?,
        vararg args: Any?
    ): EpoxyProcessorException {
        return EpoxyProcessorException(String.format(msg!!, *args))
    }

    fun isViewClickListenerType(type: TypeMirror): Boolean {
        return isType(type, VIEW_CLICK_LISTENER_TYPE)
    }

    fun isViewLongClickListenerType(type: TypeMirror): Boolean {
        return isType(
            type,
            VIEW_LONG_CLICK_LISTENER_TYPE
        )
    }

    fun isViewCheckedChangeListenerType(type: TypeMirror): Boolean {
        return isType(
            type,
            VIEW_CHECKED_CHANGE_LISTENER_TYPE
        )
    }

    fun isBooleanType(type: TypeMirror): Boolean {
        return type.kind == TypeKind.BOOLEAN || isType(
            type,
            "java.lang.Boolean"
        )
    }

    fun isDoubleType(type: TypeMirror): Boolean {
        return type.kind == TypeKind.DOUBLE || isType(
            type,
            "java.lang.Double"
        )
    }

    fun isIntType(type: TypeMirror): Boolean {
        return type.kind == TypeKind.INT || isType(
            type,
            "java.lang.Integer"
        )
    }

    fun isLongType(type: TypeMirror): Boolean {
        return type.kind == TypeKind.LONG || isType(
            type,
            "java.lang.Long"
        )
    }

    fun isStringType(type: TypeMirror): Boolean {
        return isType(type, "java.lang.String")
    }

    fun isCharSequenceOrStringType(type: TypeMirror): Boolean {
        return isType(
            type,
            "java.lang.CharSequence"
        ) || isStringType(type)
    }

    @JvmStatic
    fun isIterableType(element: TypeElement): Boolean {
        return isSubtypeOfType(element.asType(), "java.lang.Iterable<?>")
    }

    fun isController(element: TypeElement): Boolean {
        return isSubtypeOfType(
            element.asType(),
            EPOXY_CONTROLLER_TYPE
        )
    }

    @JvmStatic
    fun isEpoxyModel(type: TypeMirror): Boolean {
        return (isSubtypeOfType(
            type,
            EPOXY_MODEL_TYPE
        )
            || isSubtypeOfType(
            type,
            UNTYPED_EPOXY_MODEL_TYPE
        ))
    }

    fun isEpoxyModel(type: TypeElement): Boolean {
        return isEpoxyModel(type.asType())
    }

    fun isEpoxyModelWithHolder(type: TypeElement): Boolean {
        return isSubtypeOfType(
            type.asType(),
            EPOXY_MODEL_WITH_HOLDER_TYPE
        )
    }

    fun isDataBindingModel(type: TypeElement): Boolean {
        return isSubtypeOfType(
            type.asType(),
            DATA_BINDING_MODEL_TYPE
        )
    }

    @JvmStatic
    fun isSubtypeOfType(typeMirror: TypeMirror, otherType: String): Boolean {
        if (otherType == typeMirror.toString()) {
            return true
        }
        if (typeMirror.kind != TypeKind.DECLARED) {
            return false
        }
        val declaredType = typeMirror as DeclaredType
        val typeArguments = declaredType.typeArguments
        if (typeArguments.size > 0) {
            val typeString = StringBuilder(declaredType.asElement().toString())
            typeString.append('<')
            for (i in typeArguments.indices) {
                if (i > 0) {
                    typeString.append(',')
                }
                typeString.append('?')
            }
            typeString.append('>')
            if (typeString.toString() == otherType) {
                return true
            }
        }
        val element = declaredType.asElement() as? TypeElement ?: return false
        val superType = element.superclass
        if (isSubtypeOfType(superType, otherType)) {
            return true
        }
        for (interfaceType in element.interfaces) {
            if (isSubtypeOfType(interfaceType, otherType)) {
                return true
            }
        }
        return false
    }

    /**
     * Checks if two classes belong to the same package
     */
    fun belongToTheSamePackage(
        class1: TypeElement?,
        class2: TypeElement?,
        elements: Elements
    ): Boolean {
        val package1 =
            elements.getPackageOf(class1).qualifiedName
        val package2 =
            elements.getPackageOf(class2).qualifiedName
        return package1 == package2
    }

    /**
     * @return true if and only if the first type is a subtype of the second
     */
    fun isSubtype(
        e1: TypeElement,
        e2: TypeElement,
        types: Types
    ): Boolean {
        return isSubtype(e1.asType(), e2.asType(), types)
    }

    /**
     * @return true if and only if the first type is a subtype of the second
     */
    fun isSubtype(
        e1: TypeMirror?,
        e2: TypeMirror?,
        types: Types
    ): Boolean {
        return types.isSubtype(e1, types.erasure(e2))
    }

    /**
     * Checks if the given field has package-private visibility
     */
    @JvmStatic
    fun isFieldPackagePrivate(element: Element): Boolean {
        val modifiers =
            element.modifiers
        return (!modifiers.contains(Modifier.PUBLIC)
            && !modifiers.contains(Modifier.PROTECTED)
            && !modifiers.contains(Modifier.PRIVATE))
    }

    /**
     * @return True if the clazz (or one of its superclasses) implements the given method. Returns
     * false if the method doesn't exist anywhere in the class hierarchy or it is abstract.
     */
    fun implementsMethod(
        clazz: TypeElement,
        method: MethodSpec,
        typeUtils: Types,
        elements: Elements
    ): Boolean {
        val methodOnClass = getMethodOnClass(clazz, method, typeUtils, elements) ?: return false
        return Modifier.ABSTRACT !in methodOnClass.modifiers
    }

    /**
     * @return The first element matching the given method in the class's hierarchy, or null if there
     * is no match.
     */
    @JvmStatic
    fun getMethodOnClass(
        clazz: TypeElement, method: MethodSpec, typeUtils: Types,
        elements: Elements
    ): ExecutableElement? {
        if (clazz.asType().kind != TypeKind.DECLARED) {
            return null
        }
        for (subElement in clazz.enclosedElements) {
            if (subElement.kind == ElementKind.METHOD) {
                val methodElement = subElement as ExecutableElement
                if (methodElement.simpleName.toString() != method.name) {
                    continue
                }
                if (!areParamsTheSame(
                        methodElement,
                        method,
                        typeUtils,
                        elements
                    )
                ) {
                    continue
                }
                return methodElement
            }
        }
        val superClazz = clazz.getParentClassElement(typeUtils) ?: return null
        return getMethodOnClass(superClazz, method, typeUtils, elements)
    }

    private fun areParamsTheSame(
        method1: ExecutableElement,
        method2: MethodSpec,
        types: Types,
        elements: Elements
    ): Boolean {
        val params1 = method1.parameters
        val params2 = method2.parameters
        if (params1.size != params2.size) {
            return false
        }

        for (i in params1.indices) {
            val param1: VariableElement = params1[i]
            val param2: ParameterSpec = params2[i]
            val param1Type: TypeMirror = types.erasure(param1.asType())

            val param2Type: TypeMirror = getTypeMirror(param2.type.toString(), elements)
                ?: error("Type mirror does not exist for ${param2.type}")

            // If a param is a type variable then we don't need an exact type match, it just needs to
            // be assignable
            if (param1.asType().kind == TypeKind.TYPEVAR) {
                if (!types.isAssignable(param2Type, param1Type)) {
                    return false
                }
            } else if (param1Type.toString() != param2Type.toString()) {
                return false
            }
        }
        return true
    }

    /**
     * Returns the type of the Epoxy model.
     *
     *
     * Eg for "class MyModel extends EpoxyModel<TextView>" it would return TextView.
    </TextView> */
    fun getEpoxyObjectType(
        clazz: TypeElement,
        typeUtils: Types
    ): TypeMirror? {
        if (clazz.superclass.kind != TypeKind.DECLARED) {
            return null
        }
        val superclass = clazz.superclass as DeclaredType
        val recursiveResult = getEpoxyObjectType(
            typeUtils.asElement(superclass) as TypeElement,
            typeUtils
        )
        if (recursiveResult != null && recursiveResult.kind != TypeKind.TYPEVAR) {
            // Use the type on the parent highest in the class hierarchy so we can find the original type.
            // We don't allow TypeVar since that is just type letter (eg T).
            return recursiveResult
        }
        val superTypeArguments =
            superclass.typeArguments
        if (superTypeArguments.size == 1) {
            // If there is only one type then we use that
            return superTypeArguments[0]
        }
        for (superTypeArgument in superTypeArguments) {
            // The user might have added additional types to their class which makes it more difficult
            // to figure out the base model type. We just look for the first type that is a view or
            // view holder.
            if (isSubtypeOfType(
                    superTypeArgument,
                    ANDROID_VIEW_TYPE
                )
                || isSubtypeOfType(
                    superTypeArgument,
                    EPOXY_HOLDER_TYPE
                )
            ) {
                return superTypeArgument
            }
        }
        return null
    }

    @JvmOverloads
    fun validateFieldAccessibleViaGeneratedCode(
        fieldElement: Element,
        annotationClass: Class<*>,
        logger: Logger,
        skipPrivateFieldCheck: Boolean = false
    ) {
        val enclosingElement = fieldElement.enclosingElement as TypeElement

        // Verify method modifiers.
        val modifiers =
            fieldElement.modifiers
        if (modifiers.contains(Modifier.PRIVATE) && !skipPrivateFieldCheck || modifiers.contains(
                Modifier.STATIC
            )
        ) {
            logger.logError(
                "%s annotations must not be on private or static fields. (class: %s, field: %s)",
                annotationClass.simpleName,
                enclosingElement.simpleName, fieldElement.simpleName
            )
        }

        // Nested classes must be static
        if (enclosingElement.nestingKind.isNested) {
            if (!enclosingElement.modifiers
                    .contains(Modifier.STATIC)
            ) {
                logger.logError(
                    "Nested classes with %s annotations must be static. (class: %s, field: %s)",
                    annotationClass.simpleName,
                    enclosingElement.simpleName, fieldElement.simpleName
                )
            }
        }

        // Verify containing type.
        if (enclosingElement.kind != ElementKind.CLASS) {
            logger
                .logError(
                    "%s annotations may only be contained in classes. (class: %s, field: %s)",
                    annotationClass.simpleName,
                    enclosingElement.simpleName, fieldElement.simpleName
                )
        }

        // Verify containing class visibility is not private.
        if (enclosingElement.modifiers.contains(Modifier.PRIVATE)) {
            logger.logError(
                "%s annotations may not be contained in private classes. (class: %s, field: %s)",
                annotationClass.simpleName,
                enclosingElement.simpleName, fieldElement.simpleName
            )
        }
    }

    @JvmStatic
    fun capitalizeFirstLetter(original: String?): String? {
        return if (original == null || original.isEmpty()) {
            original
        } else original.substring(0, 1).toUpperCase() + original.substring(1)
    }

    @JvmStatic
    fun startsWithIs(original: String): Boolean {
        return original.startsWith("is") && original.length > 2 && Character.isUpperCase(original[2])
    }

    fun isSetterMethod(element: Element): Boolean {
        if (element.kind != ElementKind.METHOD) {
            return false
        }
        val method = element as ExecutableElement
        val methodName = method.simpleName.toString()
        return (PATTERN_STARTS_WITH_SET.matcher(methodName).matches()
            && method.parameters.size == 1)
    }

    fun removeSetPrefix(string: String): String {
        return if (!PATTERN_STARTS_WITH_SET.matcher(string).matches()) {
            string
        } else string[3].toString()
            .toLowerCase() + string.substring(4)
    }

    fun isType(typeMirror: TypeMirror, otherType: String): Boolean {
        return otherType == typeMirror.toString()
    }

    fun isType(
        elements: Elements,
        types: Types,
        typeMirror: TypeMirror,
        clazz: Class<*>
    ): Boolean {
        val classType: TypeMirror = getTypeMirror(clazz, elements) ?: return false
        return types.isSameType(typeMirror, classType)
    }

    fun isType(
        elements: Elements,
        types: Types,
        typeMirror: TypeMirror?,
        vararg typeNames: Class<*>?
    ): Boolean {
        return typeNames.any { isType(elements, types, typeMirror, it) }
    }

    fun isType(type: TypeMirror, className: ClassName): Boolean {
        return isType(type, className.reflectionName())
    }

    fun isListOfType(typeMirror: TypeMirror, type: String): Boolean {
        return isType(typeMirror, "java.util.List<$type>")
    }

    fun <T : Annotation> getClassParamFromAnnotation(
        annotatedElement: Element,
        annotationClass: Class<T>,
        paramName: String,
        typeUtils: Types
    ): ClassName? {
        val am =
            getAnnotationMirror(annotatedElement, annotationClass) ?: return null
        val av =
            getAnnotationValue(am, paramName)
        return if (av == null) {
            null
        } else {
            val value = av.value
            if (value is TypeMirror) {
                ClassName.get(typeUtils.asElement(value) as TypeElement)
            } else null
            // Couldn't resolve R class
        }
    }

    private fun getAnnotationMirror(
        typeElement: Element,
        annotationClass: Class<out Annotation>
    ): AnnotationMirror? {
        val clazzName = annotationClass.name
        for (m in typeElement.annotationMirrors) {
            if (m.annotationType.toString() == clazzName) {
                return m
            }
        }
        return null
    }

    private fun getAnnotationValue(
        annotationMirror: AnnotationMirror,
        key: String
    ): AnnotationValue? {
        for ((key1, value) in annotationMirror
            .elementValues) {
            if (key1.simpleName.toString() == key) {
                return value
            }
        }
        return null
    }

    fun toSnakeCase(s: String): String {
        return s.replace("([^_A-Z])([A-Z])".toRegex(), "$1_$2").toLowerCase()
    }

    fun <T> notNull(`object`: T?): T {
        if (`object` == null) {
            throw NullPointerException()
        }
        return `object`
    }

    fun getDefaultValue(attributeType: TypeName): String {
        return if (attributeType === TypeName.BOOLEAN) {
            "false"
        } else if (attributeType === TypeName.INT) {
            "0"
        } else if (attributeType === TypeName.BYTE) {
            "(byte) 0"
        } else if (attributeType === TypeName.CHAR) {
            "(char) 0"
        } else if (attributeType === TypeName.SHORT) {
            "(short) 0"
        } else if (attributeType === TypeName.LONG) {
            "0L"
        } else if (attributeType === TypeName.FLOAT) {
            "0.0f"
        } else if (attributeType === TypeName.DOUBLE) {
            "0.0d"
        } else {
            "null"
        }
    }
}