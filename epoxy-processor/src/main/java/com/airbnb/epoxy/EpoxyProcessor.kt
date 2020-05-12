package com.airbnb.epoxy

import java.util.HashSet
import java.util.LinkedHashMap
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import kotlin.reflect.KClass
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType
import java.util.concurrent.ConcurrentHashMap

/**
 * Looks for [EpoxyAttribute] annotations and generates a subclass for all classes that have
 * those attributes. The generated subclass includes setters, getters, equals, and hashcode for the
 * given field. Any constructors on the original class are duplicated. Abstract classes are ignored
 * since generated classes would have to be abstract in order to guarantee they compile, and that
 * reduces their usefulness and doesn't make as much sense to support.
 */
@IncrementalAnnotationProcessor(IncrementalAnnotationProcessorType.AGGREGATING)
class EpoxyProcessor @JvmOverloads constructor(
    testOptions: Map<String, String>? = null
) : BaseProcessorWithPackageConfigs(testOptions) {

    override val usesPackageEpoxyConfig: Boolean = true
    override val usesModelViewConfig: Boolean = false
    private val styleableModelsToWrite = mutableListOf<BasicGeneratedModelInfo>()

    override fun supportedAnnotations(): List<KClass<*>> = super.supportedAnnotations() + listOf(
        EpoxyModelClass::class,
        EpoxyAttribute::class
    )

    override suspend fun processRound(roundEnv: RoundEnvironment) {
        super.processRound(roundEnv)
        val modelClassMap = ConcurrentHashMap<TypeElement, GeneratedModelInfo>()

        roundEnv.getElementsAnnotatedWith(EpoxyAttribute::class.java)
            .map("Find EpoxyAttribute class") { annotatedElement ->
                annotatedElement to getOrCreateTargetClass(
                    modelClassMap,
                    annotatedElement.enclosingElement as TypeElement
                )
            }
            .map("Build EpoxyAttribute") { (attribute, targetClass) ->
                targetClass.addAttribute(
                    buildAttributeInfo(
                        attribute,
                        logger,
                        typeUtils,
                        elementUtils
                    )
                )
            }


        roundEnv.getElementsAnnotatedWith(EpoxyModelClass::class.java)
            .map("Process EpoxyModelClass") { clazz ->
                getOrCreateTargetClass(modelClassMap, clazz as TypeElement)
            }

        try {
            addAttributesFromOtherModules(modelClassMap)
        } catch (e: Exception) {
            logger.logError(e)
        }

        try {
            updateClassesForInheritance(modelClassMap)
        } catch (e: Exception) {
            logger.logError(e)
        }

        val modelInfos = modelClassMap.values

        val styleableModels = modelInfos.map("Check for styleable") { modelInfo ->
            if (modelInfo is BasicGeneratedModelInfo &&
                modelInfo.superClassElement.annotation<EpoxyModelClass>()?.layout == 0 &&
                modelInfo.boundObjectTypeElement?.hasStyleableAnnotation(elementUtils) == true
            ) {
                modelInfo
            } else {
                null
            }
        }

        styleableModelsToWrite.addAll(styleableModels)

        modelInfos.minus(styleableModels).map("Write model") {
            writeModel(it)
        }

        styleableModelsToWrite.map("Write styleable model") { modelInfo ->
            if (tryAddStyleBuilderAttribute(modelInfo, elementUtils, typeUtils)) {
                writeModel(modelInfo)
                modelInfo
            } else {
                null
            }
        }
            .let { styleableModelsToWrite.removeAll(it) }

        generatedModels.addAll(modelClassMap.values)
        Unit
    }

    private fun writeModel(modelInfo: GeneratedModelInfo) {
        modelWriter.generateClassForModel(
            modelInfo,
            originatingElements = modelInfo.originatingElements()
        )
    }

    @Synchronized
    private fun getOrCreateTargetClass(
        modelClassMap: MutableMap<TypeElement, GeneratedModelInfo>,
        classElement: TypeElement
    ): GeneratedModelInfo {

        modelClassMap[classElement]?.let { return it }

        val isFinal = classElement.modifiers.contains(Modifier.FINAL)
        if (isFinal) {
            logger.logError(
                "Class with %s annotations cannot be final: %s",
                EpoxyAttribute::class.java.simpleName, classElement.simpleName
            )
        }

        // Nested classes must be static
        if (classElement.nestingKind.isNested) {
            if (!classElement.modifiers.contains(Modifier.STATIC)) {
                logger.logError(
                    "Nested model classes must be static. (class: %s)",
                    classElement.simpleName
                )
            }
        }

        if (!Utils.isEpoxyModel(classElement.asType())) {
            logger.logError(
                "Class with %s annotations must extend %s (%s)",
                EpoxyAttribute::class.java.simpleName, Utils.EPOXY_MODEL_TYPE,
                classElement.simpleName
            )
        }

        if (configManager.requiresAbstractModels(classElement) && !classElement.modifiers.contains(
                Modifier.ABSTRACT
            )
        ) {
            logger
                .logError(
                    "Epoxy model class must be abstract (%s)",
                    classElement.simpleName
                )
        }

        val generatedModelInfo = BasicGeneratedModelInfo(
            elementUtils,
            typeUtils,
            classElement,
            logger
        )
        modelClassMap[classElement] = generatedModelInfo

        return generatedModelInfo
    }

    /**
     * Looks for attributes on super classes that weren't included in this processor's coverage. Super
     * classes are already found if they are in the same module since the processor will pick them up
     * with the rest of the annotations.
     */
    private fun addAttributesFromOtherModules(
        modelClassMap: Map<TypeElement, GeneratedModelInfo>
    ) {
        // Copy the entries in the original map so we can add new entries to the map while we iterate
        // through the old entries
        val originalEntries = HashSet(modelClassMap.entries)

        for ((currentEpoxyModel, generatedModelInfo) in originalEntries) {
            // We add just the attribute info to the class in our module. We do NOT want to
            // generate a class for the super class EpoxyModel in the other module since one
            // will be created when that module is processed. If we make one as well there will
            // be a duplicate (causes proguard errors and is just wrong).
            getInheritedEpoxyAttributes(
                currentEpoxyModel.superclass,
                generatedModelInfo.generatedName.packageName(),
                typeUtils,
                elementUtils,
                logger,
                includeSuperClass = { superClassElement ->
                    !modelClassMap.keys.contains(superClassElement)
                }
            ).let { attributeInfos ->
                generatedModelInfo.addAttributes(attributeInfos)
            }
        }
    }

    /**
     * Check each model for super classes that also have attributes. For each super class with
     * attributes we add those attributes to the attributes of the generated class, so that a
     * generated class contains all the attributes of its super classes combined.
     *
     * One caveat is that if a sub class is in a different package than its super class we can't
     * include attributes that are package private, otherwise the generated class won't compile.
     */
    private fun updateClassesForInheritance(
        helperClassMap: Map<TypeElement, GeneratedModelInfo>
    ) {
        for ((thisClass, value) in helperClassMap) {

            val otherClasses = LinkedHashMap(helperClassMap)
            otherClasses.remove(thisClass)

            for ((otherClass, value1) in otherClasses) {

                if (!Utils.isSubtype(thisClass, otherClass, typeUtils)) {
                    continue
                }

                val otherAttributes = value1.getAttributeInfo()

                if (Utils.belongToTheSamePackage(thisClass, otherClass, elementUtils)) {
                    value.addAttributes(otherAttributes)
                } else {
                    otherAttributes
                        .filterNot { it.isPackagePrivate }
                        .forEach { value.addAttribute(it) }
                }
            }
        }
    }

    companion object {
        /** For testing.  */
        @JvmStatic
        fun withNoValidation(): EpoxyProcessor {
            return EpoxyProcessor(mapOf(ConfigManager.PROCESSOR_OPTION_VALIDATE_MODEL_USAGE to "false"))
        }

        /** For testing.  */
        @JvmStatic
        fun withImplicitAdding(): EpoxyProcessor {
            return EpoxyProcessor(mapOf(ConfigManager.PROCESSOR_OPTION_IMPLICITLY_ADD_AUTO_MODELS to "true"))
        }

        fun buildAttributeInfo(
            attribute: Element,
            logger: Logger,
            typeUtils: Types,
            elementUtils: Elements
        ): AttributeInfo {
            Utils.validateFieldAccessibleViaGeneratedCode(
                attribute, EpoxyAttribute::class.java, logger,
                true
            )
            return BaseModelAttributeInfo(attribute, typeUtils, elementUtils, logger)
        }

        /**
         * Looks up all of the declared EpoxyAttribute fields on superclasses and returns
         * attribute info for them.
         */
        fun getInheritedEpoxyAttributes(
            originatingSuperClassType: TypeMirror,
            modelPackage: String,
            typeUtils: Types,
            elementUtils: Elements,
            logger: Logger,
            includeSuperClass: (TypeElement) -> Boolean = { true }
        ): List<AttributeInfo> {
            val result = mutableListOf<AttributeInfo>()
            var currentSuperClassType: TypeMirror = originatingSuperClassType

            while (Utils.isEpoxyModel(currentSuperClassType)) {
                val currentSuperClassElement =
                    (typeUtils.asElement(currentSuperClassType) as TypeElement)

                currentSuperClassElement
                    .takeIf(includeSuperClass)
                    ?.enclosedElements
                    ?.filter { it.getAnnotation(EpoxyAttribute::class.java) != null }
                    ?.map { buildAttributeInfo(it, logger, typeUtils, elementUtils) }
                    ?.filterTo(result) {
                        // We can't inherit a package private attribute if we're not in the same package
                        !it.isPackagePrivate || modelPackage == elementUtils.getPackageOf(
                            currentSuperClassElement
                        ).qualifiedName.toString()
                    }

                currentSuperClassType = currentSuperClassElement.superclass
            }

            return result
        }
    }
}
