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
import com.example.fypapp.databinding.ActivityMainBinding
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

data class HeartRateData(val heart_rate: String = "")

class MainActivity() : AppCompatActivity() {


//    private lateinit var appBarConfiguration: AppBarConfiguration
     private lateinit var binding: ActivityMainBinding
    
    private  lateinit var databaseRef : DatabaseReference
    private lateinit var heartRateTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //such that you can show text view
        //setContentView(R.layout.activity_main);

        //real time
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        databaseRef = FirebaseDatabase.getInstance().getReference()
        heartRateTextView = findViewById(R.id.heart_rate)

        //test firebase connection
//        databaseRef = FirebaseDatabase.getInstance().getReference("test")
//
//        binding.hello.setOnClickListener{
//            databaseRef.setValue("test123 ok")
//                .addOnSuccessListener {
//                    Toast.makeText(this,"data stroed seccessfully", Toast.LENGTH_LONG)
//                }
//        }

        readdate()



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

    private fun readdate(){
        databaseRef.child("heart_rate").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val heartRate = snapshot.getValue(String::class.java)
                heartRateTextView.text = "$heartRate bpm"
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle possible errors.
            }
        })
    }





    // look at this section
//    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
//        val inflater: MenuInflater = menuInflater
//        inflater.inflate(R.menu.my_menu, menu)
//        return super.onCreateOptionsMenu(menu)
//    }
//    override fun onCreateOptionsMenu(menu: Menu): Boolean {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        menuInflater.inflate(R.menu.menu_main, menu)
//        return true
//    }
//
//    override fun onOptionsItemSelected(item: MenuItem): Boolean {
//        // Handle action bar item clicks here. The action bar will
//        // automatically handle clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        return when (item.itemId) {
//            R.id.action_settings -> true
//            else -> super.onOptionsItemSelected(item)
//        }
//    }

//    override fun onSupportNavigateUp(): Boolean {
//        val navController = findNavController(R.id.nav_host_fragment_content_main)
//        return navController.navigateUp(appBarConfiguration)
//                || super.onSupportNavigateUp()
//    }
}

