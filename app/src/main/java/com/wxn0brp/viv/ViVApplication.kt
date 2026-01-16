package com.wxn0brp.viv

import android.app.Application

class ViVApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
}
