package org.justalk.kotlin.stdlib.view

import android.view.View
import androidx.recyclerview.widget.RecyclerView

// 一般用在聊天列表, 并且 RecyclerView 的 xml 设置 reverseLayout 为 true
fun RecyclerView.applyVerticalTranslation() {
    fun setListVerticalTranslation() {
        if (canScrollVertically(1) || canScrollVertically(-1) || childCount == 0) {
            translationY = 0f
            overScrollMode = RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS
        } else {
            val chTop = getChildAt(childCount - 1).top
            translationY = 0.coerceAtMost(-chTop).toFloat()
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
        }
    }
    // 监听列表布局变化和 item 变化，设置 RecyclerView 的垂直平移距离。当 item 未占满 RecyclerView 时，设置垂直偏移来使 item 置顶显示。
    // RecyclerView 设置高度 wrap_content 时，滑动列表时偶现列表异常跳动，因此使用 match_parent 并结合列表的监听来处理。
    addOnLayoutChangeListener { _: View, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int, _: Int ->
        setListVerticalTranslation()
    }
    addOnChildAttachStateChangeListener(object :
        RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) {
            setListVerticalTranslation()
        }

        override fun onChildViewDetachedFromWindow(view: View) {
            setListVerticalTranslation()
        }
    })
}