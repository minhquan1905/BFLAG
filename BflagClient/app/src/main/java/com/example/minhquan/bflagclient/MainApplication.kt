package com.example.minhquan.bflagclient

import android.app.Application
import android.util.Log
import com.example.minhquan.bflagclient.utils.SharedPreferenceHelper

class MainApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        Log.d("Main application","Init")
        SharedPreferenceHelper.getInstance(this)
    }
}