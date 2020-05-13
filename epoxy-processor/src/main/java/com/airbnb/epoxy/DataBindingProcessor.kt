package com.airbnb.epoxy

import com.airbnb.epoxy.Utils.getClassParamFromAnnotation
import com.squareup.javapoet.ClassName
import java.util.ArrayList
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.VariableElement
import kotlin.reflect.KClass
import net.ltgt.gradle.incap.IncrementalAnnotationProcessor
import net.ltgt.gradle.incap.IncrementalAnnotationProcessorType

@IncrementalAnnotationProcessor(IncrementalAnnotationProcessorType.AGGREGATING)
class DataBindingProcessor : BaseProcessor() {
    private val modelsToWrite = ArrayList<DataBindingModelInfo>()

    override fun supportedAnnotations(): List<KClass<*>> = listOf(
        EpoxyDataBindingLayouts::class,
        EpoxyDataBindingPattern::class
    )

    override suspend fun processRound(roundEnv: RoundEnvironment) {

        roundEnv.getElementsAnnotatedWithSafe(EpoxyDataBindingLayouts::class)
            .forEach { layoutsAnnotatedElement ->

                val layoutResources = resourceProcessor
                    .getLayoutsInAnnotation(
                        layoutsAnnotatedElement,
                        EpoxyDataBindingLayouts::class.java
                    )

                // Get the module name after parsing resources so we can use the resource classes to
                // figure out the module name
                val moduleName = dataBindingModuleLookup.getModuleName(layoutsAnnotatedElement)

                val enableDoNotHash =
                    layoutsAnnotatedElement.annotation<EpoxyDataBindingLayouts>()?.enableDoNotHash == true

                layoutResources.mapTo(modelsToWrite) { resourceValue ->
                    DataBindingModelInfo(
                        typeUtils = typeUtils,
                        elementUtils = elementUtils,
                        layoutResource = resourceValue,
                        moduleName = moduleName,
                        enableDoNotHash = enableDoNotHash,
                        annotatedElement = layoutsAnnotatedElement
                    )
                }
            }

        roundEnv.getElementsAnnotatedWithSafe(EpoxyDataBindingPattern::class)
            .forEach { annotatedElement ->

                val patternAnnotation =
                    annotatedElement.getAnnotation(EpoxyDataBindingPattern::class.java)

                val layoutPrefix = patternAnnotation.layoutPrefix
                val rClassName = getClassParamFromAnnotation(
                    annotatedElement,
                    EpoxyDataBindingPattern::class.java,
                    "rClass",
                    typeUtils
                ) ?: return@forEach

                val moduleName = rClassName.packageName()
                val layoutClassName = ClassName.get(moduleName, "R", "layout")
                val enableDoNotHash =
                    annotatedElement.annotation<EpoxyDataBindingPattern>()?.enableDoNotHash == true

                val rClassElement = Utils.getElementByName(layoutClassName, elementUtils, typeUtils)
                rClassElement.ensureLoaded()

                rClassElement
                    .enclosedElements
                    .asSequence()
                    .filterIsInstance<VariableElement>()
                    .map { it.simpleName.toString() }
                    .filter { it.startsWith(layoutPrefix) }
                    .map { ResourceValue(layoutClassName, it, 0 /* value doesn't matter */) }
                    .mapTo(modelsToWrite) { layoutResource ->
                        DataBindingModelInfo(
                            typeUtils = typeUtils,
                            elementUtils = elementUtils,
                            layoutResource = layoutResource,
                            moduleName = moduleName,
                            layoutPrefix = layoutPrefix,
                            enableDoNotHash = enableDoNotHash,
                            annotatedElement = annotatedElement
                        )
                    }
            }

        val modelsWritten = resolveDataBindingClassesAndWriteJava()
        if (modelsWritten.isNotEmpty()) {
            // All databinding classes are generated at the same time, so once one is ready they
            // all should be. Since we infer databinding layouts based on a naming pattern we may
            // have some false positives which we can clear from the list if we can't find a
            // databinding class for them.
            modelsToWrite.clear()
        }

        generatedModels.addAll(modelsWritten)
    }

    private fun resolveDataBindingClassesAndWriteJava(): List<DataBindingModelInfo> {
        return modelsToWrite.filter { bindingModelInfo ->
            bindingModelInfo.parseDataBindingClass() ?: return@filter false

            try {
                modelWriter.generateClassForModel(
                    bindingModelInfo,
                    originatingElements = bindingModelInfo.originatingElements()
                )
            } catch (e: Exception) {
                logger.logError(e, "Error generating model classes")
            }

            true
        }.onEach { writtenModel ->
            modelsToWrite.remove(writtenModel)
        }
    }
}
