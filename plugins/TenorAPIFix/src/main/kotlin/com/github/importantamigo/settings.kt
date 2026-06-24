package com.github.importantamigo

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import com.aliucord.api.SettingsAPI
import com.aliucord.views.TextInput
import com.aliucord.widgets.BottomSheet

class PluginSettings(private val settings: SettingsAPI) : BottomSheet() {
    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)

        val currentKey = settings.getString("apiKey", TenorAPIFix.DEFAULT_TENOR_KEY)

        TextInput(
            view.context, "Tenor API Key", currentKey,
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {

                    val key = s.toString().trim()

                    if (key.isEmpty()) {

                        settings.remove("apiKey")

                    } else {

                        settings.setString("apiKey", key)
                    }
                }
            },
        ).also { addView(it) }
    }
}
