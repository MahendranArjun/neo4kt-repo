package com.electronslab.neo4kt.repository

import org.intellij.lang.annotations.Language

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Query(@Language("Cypher") val query:String)