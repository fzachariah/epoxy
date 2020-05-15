package com.airbnb.epoxy.processor

import com.airbnb.epoxy.ModelView
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import kotlinx.metadata.Flag.ValueParameter.DECLARES_DEFAULT_VALUE
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmType
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import java.util.Collections

class ModelViewInfo(
    val viewElement: TypeElement,
    val typeUtils: Types,
    val elements: Elements,
    val logger: Logger,
    private val configManager: ConfigManager,
    private val resourceProcessor: ResourceProcessor,
    memoizer: Memoizer
) : GeneratedModelInfo(memoizer) {

    val resetMethodNames = Collections.synchronizedSet(mutableSetOf<String>())
    val visibilityStateChangedMethodNames = Collections.synchronizedSet(mutableSetOf<String>())
    val visibilityChangedMethodNames = Collections.synchronizedSet(mutableSetOf<String>())
    val afterPropsSetMethodNames = Collections.synchronizedSet(mutableSetOf<String>())
    val saveViewState: Boolean
    private val viewAnnotation: ModelView = viewElement.getAnnotation(ModelView::class.java)
    val fullSpanSize: Boolean
    private val generatedModelSuffix: String
    val kotlinMetadata: KotlinClassMetadata? = viewElement.kotlinMetadata()
    val functionsWithSingleDefaultParameter: List<KmFunction> =
        (kotlinMetadata as? KotlinClassMetadata.Class)
            ?.toKmClass()
            ?.functions
            ?.filter {
                val param = it.valueParameters.singleOrNull()
                param != null && DECLARES_DEFAULT_VALUE(param.flags)
            }
            ?: emptyList()

    /** All interfaces the view implements that have at least one prop set by the interface. */
    private val viewInterfaces: List<TypeElement>

    val viewAttributes: List<ViewAttributeInfo>
        get() = attributeInfo.filterIsInstance<ViewAttributeInfo>()

    init {
        superClassElement = lookUpSuperClassElement()
        this.superClassName = ParameterizedTypeName
            .get(ClassName.get(superClassElement), TypeName.get(viewElement.asType()))

        generatedModelSuffix = configManager.generatedModelSuffix(viewElement)
        generatedClassName = buildGeneratedModelName(viewElement, elements)
        // We don't have any type parameters on our generated model
        this.parametrizedClassName = generatedClassName
        shouldGenerateModel = Modifier.ABSTRACT !in viewElement.modifiers

        if (
            superClassElement.simpleName.toString() != ClassNames.EPOXY_MODEL_UNTYPED.simpleName()
        ) {
            // If the view has a custom base model then we copy any custom constructors on it
            constructors.addAll(getClassConstructors(superClassElement))
        }

        collectMethodsReturningClassType(superClassElement)

        // The bound type is the type of this view
        boundObjectTypeName = ClassName.get(viewElement.asType())

        saveViewState = viewAnnotation.saveViewState
        layoutParams = viewAnnotation.autoLayout
        fullSpanSize = viewAnnotation.fullSpan
        includeOtherLayoutOptions = configManager.includeAlternateLayoutsForViews(viewElement)

        val methodsOnView = viewElement.executableElements()
        viewInterfaces = viewElement
            .interfaces
            .filterIsInstance<DeclaredType>()
            .map { it.asElement().ensureLoaded() }
            .filterIsInstance<TypeElement>()
            .filter { interfaceElement ->
                // Only include the interface if the view has one of the interface methods annotated with a prop annotation
                methodsOnView.any { viewMethod ->
                    viewMethod.hasAnyAnnotation(ModelViewProcessor.modelPropAnnotations) &&
                        interfaceElement.executableElements().any { interfaceMethod ->
                            // To keep this simple we only compare name and ignore parameters, should be close enough
                            viewMethod.simpleName.toString() == interfaceMethod.simpleName.toString()
                        }
                }
            }

        // Pass deprecated annotations on to the generated model
        annotations.addAll(
            viewElement.buildAnnotationSpecs { DEPRECATED == it.simpleName() }
        )
    }

    /** We generate an interface on the model to represent each interface on the view.
     * This lets models with the same view interface be grouped together. */
    val generatedViewInterfaceNames: List<ClassName> by lazy {
        viewInterfaces.map {
            ClassName.get(it).appendToName("Model_")
        }
    }

    fun Element.kotlinMetadata(): KotlinClassMetadata? {
        // https://github.com/JetBrains/kotlin/tree/master/libraries/kotlinx-metadata/jvm
        val kotlinMetadataAnnotation = getAnnotation(Metadata::class.java) ?: return null

        val header = KotlinClassHeader(
            kind = kotlinMetadataAnnotation.kind,
            metadataVersion = kotlinMetadataAnnotation.metadataVersion,
            bytecodeVersion = kotlinMetadataAnnotation.bytecodeVersion,
            data1 = kotlinMetadataAnnotation.data1,
            data2 = kotlinMetadataAnnotation.data2,
            extraString = kotlinMetadataAnnotation.extraString,
            packageName = kotlinMetadataAnnotation.packageName,
            extraInt = kotlinMetadataAnnotation.extraInt
        )

        return KotlinClassMetadata.read(header)
    }

    private fun lookUpSuperClassElement(): TypeElement {
        val classToExtend: TypeMirror = typeMirror { viewAnnotation.baseModelClass }
            ?.takeIf { !it.isVoidClass() }
            ?: configManager.getDefaultBaseModel(viewElement)
            ?: return memoizer.epoxyModelClassElementUntyped

        val superElement =
            memoizer.validateViewModelBaseClass(classToExtend, logger, viewElement.simpleName)

        return superElement ?: memoizer.epoxyModelClassElementUntyped
    }

    private fun buildGeneratedModelName(
        viewElement: TypeElement,
        elementUtils: Elements
    ): ClassName {
        val packageName = elementUtils.getPackageOf(viewElement).qualifiedName.toString()

        var className = viewElement.simpleName.toString()
        className += generatedModelSuffix

        return ClassName.get(packageName, className)
    }

    fun addProp(prop: Element) {

        val hasDefaultKotlinValue = checkIsSetterWithSingleDefaultParam(prop)

        // Since our generated code is java we need jvmoverloads so that a no arg
        // version of the function is generated. However, the JvmOverloads annotation
        // is stripped when generating the java code so we can't check it directly.
        // Instead, we verify that a no arg function of the same name exists
        val hasNoArgEquivalent = hasDefaultKotlinValue &&
            prop is ExecutableElement &&
            viewElement.hasOverload(prop, 0)

        if (hasDefaultKotlinValue && !hasNoArgEquivalent) {
            logger.logError(
                "Model view function with default argument must be annotated with @JvmOverloads: %s#%s",
                viewElement.simpleName,
                prop.simpleName
            )
        }

        addAttribute(
            ViewAttributeInfo(
                modelInfo = this,
                hasDefaultKotlinValue = hasDefaultKotlinValue && hasNoArgEquivalent,
                viewAttributeElement = prop,
                types = typeUtils,
                elements = elements,
                logger = logger,
                resourceProcessor = resourceProcessor
            )
        )
    }

    fun addPropIfNotExists(prop: Element) {
        addAttributeIfNotExists(
            ViewAttributeInfo(
                modelInfo = this,
                hasDefaultKotlinValue = false,
                viewAttributeElement = prop,
                types = typeUtils,
                elements = elements,
                logger = logger,
                resourceProcessor = resourceProcessor
            )
        )
    }

    fun addOnRecycleMethod(resetMethod: Element) {
        val methodName = resetMethod.simpleName.toString()
        resetMethodNames.add(methodName)
    }

    fun addOnVisibilityStateChangedMethod(visibilityMethod: Element) {
        val methodName = visibilityMethod.simpleName.toString()
        visibilityStateChangedMethodNames.add(methodName)
    }

    fun addOnVisibilityChangedMethod(visibilityMethod: Element) {
        val methodName = visibilityMethod.simpleName.toString()
        visibilityChangedMethodNames.add(methodName)
    }

    fun addAfterPropsSetMethod(afterPropsSetMethod: Element) {
        val methodName = afterPropsSetMethod.simpleName.toString()
        afterPropsSetMethodNames.add(methodName)
    }

    fun getLayoutResource(resourceProcessor: ResourceProcessor): ResourceValue {
        val annotation = viewElement.getAnnotation(ModelView::class.java)
        val layoutValue = annotation.defaultLayout
        if (layoutValue != 0) {
            return resourceProcessor.getLayoutInAnnotation(viewElement, ModelView::class.java)
        }

        val modelViewConfig = configManager.getModelViewConfig(viewElement)

        if (modelViewConfig != null) {
            return modelViewConfig.getNameForView(viewElement)
        }

        logger.logError("Unable to get layout resource for view %s", viewElement.simpleName)
        return ResourceValue(0)
    }

    private fun checkIsSetterWithSingleDefaultParam(element: Element): Boolean {
        if (element !is ExecutableElement) return false

        // Given an element representing a function we want to find the corresponding function information
        // in the kotlin metadata of this class. We do this by searching for a function matching the same
        // name, param count, param name, and param type.
        // This assumes the param count we're looking for is 1 since this is an epoxy setter.
        val parameters = element.parametersThreadSafe
        require(parameters.size == 1) { "Expected function $element to have exactly 1 parameter" }

        val targetFunctionName = element.simpleName.toString()
        val functionsWithSameName = functionsWithSingleDefaultParameter
            .filter { it.name == targetFunctionName }

        if (functionsWithSameName.isEmpty()) return false

        val param = parameters.single()
        val paramName = param.simpleName.toString()
        val paramType = ClassNameData.from(param.asType().toString())

        return functionsWithSameName.any { kmFunction ->
            val kmParam = kmFunction.valueParameters.singleOrNull() ?: return@any false

            kmParam.name == paramName &&
                ClassNameData.from(kmParam.type)?.isSameAs(paramType) == true
        }
    }

    private fun ClassNameData.isSameAs(
        otherType: ClassNameData
    ): Boolean {
        // The package names of kotlin metadata types use kotlin versions of things (ie kotlin.String)
        // whereas the java Element types use the Java package names. This makes it hard to directly
        // compare types. Additionally, generic information is presented in slightly different formats
        // To avoid errors where we don't compare types correctly, we instead look for the closest
        // matching types, and sort to see which type we think matches the best.

        // Sanity check that generic type counts are correct.
        // Because of type erasure a function can't have the same signature with something of the same
        // class and different generic types, so we don't have to check generic types
        if (typeNames.size != otherType.typeNames.size) {
            return false
        }

        return simpleName.equals(otherType.simpleName, ignoreCase = true)
    }

    data class ClassNameData(
        val fullNameWithoutTypes: String,
        val typeNames: List<ClassNameData?>
    ) {
        val simpleName: String = fullNameWithoutTypes.substringAfterLast(".")

        companion object {
            fun from(kmType: KmType?): ClassNameData? {
                if (kmType == null) return null

                val fullyQualifiedName = when (val classifier = kmType.classifier) {
                    is KmClassifier.Class -> classifier.name
                    is KmClassifier.TypeParameter -> return null
                    is KmClassifier.TypeAlias -> classifier.name
                }
                    // package parts are separate with "/"
                    .replace("/", ".")

                return ClassNameData(
                    fullyQualifiedName,
                    typeNames = kmType.arguments.map { ClassNameData.from(it.type) }
                )
            }

            fun from(reflectionName: String): ClassNameData {
                val fullNameWithoutTypes = reflectionName.substringBefore("<")

                val typeNames = reflectionName
                    .takeIf { it.contains("<") }
                    ?.substringAfter("<")
                    ?.substringBeforeLast(">")
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() }
                    ?.map {
                        if (it == "?") {
                            // Represent a star projection as null, which is what the kotlin
                            // metadata does too.
                            null
                        } else {
                            ClassNameData.from(it)
                        }
                    }
                    ?: emptyList()

                return ClassNameData(fullNameWithoutTypes, typeNames)
            }
        }
    }

    override fun originatingElements() = super.originatingElements() + listOf(viewElement)
}
