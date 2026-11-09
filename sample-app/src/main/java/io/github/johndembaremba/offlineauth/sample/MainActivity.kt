package io.github.johndembaremba.offlineauth.sample

import android.app.Activity
import android.os.Bundle
import io.github.johndembaremba.offlineauth.OfflineAuthManager

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = OfflineAuthManager.create(applicationContext)
        manager.getPublicKey()
    }
}
