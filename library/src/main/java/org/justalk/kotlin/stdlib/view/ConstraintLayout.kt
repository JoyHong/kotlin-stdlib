package org.justalk.kotlin.stdlib.view

import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet

inline fun ConstraintLayout.updateConstraintSet(block: ConstraintSet.() -> Unit) {
    ConstraintSet().let { constraintSet ->
        constraintSet.clone(this)
        block(constraintSet)
        constraintSet.applyTo(this)
    }
}