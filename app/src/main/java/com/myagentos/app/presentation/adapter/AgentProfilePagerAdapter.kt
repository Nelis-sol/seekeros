package com.myagentos.app.presentation.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.myagentos.app.presentation.fragment.AboutTabFragment
import com.myagentos.app.presentation.fragment.WalletTabFragment
import com.myagentos.app.presentation.fragment.EmailTabFragment

/**
 * ViewPager adapter for Agent Profile tabs
 */
class AgentProfilePagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    
    override fun getItemCount(): Int = 3
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AboutTabFragment()
            1 -> WalletTabFragment()
            2 -> EmailTabFragment()
            else -> AboutTabFragment()
        }
    }
}
