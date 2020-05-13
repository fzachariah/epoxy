package com.airbnb.epoxy

import com.airbnb.paris.processor.ParisProcessor
import com.google.common.truth.Truth.assert_
import com.google.testing.compile.JavaFileObjects
import com.google.testing.compile.JavaSourceSubjectFactory.javaSource
import com.google.testing.compile.JavaSourcesSubjectFactory.javaSources
import java.util.ArrayList
import javax.annotation.processing.Processor
import javax.tools.JavaFileObject

internal object ProcessorTestUtils {
    @JvmStatic
    fun assertGenerationError(
        inputFile: String,
        errorMessage: String
    ) {
        val model = JavaFileObjects
            .forResource(inputFile.patchResource())

        assert_().about(javaSource())
            .that(model)
            .processedWith(processors())
            .failsToCompile()
            .withErrorContaining(errorMessage)
    }

    @JvmStatic
    fun checkFileCompiles(inputFile: String) {
        val model = JavaFileObjects
            .forResource(inputFile.patchResource())

        assert_().about(javaSource())
            .that(model)
            .processedWith(processors())
            .compilesWithoutError()
    }

    @JvmOverloads
    @JvmStatic
    fun assertGeneration(
        inputFile: String,
        generatedFile: String,
        useParis: Boolean = false,
        helperObjects: List<JavaFileObject> = emptyList()
    ) {
        val model = JavaFileObjects.forResource(inputFile.patchResource())

        val generatedModel = JavaFileObjects.forResource(generatedFile.patchResource())

        val processors = processors(useParis)

        assert_().about(javaSources())
            .that(helperObjects + listOf(model))
            .withCompilerOptions()
            .processedWith(processors)
            .compilesWithoutError()
            .and()
            .generatesSources(generatedModel)
    }

    @JvmStatic
    @JvmOverloads
    fun processors(useParis: Boolean = false): MutableList<Processor> {
        return mutableListOf<Processor>().apply {
            add(EpoxyProcessor())
            add(ControllerProcessor())
            add(DataBindingProcessor())
            add(ModelViewProcessor())
            if (useParis) add(ParisProcessor())
        }
    }

    @JvmStatic
    fun options(
        withNoValidation: Boolean = false,
        withImplicitAdding: Boolean = false
    ): List<String> {
        infix fun String.setTo(value: Any) = "-A$this=$value"

        return mutableListOf<String>().apply {
            if (withNoValidation) add("validateEpoxyModelUsage" setTo false)
            if (withImplicitAdding) add("implicitlyAddAutoModels" setTo true)
        }
    }

    @JvmStatic
    fun assertGeneration(
        inputFiles: List<String>,
        fileNames: List<String>
    ) {
        val sources = ArrayList<JavaFileObject>()

        for (inputFile in inputFiles) {
            sources.add(
                JavaFileObjects
                    .forResource(inputFile.patchResource())
            )
        }

        val generatedFiles = ArrayList<JavaFileObject>()
        for (i in fileNames.indices) {
            generatedFiles.add(JavaFileObjects.forResource(fileNames[i].patchResource()))
        }

        assert_().about(javaSources())
            .that(sources)
            .processedWith(processors())
            .compilesWithoutError()
            .and()
            .generatesSources(
                generatedFiles[0],
                *generatedFiles.subList(1, generatedFiles.size)
                    .toTypedArray()
            )
    }
}
