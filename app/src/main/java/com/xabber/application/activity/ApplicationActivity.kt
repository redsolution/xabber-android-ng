package com.xabber.application.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.xabber.R
import com.xabber.application.fragments.ChatFragment
import com.xabber.databinding.ActivityApplicationBinding

class ApplicationActivity : AppCompatActivity() {

    private var binding: ActivityApplicationBinding? = null
    lateinit var userName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApplicationBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        userName = intent.getStringExtra("key").toString()
        if (savedInstanceState == null) {
            startChatFragment()
        }
    }

    private fun startChatFragment() {
        supportFragmentManager.beginTransaction().add(R.id.application_container,
            ChatFragment.newInstance(userName)
        )
            .commit()
    }
}
