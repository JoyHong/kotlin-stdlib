package org.justalk.kotlin.stdlib.view

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
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

/**
 * LinearLayoutManager 的 RecyclerView 的分割线
 * @param offsets   分割线的高度
 * @param color     分割线的颜色, 默认是透明的
 * @param alignTop  分割线的位置是否在 item 控件的上方, 默认是在下方
 */
open class LinearItemDecoration(
    private val offsets: Int,
    private val color: Int? = null,
    private val alignTop: Boolean = false
) : RecyclerView.ItemDecoration() {

    final override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (!onInterceptItemOffsets(view, parent)) {
            if (alignTop) {
                outRect.top = offsets
            } else {
                outRect.bottom = offsets
            }
        }
    }

    private val paint by lazy {
        color?.let { color1 ->
            Paint().also { paint1 ->
                paint1.color = color1
                paint1.isAntiAlias = true
            }
        }
    }

    final override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        paint?.let { paint1 ->
            val left = parent.paddingLeft
            val right = parent.width - parent.paddingRight
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (!onInterceptItemColor(child, parent)) {
                    val params = child.layoutParams as RecyclerView.LayoutParams
                    val top: Int
                    val bottom: Int
                    if (alignTop) {
                        bottom = child.top - params.topMargin
                        top = bottom - offsets
                    } else {
                        top = child.bottom + params.bottomMargin
                        bottom = top + offsets
                    }
                    canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint1)
                }
            }
        }
    }

    final override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)
    }

    open fun onInterceptItemOffsets(view: View, parent: RecyclerView): Boolean {
        return false
    }

    open fun onInterceptItemColor(view: View, parent: RecyclerView): Boolean {
        return false
    }

}