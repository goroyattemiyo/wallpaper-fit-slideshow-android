package io.github.goroyattemiyo.wallpaperfitslideshow.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.TextView
import io.github.goroyattemiyo.wallpaperfitslideshow.R
import io.github.goroyattemiyo.wallpaperfitslideshow.core.layout.LayoutMode
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem

class WallpaperItemAdapter(
    context: Context,
    private val onEnabledChanged: (String, Boolean) -> Unit,
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private var items: List<WallpaperItem> = emptyList()
    private var currentItemId: String? = null
    private var selectedItemId: String? = null

    fun update(
        items: List<WallpaperItem>,
        currentItemId: String?,
        selectedItemId: String?,
    ) {
        this.items = items.sortedBy { it.order }
        this.currentItemId = currentItemId
        this.selectedItemId = selectedItemId
        notifyDataSetChanged()
    }

    fun indexOfItemId(itemId: String?): Int =
        items.indexOfFirst { it.id == itemId }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): WallpaperItem = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View {
        val view = convertView ?: inflater.inflate(
            R.layout.item_wallpaper,
            parent,
            false,
        )
        val holder = (view.tag as? Holder) ?: Holder(
            enabledCheckBox = view.findViewById(R.id.slideshow_checkbox),
            titleText = view.findViewById(R.id.item_title),
            detailText = view.findViewById(R.id.item_detail),
        ).also { view.tag = it }

        val item = getItem(position)
        holder.enabledCheckBox.setOnCheckedChangeListener(null)
        holder.enabledCheckBox.isChecked = item.enabled
        holder.enabledCheckBox.contentDescription =
            "${item.displayName}をスライドショー対象にする"
        holder.enabledCheckBox.setOnCheckedChangeListener { _, checked ->
            if (checked != item.enabled) {
                onEnabledChanged(item.id, checked)
            }
        }

        holder.titleText.text = item.displayName
        holder.detailText.text = buildString {
            append(
                when (item.layout.mode) {
                    LayoutMode.CONTAIN -> "全体表示"
                    LayoutMode.CROP -> "Crop"
                },
            )
            if (item.id == currentItemId) {
                append(" ・ 現在の壁紙")
            }
            if (item.id == selectedItemId) {
                append(" ・ 編集対象")
            }
        }

        return view
    }

    private data class Holder(
        val enabledCheckBox: CheckBox,
        val titleText: TextView,
        val detailText: TextView,
    )
}
