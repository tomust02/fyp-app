package com.example.fypapp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class HomeFragment : Fragment() {

    private lateinit var databaseRef: DatabaseReference
    private lateinit var heartRateTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout and initialize the view object
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize heartRateTextView and databaseRef
        heartRateTextView = view.findViewById(R.id.heart_rate)
        databaseRef = FirebaseDatabase.getInstance().getReference()

        // Call readdata function to read data from Firebase
        readdata()

        // Return the inflated view
        return view
    }

    private fun readdata() {
        databaseRef.child("heart_rate").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val heartRate = snapshot.getValue(String::class.java)
                heartRateTextView.text = "$heartRate"
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle possible errors.
            }
        })
    }
}