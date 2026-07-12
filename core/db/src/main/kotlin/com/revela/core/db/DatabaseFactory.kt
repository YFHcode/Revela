package com.revela.core.db

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object DatabaseFactory {

    private const val DB_NAME = "revela.db"

    fun create(context: Context): RevelaDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = DbKeyManager(context.applicationContext).getOrCreatePassphrase()
        return Room.databaseBuilder(context.applicationContext, RevelaDatabase::class.java, DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }
}
