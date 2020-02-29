package com.electronslab.neo4kt.repository


import org.neo4j.ogm.session.Session
import java.io.Serializable
import kotlin.coroutines.suspendCoroutine

class NeoRepositoryImpl<T:Any, ID: Serializable>(private val session: Session, private val type:Class<T>) :
    NeoRepository<T, ID> {


    override suspend fun create(data: T):Unit = suspendCoroutine {
        data.also { session.save(it) }
    }


    override suspend fun update(data: T):Unit = suspendCoroutine {
        data.also { if (it is Updatable) it.update() }.also { session.save(it) }
    }

    override suspend fun findById(id: ID): T? = suspendCoroutine {
        session.load(type, id)
    }


    override suspend fun deleteById(id: ID) {
         findById(id)?.let { delete(it) }
    }

    override suspend fun delete(data: T) {
        session.delete(data)
    }

}