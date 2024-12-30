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

data class WeatherResponse(val main: Main)
data class Main(val temp: Float)

interface WeatherService {
    @GET("weather")
    fun getCurrentWeather(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): Call<WeatherResponse>
}

class HomeFragment : Fragment() {

    private lateinit var databaseRef: DatabaseReference
    private lateinit var heartRateTextView: TextView
    private lateinit var spo2TextView: TextView

    private lateinit var dateTextView: TextView
    private lateinit var weatherTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout and initialize the view object
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize heartRateTextView, spo2TextView, and databaseRef
        heartRateTextView = view.findViewById(R.id.heart_rate)
        spo2TextView = view.findViewById(R.id.spo2)
        databaseRef = FirebaseDatabase.getInstance().getReference()

        dateTextView = view.findViewById(R.id.dateTextView)
        weatherTextView = view.findViewById(R.id.weatherTextView)


        // Call readdata function to read data from Firebase
        readdata()
        displayCurrentDate()
        fetchWeatherData()

        // Return the inflated view
        return view
    }

    private fun displayCurrentDate() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = sdf.format(Date())
        dateTextView.text = currentDate
    }

    private fun fetchWeatherData() {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val service = retrofit.create(WeatherService::class.java)
        val call = service.getCurrentWeather("Hong Kong", "b805632551d998b45f0ae42b24e31c63")

        call.enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                if (response.isSuccessful) {
                    val weatherResponse = response.body()
                    weatherTextView.text = "${weatherResponse?.main?.temp}°C"
                } else {
                    weatherTextView.text = "Failed to get weather data"
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                weatherTextView.text = "Failed to get weather data"
            }
        })
    }

    private fun readdata() {
        // Read heart rate data
        databaseRef.child("heart_rate").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val heartRate = snapshot.getValue(Int::class.java)
                heartRateTextView.text = heartRate?.toString() ?: "N/A"
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle possible errors.
            }
        })

        // Read spo2 data
        databaseRef.child("SPO2").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val spo2 = snapshot.getValue(Int::class.java)
                spo2TextView.text = spo2?.toString() ?: "N/A"
            }
            override fun onCancelled(error: DatabaseError) {
                // Handle possible errors.
            }
        })
    }
}