package com.wyg.smart_man.activity

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.wyg.smart_man.R
import com.wyg.smart_man.utils.DockingPoint

//class CustomArrayAdapter(context: Context, private val items: List<String>) : ArrayAdapter<String>(context, R.layout.custom_spinner_item, items) {
class CustomArrayAdapter(
    context: Context,
    private val items: MutableList<String>,
    private val onItemDeleteRequested: (String) -> Unit, // 添加请求删除的回调
    private val onItemUpdated: (Int, String) -> Unit // 添加更新停靠点的回调
) : ArrayAdapter<String>(context, R.layout.custom_spinner_item, items) {
    private var selectedPosition : Int? = null    // 记录当前选中的位置
    private var isDropDownVisible = true  // 控制下拉菜单是否显示

    @SuppressLint("ClickableViewAccessibility")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.custom_spinner_item, parent, false)

        // 找到 TextView
        val textView: TextView = view.findViewById(R.id.text)
        val deleteIcon: ImageView = view.findViewById(R.id.delete_icon)

        val item = getItem(position)
        textView.text = item

        // 控制 EditText 的可编辑状态
        textView.isFocusable = selectedPosition == position
        textView.isFocusableInTouchMode = selectedPosition == position

        if (item == "无停靠点") { // 您可以根据实际判断条件决定
            deleteIcon.visibility = View.GONE // 隐藏叉号
        } else {
            deleteIcon.visibility = View.VISIBLE // 显示叉号
        }
        // 设置叉号的点击事件
        deleteIcon.setOnClickListener {
            item?.let {
                // 请求外部进行删除
                Log.d("CustomArrayAdapter", "Delete icon clicked for item: $it")
                onItemDeleteRequested(it)
            }
        }
        // EditText 的焦点变化事件
        textView.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                // 当失去焦点时，检查是否进行过更改
                val updatedName = textView.text.toString()
                if (updatedName != item) {
                    selectedPosition?.let {
                        items[position] = updatedName  // 更新停靠点名称
                        onItemUpdated(position, updatedName)  // 通知更新
                    }
                }
                selectedPosition = null  // 重置选中状态
                notifyDataSetChanged()  // 刷新适配器
            }
        }
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent)
    }

    fun setSelectedPosition(position: Int) {
        selectedPosition = position
        notifyDataSetChanged()  // 刷新视图
    }

    fun setDropDownVisible(visible: Boolean) {
        isDropDownVisible = visible
        notifyDataSetChanged()
    }
}