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
            .processedWith(EpoxyProcessor())
            .failsToCompile()
            .withErrorContaining(errorMessage)
    }

    @JvmStatic
    fun checkFileCompiles(inputFile: String) {
        val model = JavaFileObjects
            .forResource(inputFile.patchResource())

        assert_().about(javaSource())
            .that(model)
            .processedWith(EpoxyProcessor())
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

        val processors = mutableListOf<Processor>().apply {
            add(EpoxyProcessor())
            if (useParis) add(ParisProcessor())
        }

        assert_().about(javaSources())
            .that(helperObjects + listOf(model))
            .processedWith(processors)
            .compilesWithoutError()
            .and()
            .generatesSources(generatedModel)
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
            .processedWith(EpoxyProcessor())
            .compilesWithoutError()
            .and()
            .generatesSources(
                generatedFiles[0],
                *generatedFiles.subList(1, generatedFiles.size)
                    .toTypedArray()
            )
    }
}
