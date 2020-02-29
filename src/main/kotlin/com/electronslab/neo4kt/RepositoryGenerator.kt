package com.electronslab.neo4kt

import com.electronslab.neo4kt.repository.Insert
import com.electronslab.neo4kt.repository.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import me.eugeniomarletti.kotlin.metadata.*
import me.eugeniomarletti.kotlin.metadata.shadow.metadata.ProtoBuf
import me.eugeniomarletti.kotlin.metadata.shadow.name.ClassId
import me.eugeniomarletti.kotlin.metadata.shadow.name.FqName
import me.eugeniomarletti.kotlin.metadata.shadow.platform.JavaToKotlinClassMap
import me.eugeniomarletti.kotlin.metadata.shadow.serialization.deserialization.getClassId
import me.eugeniomarletti.kotlin.metadata.shadow.serialization.deserialization.getName
import org.neo4j.ogm.session.Session
import javax.annotation.processing.Filer
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement


internal class RepositoryGenerator(
    metadata: KotlinClassMetadata,
    private val typeElement: TypeElement
) {

    private val data = metadata.data
    private val nameResolver = data.nameResolver
    private val classProto = data.classProto

    private val name = nameResolver.getClassId(classProto.fqName).relativeClassName.shortName().toString()

    private val fileSpec: FileSpec.Builder = FileSpec.builder("com.electronslab.data.database.repository", "${name}Impl")


    init {


        val parameterizedTypeName = typeElement.interfaces[0].asTypeName() as ParameterizedTypeName
        val repositoryTypeName =
            parameterizedTypeName.rawType.parameterizedBy(*parameterizedTypeName.typeArguments.map { it.asKtType() }.toTypedArray())

        fileSpec.addFunction(FunSpec.builder(typeElement.simpleName.toString().decapitalize())
            .addParameter("session", Session::class)
            .returns(typeElement.asClassName())
            .addCode("return ${typeElement.simpleName}Impl(session)")
            .build()).addImport("com.electronslab.neo4kt.repository","NotFoundError")

        val type = TypeSpec.classBuilder("${name}Impl")
            .addModifiers(KModifier.PRIVATE)
            .addSuperinterface(classProto.asClassName())
            .addSuperinterface(
                repositoryTypeName,
                delegate = CodeBlock.of(
                    "%1T(session,%2T::class.java)",
                    NeoRepositoryImpl::class.java,
                    parameterizedTypeName.typeArguments[0].asKtType()
                )
            )
            .addFunctions(classProto.functionList.map { it.functionSpec() })
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("session", Session::class)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("session", Session::class, KModifier.PRIVATE)
                    .initializer("session")
                    .build()
            )
            .build()

        fileSpec.addType(type)

    }


    fun build(filer: Filer) {
        fileSpec.build().writeTo(filer)
    }


    private fun ProtoBuf.Function.functionSpec(): FunSpec {

        val executableElement = getExecutableElement()

        val code = queryCode(executableElement, valueParameterList.map { it.simpleName }, returnType)
            ?: insertCode(executableElement)
            ?: updateCode(executableElement)!!

        val parameterizedTypeName =
            if (returnType.argumentCount > 0) ClassName.bestGuess(nameResolver.getClassId(returnType.className).asSingleFqName().asString()).parameterizedBy(
                returnType.argumentList.map {
                    ClassName.bestGuess(nameResolver.getClassId(it.type.className).asSingleFqName().asString())
                }) else returnType.asClassName()

        return FunSpec.builder(simpleName)
            .addModifiers(KModifier.OVERRIDE)
            .also { if (isSuspend) it.addModifiers(KModifier.SUSPEND) }
            .addParameters(valueParameterList.map { it.parameterSpec() })
            .returns(parameterizedTypeName)
            .addCode(code)
            .build()
    }


    private fun ProtoBuf.ValueParameter.parameterSpec() =
        ParameterSpec.builder(simpleName, type.asClassName()).build()


    private val ProtoBuf.Function.simpleName get() = nameResolver.getName(name).asString()
    private val ProtoBuf.ValueParameter.simpleName get() = nameResolver.getName(name).asString()
    private fun ClassId.asClassName() = ClassName.bestGuess(asSingleFqName().asString())
    private fun ProtoBuf.Type.asClassName() = nameResolver.getClassId(className).asClassName().copy(nullable)
    private fun ProtoBuf.Class.asClassName() = nameResolver.getClassId(fqName).asClassName().copy()


    private fun ProtoBuf.Function.getExecutableElement(): ExecutableElement =
        (typeElement.enclosedElements.find { nameResolver.getName(name).asString() == it.simpleName.toString() } as ExecutableElement)


    private fun queryCode(
        executableElement: ExecutableElement,
        parameters: List<String>,
        returnType: ProtoBuf.Type
    ): CodeBlock? {
        val query = executableElement.getAnnotation(Query::class.java)
        return if (query != null) queryCode(query.query, queryParameters(executableElement, parameters), returnType)
        else null
    }

    private fun insertCode(executableElement: ExecutableElement): CodeBlock? {
        return if (executableElement.getAnnotation(Insert::class.java) != null)
            CodeBlock.of(saveCode(executableElement.parameters[0].simpleName.toString()))
        else null
    }

    private fun saveCode(parameter: String) = "session.save($parameter)"
    private fun updateCode(executableElement: ExecutableElement): CodeBlock? {
        return if (executableElement.getAnnotation(Update::class.java) != null) {
            val parameterName = executableElement.parameters[0].simpleName.toString()
            CodeBlock.builder()
                .addStatement("if($parameterName is %T) $parameterName.update()", Updatable::class.java)
                .addStatement(saveCode(parameterName))
                .build()
        } else null
    }

    private fun queryCode(query: String, parameters: String, returnType: ProtoBuf.Type): CodeBlock {

        val type = returnType.asClassName()
        val  notFoundError =  if (!returnType.nullable) "?:throw NotFoundError()" else ""
        return when {
            type == Unit::class.asTypeName() -> CodeBlock.builder().addStatement("val _shadow_query=%S",query).add(
                "session.query(_shadow_query,  mutableMapOf($parameters))$notFoundError"
            ).build()
            returnType.argumentCount == 1 -> CodeBlock.builder().addStatement("val _shadow_query=%S",query).add(
                "return session.query(%T::class.java,_shadow_query,  mutableMapOf($parameters)).toList()",
                returnType.argumentList[0].type.asClassName()
            ).build()
            else -> CodeBlock.builder().addStatement("val _shadow_query=%S",query).add(
                "return session.queryForObject(%T::class.java,_shadow_query,  mutableMapOf($parameters))$notFoundError",
                type.copy(nullable = false)
            ).build()
        }

    }


    private fun queryParameters(executableElement: ExecutableElement, parameters: List<String>) =
        executableElement.parameters.map {
            val name = it.simpleName.toString()
            if (name in parameters) "\"$name\" to ${it.getAnnotation(Parameter::class.java)?.key ?: name}"
            else null
        }.filterNotNull().joinToString(",")


    private fun TypeName.asKtType(): ClassName {
        val classId = JavaToKotlinClassMap.mapJavaToKotlin(FqName(toString()))
        return ClassName.bestGuess(classId?.asSingleFqName()?.asString() ?: toString())
    }

    companion object {

        fun build(element: Element, filer: Filer) {
            if (element is TypeElement) {
                val metaData = element.kotlinMetadata
                if (metaData is KotlinClassMetadata) RepositoryGenerator(
                    metaData,
                    element
                ).build(filer)
            }

        }
    }
}