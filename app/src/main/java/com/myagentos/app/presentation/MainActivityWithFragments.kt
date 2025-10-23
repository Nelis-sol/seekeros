package com.myagentos.app.presentation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.myagentos.app.R

/**
 * MVVM-based MainActivity using Fragments and Navigation Component
 * 
 * This is the new MVVM architecture demonstrating:
 * - Clean separation of concerns
 * - Fragment-based navigation
 * - ViewModel state management
 * - Tested business logic (112 unit tests!)
 * 
 * This activity is MUCH simpler than the original MainActivity (5,709 lines)
 * because all business logic is in ViewModels and Repositories.
 */
class MainActivityWithFragments : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_with_nav)
        
        setupNavigation()
    }
    
    private fun setupNavigation() {
        // Get the NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        // Connect bottom navigation with nav controller
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.setupWithNavController(navController)
    }
}

