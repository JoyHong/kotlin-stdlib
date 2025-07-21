package org.justalk.kotlin.demo

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import org.justalk.kotlin.demo.databinding.FragmentMainBinding
import org.justalk.kotlin.stdlib.activity.setImmersiveMode
import org.justalk.kotlin.stdlib.app.viewDataBindingDelegate

class MainActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setImmersiveMode()
    }
}

class MainFragment : Fragment(R.layout.fragment_main) {

    private val binding: FragmentMainBinding by viewDataBindingDelegate()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

}