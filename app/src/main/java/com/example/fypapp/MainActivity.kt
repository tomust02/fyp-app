package com.example.fypapp

import android.content.ContentValues.TAG
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.fypapp.databinding.ActivityMainBinding
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*

data class HeartRateData(val heart_rate: String = "")


class MainActivity() : AppCompatActivity() {


//    private lateinit var appBarConfiguration: AppBarConfiguration
     private lateinit var binding: ActivityMainBinding
    
    private  lateinit var databaseRef : DatabaseReference
    private lateinit var heartRateTextView: TextView



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //real time
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        setupTabBar()
//        simulateHealthData()


//        //Firestore
//        val db=FirebaseFirestore.getInstance()
//        //declare the animation
//        val heart_rate = findViewById(R.id.heart_rate) as TextView
//
//        val docRef = db.collection("sensors").document("user")
//        docRef.get()
//            .addOnSuccessListener { document ->
//                if (document != null) {
//                    Log.d("exist", "DocumentSnapshot data: ${document.data}")
//
//                    heart_rate.text = document.getString("heart_rate")
//                } else {
//                    Log.d("noexist", "No such document")
//                }
//            }
//            .addOnFailureListener { exception ->
//                Log.d("errordb", "get failed with ", exception)
//            }

    }



    private fun setupTabBar() {
        val adapter = TabPageAdapter(this, 4)
        binding.viewPager.adapter = adapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Home"
                1 -> "posture"
                2 -> "Heart Health"
                3 -> "Heart Data"
                else -> "Home"
            }
        }.attach()
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.tabLayout.selectTab(binding.tabLayout.getTabAt(position))
            }
        })

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                binding.viewPager.currentItem = tab.position
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // No action needed
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // No action needed
            }
        })
    }

//    fun simulateHealthData() {
//        val databaseRef = FirebaseDatabase.getInstance().getReference()
//        val timer = java.util.Timer()
//        timer.schedule(object : java.util.TimerTask() {
//            override fun run() {
//                val heartRate = (60 + java.util.Random().nextInt(120)) // Random heart rate between 60 and 180
//                val spo2 = (90 + java.util.Random().nextInt(10)) // Random SpO2 between 90 and 100
//                databaseRef.child("heart_rate").setValue(heartRate)
//                databaseRef.child("SPO2").setValue(spo2)
//            }
//        }, 0, 2000) // Update every 2 seconds
//    }




}

