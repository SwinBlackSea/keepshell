package com.keepshell.data

import kotlinx.coroutines.flow.Flow

class HostRepository(private val hostDao: HostDao) {
    val hosts: Flow<List<HostEntity>> = hostDao.observeAll()

    suspend fun get(id: Long): HostEntity? = hostDao.getById(id)

    suspend fun save(host: HostEntity): Long {
        return if (host.id == 0L) {
            hostDao.insert(host)
        } else {
            hostDao.update(host)
            host.id
        }
    }

    suspend fun delete(host: HostEntity) = hostDao.delete(host)

    suspend fun markConnected(id: Long) = hostDao.markConnected(id, System.currentTimeMillis())
}
