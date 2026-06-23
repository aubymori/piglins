package com.github.importantamigo

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.aliucord.utils.DimenUtils
import com.aliucord.utils.ReflectUtils
import com.github.importantamigo.ui.SilentIconResource
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage
import com.discord.widgets.chat.list.entries.ChatListEntry
import com.discord.widgets.chat.list.entries.MessageEntry

@AliucordPlugin
@SuppressLint("SetTextI18n")
class SilentIcon : Plugin() {
    private val silentFlag = 4096L
    private val silentIconId = View.generateViewId()
    private lateinit var silentIconResource: SilentIconResource

    val sizePx by lazy { DimenUtils.dpToPx(14) }
    private val marginPx by lazy { DimenUtils.dpToPx(4) }

    override fun load(context: Context) {
        silentIconResource = SilentIconResource(resources!!)
    }

    override fun start(context: Context) {
        val timestampId = Utils.getResId("chat_list_adapter_item_text_timestamp", "id")
        val headerId = Utils.getResId("chat_list_adapter_item_text_header", "id")

        patcher.patch(
            MessageEntry::class.java,
            "getType",
            emptyArray(),
            com.aliucord.patcher.Hook { param ->
                try {
                    val entry = param.thisObject as? MessageEntry ?: return@Hook
                    val message = entry.message ?: return@Hook
                    val isSilent = ((message.flags ?: 0L) and silentFlag) != 0L

                    if (isSilent) {
                        val prevEntry = ReflectUtils.getField(entry, "prevEntry") as? MessageEntry
                        val prevIsSilent = (prevEntry?.message?.flags ?: 0L) and silentFlag != 0L

                        if (prevIsSilent) { return@Hook } else { param.result = 0 }
                    }
                } catch (_: Throwable) { }
            }
        )

        patcher.after<WidgetChatListAdapterItemMessage>(
            "onConfigure",
            Int::class.java,
            ChatListEntry::class.java
        ) { param ->
            try {
                val entry = param.args[1] as? MessageEntry ?: return@after
                val message = entry.message ?: return@after
                val flags = message.flags ?: 0L
                val isSilent = (flags and silentFlag) != 0L

                val itemView = this.itemView
                val headerView = itemView.findViewById<ConstraintLayout>(headerId) ?: return@after
                val timestampView = (ReflectUtils.getField(this, "itemTimestamp") as? TextView)
                    ?: itemView.findViewById<TextView>(timestampId) ?: return@after

                val existingIcon = headerView.findViewById<ImageView>(silentIconId)
                if (existingIcon != null) {
                    existingIcon.visibility = if (isSilent) View.VISIBLE else View.GONE
                    if (isSilent) existingIcon.setColorFilter(timestampView.currentTextColor)
                    return@after
                }

                if (!isSilent) return@after

                ImageView(context).apply {
                    id = silentIconId
                    visibility = View.VISIBLE
                    val drawable = silentIconResource.getDrawable("ic_silent_message")
                    setImageDrawable(drawable)
                    contentDescription = "Silent Message"
                    setColorFilter(timestampView.currentTextColor)
                }.addNextTo(headerView, timestampView)

            } catch (e: Throwable) {
                logger.error("SilentIcon error", e)
            }
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }

    private fun View.addNextTo(parent: ConstraintLayout, anchor: View): View {
        addTo(parent) {
            layoutParams = ConstraintLayout.LayoutParams(sizePx, sizePx).apply {
                marginStart = marginPx
                topToTop = anchor.id
                bottomToBottom = anchor.id
                startToEnd = anchor.id
            }
        }
        return this
    }

    private fun View.addTo(parent: ViewGroup, setup: View.() -> Unit = {}): View {
        if (this.parent != null) (this.parent as ViewGroup).removeView(this)
        parent.addView(this)
        setup()
        return this
    }
}
