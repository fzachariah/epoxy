@file:Suppress("asfd")

package com.airbnb.epoxy

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeVariableName
import kotlinx.coroutines.coroutineScope
import javax.annotation.processing.Filer
import javax.lang.model.element.Modifier
internal class KotlinModelBuilderExtensionWriter(
    val filer: Filer,
    asyncable: Asyncable
) : Asyncable by asyncable {

    suspend fun generateExtensionsForModels(
        generatedModels: List<GeneratedModelInfo>,
        processorName: String
    ) {
        generatedModels
            .filter { it.shouldGenerateModel }
            .groupBy { it.generatedClassName.packageName() }
            .map("generateExtensionsForModels") { packageName, models ->
                buildExtensionFile(
                    packageName,
                    models,
                    processorName
                )
            }.forEach("writeExtensionsForModels", parallel = false) {
                // Cannot be done in parallel since filer is not thread safe
                it.writeSynchronized(filer)
            }
    }

    private suspend fun buildExtensionFile(
        packageName: String,
        models: List<GeneratedModelInfo>,
        processorName: String
    ): FileSpec = coroutineScope {
        val fileBuilder = FileSpec.builder(
            packageName,
            "Epoxy${processorName.removePrefix("Epoxy")}KotlinExtensions"
        )

        models
            .map("buildExtensionFile") {
                if (it.constructors.isEmpty()) {
                    listOf(buildExtensionsForModel(it, null))
                } else {
                    it.constructors.map { constructor ->
                        buildExtensionsForModel(it, constructor)
                    }
                }
            }
            .flatten()
            .forEach { fileBuilder.addFunction(it) }

        // We suppress Deprecation warnings for this class in case any of the models used are deprecated.
        // This prevents the generated file from causing errors for using deprecated classes.
        fileBuilder.addAnnotation(
            AnnotationSpec.builder(Suppress::class)
                .addMember("%S", "DEPRECATION")
                .build()
        )

        fileBuilder.build()
    }

    private fun buildExtensionsForModel(
        model: GeneratedModelInfo,
        constructor: GeneratedModelInfo.ConstructorInfo?
    ): FunSpec {
        val constructorIsNotPublic =
            constructor != null && Modifier.PUBLIC !in constructor.modifiers

        // Kotlin cannot directly reference a class with a $ in the name. It must be wrapped in ticks (``)
        val useTicksAroundModelName = model.generatedName.simpleName().contains("$")
        val tick = if (useTicksAroundModelName) "`" else ""

        val initializerLambda = LambdaTypeName.get(
            receiver = getBuilderInterfaceTypeName(model).toKPoet(),
            returnType = ClassName.bestGuess("kotlin.Unit")
        )

        FunSpec.builder(getMethodName(model)).run {
            receiver(ClassNames.EPOXY_CONTROLLER.toKPoet())
            val params = constructor?.params ?: listOf()
            addParameters(params.toKParams())

            addParameter(
                "modelInitializer",
                initializerLambda
            )

            val modelClass = model.parameterizedGeneratedName.toKPoet()
            if (modelClass is ParameterizedTypeName) {
                // We expect the type arguments to be of type TypeVariableName
                // Otherwise we can't get bounds information off of it and can't do much
                modelClass
                    .typeArguments
                    .filterIsInstance<TypeVariableName>()
                    .let { if (it.isNotEmpty()) addTypeVariables(it) }
            }

            addModifiers(KModifier.INLINE)
            if (constructorIsNotPublic) addModifiers(KModifier.INTERNAL)

            beginControlFlow(
                "$tick%T$tick(${params.joinToString(", ") { it.name }}).apply",
                modelClass
            )
            addStatement("modelInitializer()")
            endControlFlow()
            addStatement(".addTo(this)")

            model.originatingElements().forEach {
                addOriginatingElement(it)
            }

            return build()
        }
    }

    private fun getMethodName(model: GeneratedModelInfo) = model.generatedClassName
        .simpleName()
        .lowerCaseFirstLetter()
        .removeSuffix(GeneratedModelInfo.GENERATED_MODEL_SUFFIX)
        .removeSuffix(DataBindingModelInfo.BINDING_SUFFIX)
        .removeSuffix(GeneratedModelInfo.GENERATED_CLASS_NAME_SUFFIX)
        .replace("$", "")
        .removeSuffix("Epoxy")
}
