package com.mindisle.app.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mindisle.app.R
import com.mindisle.app.utils.StatusBarUtils

class DiagnosisActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StatusBarUtils.applyLightStatusBar(this)
        setContentView(R.layout.activity_diagnosis)
        bindBottomNavigation()
    }

    private fun bindBottomNavigation() {
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNavigationView.selectedItemId = R.id.nav_diagnosis
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_companion -> {
                    startActivity(Intent(this, CompanionActivity::class.java))
                    true
                }
                R.id.nav_exchange -> {
                    startActivity(Intent(this, CompanionActivity::class.java).apply {
                        putExtra(CompanionActivity.EXTRA_INITIAL_TAB, CompanionActivity.TAB_EXCHANGE)
                    })
                    true
                }
                R.id.nav_diagnosis -> true
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }
}
