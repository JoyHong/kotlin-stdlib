package org.justalk.kotlin.demo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import org.justalk.kotlin.demo.databinding.FragmentMainBinding
import org.justalk.kotlin.stdlib.app.viewDataBindingDelegate

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}

class MainFragment : Fragment(R.layout.fragment_main) {

    private val binding: FragmentMainBinding by viewDataBindingDelegate()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.text1.text = "Hello viewDataBindingDelete"
    }

}