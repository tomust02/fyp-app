package com.example.fypapp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter


class TabPageAdapter (activity: FragmentActivity, private  val tabCount: Int): FragmentStateAdapter(activity){

    override fun getItemCount(): Int{
        return tabCount
    }
    override  fun createFragment(position:Int):Fragment{
        return  when (position){
            0->HomeFragment()
            1->TestFragment()
            2->HeartHealthFragment()
            else ->HomeFragment()
        }
    }
}