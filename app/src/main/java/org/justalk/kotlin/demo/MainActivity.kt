package org.justalk.kotlin.demo

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import org.justalk.kotlin.demo.databinding.FragmentMainBinding
import org.justalk.kotlin.stdlib.activity.hideIme
import org.justalk.kotlin.stdlib.activity.setImmersiveMode
import org.justalk.kotlin.stdlib.activity.showIme
import org.justalk.kotlin.stdlib.app.viewDataBindingDelegate

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setImmersiveMode()
    }
}

class MainFragment : Fragment(R.layout.fragment_main) {

    private val binding: FragmentMainBinding by viewDataBindingDelegate()

    private var isSystemBarShowing = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.text1.setOnClickListener {
            if (isSystemBarShowing) {
                requireActivity().showIme()
            } else {
                requireActivity().hideIme()
            }
            isSystemBarShowing = !isSystemBarShowing
        }
    }

}