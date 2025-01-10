package org.justalk.kotlin.stdlib.view

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet

inline fun ConstraintLayout.updateConstraintSet(block: ConstraintSet.() -> Unit) {
    ConstraintSet().let { constraintSet ->
        constraintSet.clone(this)
        block(constraintSet)
        constraintSet.applyTo(this)
    }
}

fun ConstraintLayout.expandChildView(childView: View) {
    updateConstraintSet {
        connect(childView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        connect(childView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        connect(childView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        connect(childView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
    }
}