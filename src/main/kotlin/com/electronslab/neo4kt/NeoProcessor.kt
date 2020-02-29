package com.electronslab.neo4kt

import com.electronslab.neo4kt.repository.Repository
import me.eugeniomarletti.kotlin.processing.KotlinAbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement


internal class NeoProcessor: KotlinAbstractProcessor() {
    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {

        roundEnv.getElementsAnnotatedWith(Repository::class.java).forEach {
            if (it.kind==ElementKind.INTERFACE && it is TypeElement) {
                RepositoryGenerator.build(it, filer)
            }
        }
        return true
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        return mutableSetOf(Repository::class.java.name)
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return sourceVersion
    }


}