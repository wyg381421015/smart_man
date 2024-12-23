package com.wyg.smart_man.activity

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.wyg.smart_man.R

class CustomArrayAdapter(context: Context, private val items: List<String>) : ArrayAdapter<String>(context, R.layout.custom_spinner_item, items) {

    private var selectedPosition = 0  // 记录当前选中的位置
    private var isDropDownVisible = true  // 控制下拉菜单是否显示

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.custom_spinner_item, parent, false)

        // 找到 TextView
        val textView: TextView = view.findViewById(R.id.text)
        val arrowImageView: ImageView = view.findViewById(R.id.arrow)

        textView.text = getItem(position)

        // 控制箭头的可见性
        // 控制箭头的可见性
        arrowImageView.visibility = if (isDropDownVisible) View.VISIBLE else if (position == selectedPosition) View.VISIBLE else View.GONE

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