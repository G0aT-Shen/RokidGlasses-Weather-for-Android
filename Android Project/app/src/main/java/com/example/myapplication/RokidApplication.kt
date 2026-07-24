package com.example.myapplication

import android.app.Application
import com.rokid.cxr.link.CXRLink

class RokidApplication : Application() {
    var sharedLink: CXRLink? = null

    fun resetSession() {
        sharedLink?.disconnect()
        sharedLink = null
    }
}
