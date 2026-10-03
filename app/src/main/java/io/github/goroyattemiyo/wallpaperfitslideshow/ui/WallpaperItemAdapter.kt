package io.github.goroyattemiyo.wallpaperfitslideshow.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import io.github.goroyattemiyo.wallpaperfitslideshow.R
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperItem
import io.github.goroyattemiyo.wallpaperfitslideshow.model.WallpaperTarget

class WallpaperItemAdapter(
    context: Context,
    private val onTargetChanged: (String, WallpaperTarget, Boolean) -> Unit,
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private val thumbnailLoader = ThumbnailLoader(context)
    private var items: List<WallpaperItem> = emptyList()
    private var selectedItemId: String? = null

    fun update(
        items: List<WallpaperItem>,
        selectedItemId: String?,
    ) {
        this.items = items.sortedBy { it.order }
        this.selectedItemId = selectedItemId
        notifyDataSetChanged()
    }

    fun indexOfItemId(itemId: String?): Int =
        items.indexOfFirst { it.id == itemId }

    fun release() {
        thumbnailLoader.release()
    }

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
            thumbnail = view.findViewById(R.id.item_thumbnail),
            titleText = view.findViewById(R.id.item_title),
            homeCheckBox = view.findViewById(R.id.home_checkbox),
            lockCheckBox = view.findViewById(R.id.lock_checkbox),
        ).also { view.tag = it }

        val item = getItem(position)
        view.isActivated = item.id == selectedItemId

        holder.titleText.text = item.displayName
        holder.titleText.contentDescription = item.displayName
        thumbnailLoader.load(item.uri, holder.thumbnail)

        bindTargetCheckBox(
            checkBox = holder.homeCheckBox,
            item = item,
            target = WallpaperTarget.HOME,
            checked = item.homeEnabled,
        )
        bindTargetCheckBox(
            checkBox = holder.lockCheckBox,
            item = item,
            target = WallpaperTarget.LOCK,
            checked = item.lockEnabled,
        )

        return view
    }

    private fun bindTargetCheckBox(
        checkBox: CheckBox,
        item: WallpaperItem,
        target: WallpaperTarget,
        checked: Boolean,
    ) {
        checkBox.setOnCheckedChangeListener(null)
        checkBox.isChecked = checked
        checkBox.contentDescription =
            "${item.displayName}を${if (target == WallpaperTarget.HOME) "ホーム" else "ロック"}用にする"
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != checked) {
                onTargetChanged(item.id, target, isChecked)
            }
        }
    }

    private data class Holder(
        val thumbnail: ImageView,
        val titleText: TextView,
        val homeCheckBox: CheckBox,
        val lockCheckBox: CheckBox,
    )
}
