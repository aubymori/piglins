package com.github.importantamigo.ui

import android.annotation.SuppressLint
import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat

class SilentIconResource(private val resources: Resources) {

    @SuppressLint("DiscouragedApi")
    fun getId(name: String, type: String) =
        resources.getIdentifier(name, type, "com.github.importantamigo")

    @DrawableRes fun getDrawableId(name: String) =
        getId(name, "drawable")

    fun getDrawable(@DrawableRes id: Int) =
        ResourcesCompat.getDrawable(resources, id, null)

    fun getDrawable(name: String) =
        getDrawable(getDrawableId(name))
}
