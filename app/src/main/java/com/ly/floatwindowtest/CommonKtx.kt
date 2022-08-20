package com.ly.floatwindowtest

import android.widget.Toast

fun toast(msg: String) {
    Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
}