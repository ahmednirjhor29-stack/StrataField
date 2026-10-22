package com.stratafield.app

import android.app.Application
import android.content.Context
import com.stratafield.app.data.repository.StrataFieldRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class StrataFieldApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: StrataFieldApp
            private set

        fun getRepository(context: Context): StrataFieldRepository {
            return StrataFieldRepository.getInstance(context)
        }
    }
}
