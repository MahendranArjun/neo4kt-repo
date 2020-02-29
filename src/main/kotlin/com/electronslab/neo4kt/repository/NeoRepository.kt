package com.electronslab.neo4kt.repository

import java.io.Serializable

interface NeoRepository<T:Any, ID:Serializable> {

    suspend fun create(data:T)
    suspend fun update(data:T)

    suspend fun findById(id:ID):T?

    suspend fun deleteById(id:ID)
    suspend fun delete(data:T)


}