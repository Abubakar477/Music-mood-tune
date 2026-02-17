package com.abubakar.musicplayer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.abubakar.musicplayer.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle
    private var musicAdapter: MusicAdapter? = null // Change to nullable to prevent crash
    private lateinit var auth: FirebaseAuth
    private var searchView: SearchView? = null
    private var searchItem: MenuItem? = null
    private var isDetecting = false
    private var scanStartTime: Long = 0
    private var lastMood = ""
    private var stableMoodStartTime: Long = 0
    private var lastHappySongId = ""
    private var lastSadSongId = ""
    private var lastEnergeticSongId = ""
    private var lastCalmSongId = ""
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var originalBrightness: Float = -1f
    
    // Blinking detection variables
    private var lastBlinkTime: Long = 0
    private var blinkCount: Int = 0
    private var areEyesClosedPrev: Boolean = false

    companion object{
        lateinit var MusicListMA : ArrayList<Music>
        lateinit var musicListSearch : ArrayList<Music>
        var search: Boolean = false
        var themeIndex: Int = 0
        val currentTheme = arrayOf(R.style.coolPink, R.style.coolBlue, R.style.coolPurple, R.style.coolGreen, R.style.coolBlack)
        val currentThemeNav = arrayOf(R.style.coolPinkNav, R.style.coolBlueNav, R.style.coolPurpleNav, R.style.coolGreenNav,
            R.style.coolBlackNav)
        val currentGradient = arrayOf(R.drawable.gradient_pink, R.drawable.gradient_blue, R.drawable.gradient_purple, R.drawable.gradient_green,
        R.drawable.gradient_black)
        var sortOrder: Int = 0
        val sortingList = arrayOf(MediaStore.Audio.Media.DATE_ADDED + " DESC", MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.SIZE + " DESC")
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required for mood detection", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeEditor = getSharedPreferences("THEMES", MODE_PRIVATE)
        themeIndex = themeEditor.getInt("themeIndex", 0)
        setTheme(currentThemeNav[themeIndex])
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        //for nav drawer
        toggle = ActionBarDrawerToggle(this, binding.root,R.string.open, R.string.close)
        binding.root.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        //checking for dark theme
        if(themeIndex == 4 &&  resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_NO)
            Toast.makeText(this, "Black Theme Works Best in Dark Mode!!", Toast.LENGTH_LONG).show()

        if(requestRuntimePermission()){
            initializeLayout()
            //for retrieving favourites data using shared preferences
            FavouriteActivity.favouriteSongs = ArrayList()
            val editor = getSharedPreferences("FAVOURITES", MODE_PRIVATE)
            val jsonString = editor.getString("FavouriteSongs", null)
            val typeToken = object : TypeToken<ArrayList<Music>>(){}.type
            if(jsonString != null){
                val data: ArrayList<Music> = GsonBuilder().create().fromJson(jsonString, typeToken)
                FavouriteActivity.favouriteSongs.addAll(data)
            }
            PlaylistActivity.musicPlaylist = MusicPlaylist()
            val jsonStringPlaylist = editor.getString("MusicPlaylist", null)
            if(jsonStringPlaylist != null){
                val dataPlaylist: MusicPlaylist = GsonBuilder().create().fromJson(jsonStringPlaylist, MusicPlaylist::class.java)
                PlaylistActivity.musicPlaylist = dataPlaylist
            }
        }

        binding.shuffleBtn.setOnClickListener {
            val intent = Intent(this@MainActivity, PlayerActivity::class.java)
            intent.putExtra("index", 0)
            intent.putExtra("class", "MainActivity")
            startActivity(intent)
        }
        binding.favouriteBtn.setOnClickListener {
            startActivity(Intent(this@MainActivity, FavouriteActivity::class.java))
        }
        binding.playlistBtn.setOnClickListener {
            startActivity(Intent(this@MainActivity, PlaylistActivity::class.java))
        }
        binding.playNextBtn.setOnClickListener {
            startActivity(Intent(this@MainActivity, PlayNext::class.java))
        }
        binding.navView.setNavigationItemSelectedListener{
            when(it.itemId)
            {
                R.id.navHome -> {
                     binding.root.closeDrawer(GravityCompat.START)
                }
                R.id.navLibrary -> startActivity(Intent(this@MainActivity, PlaylistActivity::class.java))
                R.id.navFavourites -> startActivity(Intent(this@MainActivity, FavouriteActivity::class.java))
//                R.id.navFeedback -> startActivity(Intent(this@MainActivity, FeedbackActivity::class.java))
                R.id.navSettings -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                R.id.navAbout -> startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                R.id.navLogout -> {
                    auth.signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                R.id.navExit -> {
                    val builder = MaterialAlertDialogBuilder(this)
                    builder.setTitle("Exit")
                        .setMessage("Do you want to close app?")
                        .setPositiveButton("Yes"){ _, _ ->
                            exitApplication()
                        }
                        .setNegativeButton("No"){dialog, _ ->
                            dialog.dismiss()
                        }
                    val customDialog = builder.create()
                    customDialog.show()

                    setDialogBtnBackground(this, customDialog)
                }
            }
            true
        }

        binding.bottomNavigationView.setOnItemSelectedListener {
            when(it.itemId){
                R.id.bottom_nav_home -> {
                    // Already in MainActivity, do nothing or scroll to top
                    true
                }
                R.id.bottom_nav_search -> {
                    val item = searchItem
                    if (item != null) {
                        item.expandActionView()
                        searchView?.requestFocus()
                    } else {
                        Toast.makeText(this, "Search not available", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.bottom_nav_create -> {
                    val intent = Intent(this@MainActivity, PlaylistActivity::class.java)
                    intent.putExtra("show_create_dialog", true)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }
    //For requesting permission
    private fun requestRuntimePermission() :Boolean{
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU){
            if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 13)
                return false
            }
        }else{
            //android 13 or Higher permission request
            if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO), 13)
                return false
            }
        }
        return true
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == 13){
            if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, "Permission Granted",Toast.LENGTH_SHORT).show()
                initializeLayout()
            }
//            else ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 13)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(toggle.onOptionsItemSelected(item))
            return true
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("SetTextI18n")
    private fun initializeLayout(){
        search = false
        val sortEditor = getSharedPreferences("SORTING", MODE_PRIVATE)
        sortOrder = sortEditor.getInt("sortOrder", 0)
        MusicListMA = getAllAudio()
        binding.musicRV.setHasFixedSize(true)
        binding.musicRV.setItemViewCacheSize(13)
        binding.musicRV.layoutManager = LinearLayoutManager(this@MainActivity)
        musicAdapter = MusicAdapter(this@MainActivity, MusicListMA)
        binding.musicRV.adapter = musicAdapter
        binding.totalSongs.text  = "Total Songs : "+musicAdapter!!.itemCount

        //for refreshing layout on swipe from top
        binding.refreshLayout.setOnRefreshListener {
            MusicListMA = getAllAudio()
            musicAdapter?.updateMusicList(MusicListMA)

            binding.refreshLayout.isRefreshing = false
        }

        // Initialize Mood Controls
        val modeSwitch = findViewById<Switch>(R.id.modeSwitch)
        val moodSeekBar = findViewById<SeekBar>(R.id.moodSeekBar)

        modeSwitch.setOnCheckedChangeListener { _, isChecked ->
            MoodController.isAutoMode = isChecked
            moodSeekBar.isEnabled = !isChecked
            binding.btnDetectMood.isEnabled = isChecked
            if (!isChecked) {
                // If switching to manual, ensure UI reflects current manual setting
                // Or just keep the last set mood
            }
        }

        moodSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return

                val mood = when (progress) {
                    0 -> Mood.SAD
                    1 -> Mood.CALM
                    2 -> Mood.HAPPY
                    3 -> Mood.ENERGETIC
                    else -> Mood.HAPPY
                }

                MoodController.setManualMood(mood)
                applyMoodTheme(mood)
                playMoodMusic(mood)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnHappy.setOnClickListener { 
            // Manual buttons can override if in manual mode? 
            // Or force manual mode? Let's assume buttons are also manual triggers.
             if(!MoodController.isAutoMode) {
                 MoodController.setManualMood(Mood.HAPPY)
                 moodSeekBar.progress = 2
                 applyMoodTheme(Mood.HAPPY)
                 playMoodMusic(Mood.HAPPY)
             } else {
                 playMoodMusic(Mood.HAPPY) // Just play logic if in auto mode but user clicks?
                 // Or maybe we should prompt user to switch to manual mode.
                 Toast.makeText(this, "Switch to Manual Mode to select mood manually", Toast.LENGTH_SHORT).show()
             }
        }
        binding.btnSad.setOnClickListener { 
             if(!MoodController.isAutoMode) {
                 MoodController.setManualMood(Mood.SAD)
                 moodSeekBar.progress = 0
                 applyMoodTheme(Mood.SAD)
                 playMoodMusic(Mood.SAD)
             } else {
                 Toast.makeText(this, "Switch to Manual Mode to select mood manually", Toast.LENGTH_SHORT).show()
             }
        }
        binding.btnEnergetic.setOnClickListener { 
             if(!MoodController.isAutoMode) {
                 MoodController.setManualMood(Mood.ENERGETIC)
                 moodSeekBar.progress = 3
                 applyMoodTheme(Mood.ENERGETIC)
                 playMoodMusic(Mood.ENERGETIC)
             } else {
                 Toast.makeText(this, "Switch to Manual Mode to select mood manually", Toast.LENGTH_SHORT).show()
             }
        }

        binding.btnDetectMood.setOnClickListener { 
            if(MoodController.isAutoMode) {
                checkCameraPermissionAndOpen()
            } else {
                Toast.makeText(this, "Enable Auto Mood Detection first", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSwitchCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            if (isDetecting) {
                startCamera()
            }
        }
    }
    
    private fun applyMoodTheme(mood: Mood) {
        val theme = MoodController.moodThemes[mood] ?: return
        
        // Change background color of root view
        // Using window background or root layout background
        binding.root.setBackgroundColor(ContextCompat.getColor(this, theme.background))
        
        // Change toolbar/appbar color
        binding.appBarLayout.setBackgroundColor(ContextCompat.getColor(this, theme.accent))
        
        // Also update adapter to refresh card colors
        musicAdapter?.notifyDataSetChanged()
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        // Show the preview view
        binding.previewView.visibility = View.VISIBLE
        binding.tvMoodEmoji.visibility = View.GONE
        isDetecting = true
        scanStartTime = System.currentTimeMillis() // Start timer
        lastMood = ""
        stableMoodStartTime = 0
        blinkCount = 0
        areEyesClosedPrev = false
        lastBlinkTime = 0
        Toast.makeText(this, "Scanning face... Hold still!", Toast.LENGTH_SHORT).show()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)

            // ImageAnalysis
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null && isDetecting) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    detectEmotion(image, imageProxy, cameraProvider)
                } else {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
                
                if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    if (camera.cameraInfo.hasFlashUnit()) {
                        camera.cameraControl.enableTorch(true)
                    }
                } else {
                    // Front camera "flash" effect: Maximize screen brightness and set background to white
                    enableScreenFlash(true)
                }
                
            } catch (exc: Exception) {
                Toast.makeText(this, "Use camera failed", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun enableScreenFlash(enable: Boolean) {
        val layoutParams = window.attributes
        if (enable) {
            if (originalBrightness == -1f) {
                originalBrightness = layoutParams.screenBrightness
            }
            layoutParams.screenBrightness = 1.0f
            binding.root.setBackgroundColor(Color.WHITE)
        } else {
            layoutParams.screenBrightness = originalBrightness
            // Reset background to current theme or default
            val mood = MoodController.currentMood
            if (mood != null) {
                applyMoodTheme(mood)
            } else {
                binding.root.setBackgroundColor(ContextCompat.getColor(this, R.color.spotify_black))
            }
        }
        window.attributes = layoutParams
    }

    private fun detectEmotion(image: InputImage, imageProxy: androidx.camera.core.ImageProxy, cameraProvider: ProcessCameraProvider) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // Use FAST for real-time
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
            
        val detector = FaceDetection.getClient(options)
        
        detector.process(image)
            .addOnSuccessListener { faces ->
                if (!isDetecting) {
                    imageProxy.close()
                    return@addOnSuccessListener
                }

                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    val smileProb = face.smilingProbability ?: 0f
                    val leftEyeOpenProb = face.leftEyeOpenProbability ?: 0f
                    val rightEyeOpenProb = face.rightEyeOpenProbability ?: 0f
                    
                    var currentMood: Mood? = null
                    
                    // Blinking detection
                    val isEyesClosed = leftEyeOpenProb < 0.5 && rightEyeOpenProb < 0.5
                    
                    if (isEyesClosed && !areEyesClosedPrev) {
                        val now = System.currentTimeMillis()
                        // If previous blink was within 1000ms, count it
                        if (now - lastBlinkTime < 1000) {
                            blinkCount++
                        } else {
                            blinkCount = 1
                        }
                        lastBlinkTime = now
                    }
                    areEyesClosedPrev = isEyesClosed
                    
                    if (blinkCount >= 3) {
                         currentMood = Mood.ENERGETIC
                    } 
                    else if (smileProb > 0.75) {
                         currentMood = Mood.HAPPY
                    } 
                    else if (smileProb < 0.2) {
                         currentMood = Mood.SAD
                    }
                    
                    if (currentMood != null) {
                        stabilizeMood(currentMood, cameraProvider)
                    } else {
                        // Reset if mood is unstable or neutral (but don't reset blink variables here)
                        stableMoodStartTime = 0
                        lastMood = ""
                    }
                }
                imageProxy.close()
            }
            .addOnFailureListener {
                imageProxy.close()
            }
    }

    private fun stabilizeMood(mood: Mood, cameraProvider: ProcessCameraProvider) {
        if (mood == Mood.ENERGETIC) {
            // Immediate trigger for blinking sequence
            stopDetection(cameraProvider)
            handleMood(mood)
            return
        }

        if (mood.name == lastMood) {
            if (stableMoodStartTime == 0L) {
                stableMoodStartTime = System.currentTimeMillis()
            }
            val duration = System.currentTimeMillis() - stableMoodStartTime
            // Require 1 second (1000ms) of consistent mood
            if (duration >= 1000) {
                stopDetection(cameraProvider)
                handleMood(mood)
            }
        } else {
            lastMood = mood.name
            stableMoodStartTime = System.currentTimeMillis()
        }
    }
    
    private fun stopDetection(cameraProvider: ProcessCameraProvider) {
        isDetecting = false
        cameraProvider.unbindAll()
        binding.previewView.visibility = View.GONE
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            enableScreenFlash(false)
        }
    }

    private fun handleMood(mood: Mood) {
        MoodController.setAutoMood(mood)
        applyMoodTheme(mood)
        
        showMoodEmoji(mood.name)
        Toast.makeText(this, "Detected Mood: ${mood.name}", Toast.LENGTH_SHORT).show()
        playMoodMusic(mood)
        
        // Sync SeekBar UI
        val seekBar = findViewById<SeekBar>(R.id.moodSeekBar)
        when(mood) {
            Mood.SAD -> seekBar.progress = 0
            Mood.CALM -> seekBar.progress = 1
            Mood.HAPPY -> seekBar.progress = 2
            Mood.ENERGETIC -> seekBar.progress = 3
        }
    }

    private fun showMoodEmoji(mood: String) {
        val emojiText = when (mood) {
            "HAPPY" -> "😄"
            "SAD" -> "😢"
            "ENERGETIC" -> "⚡"
            else -> ""
        }
        
        binding.tvMoodEmoji.text = emojiText
        binding.tvMoodEmoji.visibility = View.VISIBLE
        binding.tvMoodEmoji.alpha = 0f
        binding.tvMoodEmoji.scaleX = 0.5f
        binding.tvMoodEmoji.scaleY = 0.5f

        binding.tvMoodEmoji.animate()
            .alpha(1f)
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(500)
            .withEndAction {
                binding.tvMoodEmoji.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(0f)
                    .setStartDelay(1000)
                    .setDuration(500)
                    .withEndAction {
                        binding.tvMoodEmoji.visibility = View.GONE
                    }
            }
    }
    
    private fun playMoodMusic(mood: Mood) {
        if (musicAdapter == null) return // Safety check

        when(mood) {
            Mood.HAPPY -> playHappySongs()
            Mood.SAD -> playSadSongs()
            Mood.ENERGETIC -> playEnergeticSongs()
            Mood.CALM -> playCalmSongs()
        }
    }

    private fun playHappySongs() {
        musicListSearch = ArrayList(MusicListMA.filter { it.duration < 240000 })
        if (musicListSearch.isNotEmpty()) {
            musicListSearch.shuffle()
            
            // Ensure different song if possible
            if (musicListSearch.size > 1 && musicListSearch[0].id == lastHappySongId) {
                val temp = musicListSearch[0]
                musicListSearch[0] = musicListSearch[1]
                musicListSearch[1] = temp
            }
            lastHappySongId = musicListSearch[0].id
            
            search = true
            musicAdapter?.updateMusicList(musicListSearch)
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("index", 0)
            intent.putExtra("class", "MusicAdapterSearch")
            startActivity(intent)
        } else {
            Toast.makeText(this, "No happy songs found!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playSadSongs() {
        musicListSearch = ArrayList(MusicListMA.filter { it.duration >= 240000 })
        if (musicListSearch.isNotEmpty()) {
            musicListSearch.shuffle()
            
            if (musicListSearch.size > 1 && musicListSearch[0].id == lastSadSongId) {
                val temp = musicListSearch[0]
                musicListSearch[0] = musicListSearch[1]
                musicListSearch[1] = temp
            }
            lastSadSongId = musicListSearch[0].id
            
            search = true
            musicAdapter?.updateMusicList(musicListSearch)
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("index", 0)
            intent.putExtra("class", "MusicAdapterSearch")
            startActivity(intent)
        } else {
            Toast.makeText(this, "No sad songs found!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playEnergeticSongs() {
        musicListSearch = ArrayList(MusicListMA)
        musicListSearch.shuffle()
        if (musicListSearch.isNotEmpty()) {
            if (musicListSearch.size > 1 && musicListSearch[0].id == lastEnergeticSongId) {
                val temp = musicListSearch[0]
                musicListSearch[0] = musicListSearch[1]
                musicListSearch[1] = temp
            }
            lastEnergeticSongId = musicListSearch[0].id

            search = true
            musicAdapter?.updateMusicList(musicListSearch)
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("index", 0)
            intent.putExtra("class", "MusicAdapterSearch")
            startActivity(intent)
        }
    }

    private fun playCalmSongs() {
        musicListSearch = ArrayList(MusicListMA.filter { it.duration > 180000 })
        if (musicListSearch.isNotEmpty()) {
            // Calm logic - similar to Sad but maybe different filter? 
            // Existing logic was > 180000 (3 mins)
            musicListSearch.shuffle()
             if (musicListSearch.size > 1 && musicListSearch[0].id == lastCalmSongId) {
                val temp = musicListSearch[0]
                musicListSearch[0] = musicListSearch[1]
                musicListSearch[1] = temp
            }
            lastCalmSongId = musicListSearch[0].id
            
            search = true
            musicAdapter?.updateMusicList(musicListSearch)
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("index", 0)
            intent.putExtra("class", "MusicAdapterSearch")
            startActivity(intent)
        } else {
            Toast.makeText(this, "No calm songs found!", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("Recycle", "Range")
    private fun getAllAudio(): ArrayList<Music> {
        val tempList = ArrayList<Music>()

        // Filter Only Music or Audio Files
        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " + MediaStore.Audio.Media.MIME_TYPE + " LIKE 'audio/%'"
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val cursor = this.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null,
            sortingList[sortOrder], null
        )
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    val titleC = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)) ?: "Unknown"
                    val idC = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media._ID)) ?: "Unknown"
                    val albumC = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)) ?: "Unknown"
                    val artistC = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)) ?: "Unknown"
                    val pathC = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                    val durationC = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION))
                    val albumIdC = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)).toString()
                    val uri = Uri.parse("content://media/external/audio/albumart")
                    val artUriC = Uri.withAppendedPath(uri, albumIdC).toString()

                    // Only add the music file if the duration is greater than 31 seconds (31000 ms)
                    if (durationC > 31000) {
                        val music = Music(
                            id = idC,
                            title = titleC,
                            album = albumC,
                            artist = artistC,
                            path = pathC,
                            duration = durationC,
                            artUri = artUriC
                        )

                        if (File(music.path).exists()) tempList.add(music)
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
        }
        return tempList
    }


    override fun onDestroy() {
        super.onDestroy()
        if(!PlayerActivity.isPlaying && PlayerActivity.musicService != null){
           exitApplication()
        }
    }

    override fun onResume() {
        super.onResume()

        //for storing favourites data using shared preferences
        val editor = getSharedPreferences("FAVOURITES", MODE_PRIVATE).edit()
        val jsonString = GsonBuilder().create().toJson(FavouriteActivity.favouriteSongs)
        editor.putString("FavouriteSongs", jsonString)
        val jsonStringPlaylist = GsonBuilder().create().toJson(PlaylistActivity.musicPlaylist)
        editor.putString("MusicPlaylist", jsonStringPlaylist)
        editor.apply()

        //for sorting
        val sortEditor = getSharedPreferences("SORTING", MODE_PRIVATE)
        val sortValue = sortEditor.getInt("sortOrder", 0)
        if(sortOrder != sortValue){
            sortOrder = sortValue
            MusicListMA = getAllAudio()
            musicAdapter?.updateMusicList(MusicListMA)
        }
        if(PlayerActivity.musicService != null) binding.nowPlaying.visibility = View.VISIBLE

        binding.bottomNavigationView.selectedItemId = R.id.bottom_nav_home
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.search_view_menu, menu)
        //for setting gradient
        findViewById<LinearLayout>(R.id.linearLayoutNav)?.setBackgroundResource(currentGradient[themeIndex])

        searchItem = menu?.findItem(R.id.searchView)
        searchView = searchItem?.actionView as SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                musicListSearch = ArrayList()
                if(newText != null){
                    val userInput = newText.lowercase()
                    for (song in MusicListMA)
                        if(song.title.lowercase().contains(userInput))
                            musicListSearch.add(song)
                    search = true
                    musicAdapter?.updateMusicList(searchList = musicListSearch)
                }
                return true
            }
        })
        return super.onCreateOptionsMenu(menu)
    }
}