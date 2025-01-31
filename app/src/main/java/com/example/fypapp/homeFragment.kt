package com.example.fypapp

import android.content.Context.SENSOR_SERVICE
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.database.*
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
    private lateinit var stepsTextView: TextView
    private lateinit var stepDetector: StepDetector

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        heartRateTextView = view.findViewById(R.id.heart_rate)
        spo2TextView = view.findViewById(R.id.spo2)
        stepsTextView = view.findViewById(R.id.steps)
        dateTextView = view.findViewById(R.id.dateTextView)
        weatherTextView = view.findViewById(R.id.weatherTextView)
        databaseRef = FirebaseDatabase.getInstance().getReference()

        readdata()
        displayCurrentDate()
        fetchWeatherData()

        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val sensorManager = requireActivity().getSystemService(SENSOR_SERVICE) as SensorManager
        stepDetector = StepDetector(requireContext(), sensorManager)
    }

    override fun onResume() {
        super.onResume()
        stepDetector.start()
        updateStepCount()
    }

    override fun onPause() {
        super.onPause()
        stepDetector.stop()
    }

    private fun updateStepCount() {
        stepsTextView.text = stepDetector.getStepCount().toString()
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
        databaseRef.child("heart_rate").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val heartRate = snapshot.getValue(Int::class.java)
                heartRateTextView.text = heartRate?.toString() ?: "N/A"
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        databaseRef.child("SPO2").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val spo2 = snapshot.getValue(Int::class.java)
                spo2TextView.text = spo2?.toString() ?: "N/A"
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}