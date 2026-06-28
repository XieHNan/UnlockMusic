package com.unlockmusic.android.ui

import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.unlockmusic.android.R
import com.unlockmusic.android.decrypt.MusicDecryptor
import com.unlockmusic.android.model.MusicFile
import com.unlockmusic.android.util.AudioUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: com.unlockmusic.android.databinding.ActivityMainBinding
    private val adapter = MusicAdapter()
    private val musicFiles = mutableListOf<MusicFile>()

    // 播放器
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlaying: MusicFile? = null
    private var isMuted = false
    private var isUserSeeking = false
    private val seekHandler = Handler(Looper.getMainLooper())
    private var seekRunnable: Runnable? = null
    private var playTempFile: File? = null

    // 设置
    private var outputPathType = "downloads"
    private var customOutputPath = ""
    private var duplicateStrategy = "copy"
    private var folderNamePattern = ""
    private var seedIndex = 5  // 默认蓝色 (seed_06)
    private var lastSaveTime = 0L  // 保存冷却时间（1秒）
    private var fileManagerType = "documents"  // 文件管理器: documents / system

    // 文件选择器（支持多选 + 可配置类型）
    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uris = mutableListOf<Uri>()
            result.data?.data?.let { uris.add(it) }
            result.data?.clipData?.let { for (i in 0 until it.itemCount) uris.add(it.getItemAt(i).uri) }
            uris.forEach { addFile(it) }
            refreshList()
        }
    }

    private fun openFilePicker() {
        val intent = if (fileManagerType == "documents") {
            // 与 KernelSU 完全一致：ACTION_GET_CONTENT + application/octet-stream，仅增加了多选
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        } else {
            // 系统文件选择器：*/* 会交给手机 OS，小米设备上会调起小米文件管理器
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }
        filePicker.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadPreferences()
        theme.applyStyle(seedOverlayRes(seedIndex), true)
        binding = com.unlockmusic.android.databinding.ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // ==================== 初始化 ====================

    private fun initViews() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        adapter.onPlayClick = { file -> playAudio(file) }
        adapter.onSaveClick = { file -> saveSingle(file) }
        adapter.onReDecrypt = { file -> file.reset(); adapter.submitList(musicFiles.toList()); autoDecrypt(file) }
        adapter.onRename = { file, name -> file.displayName = name; adapter.submitList(musicFiles.toList()); toast(getString(R.string.toast_renamed, name)) }
        adapter.onRemove = { file -> musicFiles.remove(file); refreshList(); toast(R.string.toast_removed) }

        binding.btnSelect.setOnClickListener { openFilePicker() }
        binding.btnSaveAll.setOnClickListener { saveAll() }
        binding.layoutEmpty.setOnClickListener { openFilePicker() }
        binding.btnSettings.setOnClickListener { showSettingsDialog() }

        initPlayerBar()
        updateEmptyState()
    }

    // ==================== Intent 处理 ====================

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { addFile(it) }
                refreshList()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.forEach { addFile(it) }
                refreshList()
            }
        }
    }

    // ==================== 文件选择 ====================

    private fun addFile(uri: Uri) {
        val name = getFileName(uri) ?: return
        if (MusicDecryptor.isSupportedFormat(name)) {
            val file = MusicFile(name = name, path = uri.toString())
            musicFiles.add(file)
            autoDecrypt(file)  // 导入即解密
        } else {
            toast(getString(R.string.toast_unsupported, name))
        }
    }

    private fun refreshList() {
        adapter.submitList(musicFiles.toList())
        updateEmptyState()
        updateStatusCount()
    }

    private fun updateEmptyState() {
        binding.layoutEmpty.visibility = if (musicFiles.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (musicFiles.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateStatusCount() {
        val decrypted = musicFiles.count { it.isDecrypted }
        binding.tvStatus.text = if (musicFiles.isEmpty()) {
            getString(R.string.status_select_file)
        } else {
            getString(R.string.status_count_decrypted, decrypted, musicFiles.size)
        }
    }

    // ==================== 自动解密（导入即解密） ====================

    private fun autoDecrypt(file: MusicFile) {
        file.status = getString(R.string.status_decrypting_single)
        adapter.submitList(musicFiles.toList())
        updateStatusCount()

        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { readFileData(Uri.parse(file.path)) }
                if (data != null) {
                    val result = MusicDecryptor.decrypt(data, file.name)
                    if (result.success && result.data != null) {
                        file.decryptedData = result.data
                        file.decryptedExt = result.ext
                        file.displayName = AudioUtils.removeExtension(result.filename)
                        file.status = getString(R.string.status_decrypted)
                        file.isDecrypted = true
                    } else {
                        file.status = "失败: ${result.message}"
                    }
                } else {
                    file.status = getString(R.string.status_read_failed)
                }
            } catch (e: Exception) {
                file.status = "错误: ${e.message}"
            }
            adapter.submitList(musicFiles.toList())
            updateStatusCount()
        }
    }

    // ==================== 保存 ====================

    /** 单个文件保存 */
    private fun saveSingle(file: MusicFile) {
        if (!checkSaveCooldown()) return
        val data = file.decryptedData ?: return
        val filename = "${file.displayName}.${file.decryptedExt}"
        lifecycleScope.launch {
            val ok = saveDecrypted(data, filename, file.decryptedExt, "")
            toast(if (ok) R.string.toast_save_success else R.string.toast_save_failed)
        }
    }

    /** 批量保存到带时间戳的子文件夹 */
    private fun saveAll() {
        if (!checkSaveCooldown()) return
        val decrypted = musicFiles.filter { it.isDecrypted && it.decryptedData != null }
        if (decrypted.isEmpty()) { toast(R.string.toast_no_decrypted); return }

        val subfolder = generateFolderName()
        lifecycleScope.launch {
            var success = 0
            var fail = 0
            decrypted.forEach { file ->
                val filename = "${file.displayName}.${file.decryptedExt}"
                if (saveDecrypted(file.decryptedData!!, filename, file.decryptedExt, subfolder)) success++ else fail++
            }
            toast(getString(R.string.toast_batch_save_result, success, fail))
        }
    }

    /** 保存冷却（1秒），防止连续点击重复保存 */
    private fun checkSaveCooldown(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastSaveTime < 1000) {
            toast(R.string.toast_save_cooldown)
            return false
        }
        lastSaveTime = now
        return true
    }

    /** 按用户模板生成子文件夹名，支持占位符 {date} {time} */
    private fun generateFolderName(): String {
        val now = Date()
        val date = SimpleDateFormat("yy年M月d日", Locale.getDefault()).format(now)
        val time = SimpleDateFormat("H点mm分ss秒", Locale.getDefault()).format(now)
        val pattern = folderNamePattern.ifEmpty { getString(R.string.settings_naming_default) }
        return pattern.replace("{date}", date).replace("{time}", time)
    }

    /** 核心保存逻辑：写入输出目录，subfolder 非空时存到子文件夹 */
    private suspend fun saveDecrypted(data: ByteArray, filename: String, ext: String, subfolder: String): Boolean {
        return withContext(Dispatchers.IO) {
            when {
                outputPathType == "custom" -> {
                    val dir = if (subfolder.isEmpty()) File(customOutputPath) else File(customOutputPath, subfolder)
                    if (!dir.exists()) dir.mkdirs()
                    saveToFile(data, resolveDuplicateFile(filename, dir), dir)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    val baseRel = if (outputPathType == "music") "Music/UnlockMusic" else "${Environment.DIRECTORY_DOWNLOADS}/UnlockMusic"
                    val relPath = if (subfolder.isEmpty()) baseRel else "$baseRel/$subfolder"
                    saveToMediaStore(data, resolveDuplicateMedia(filename, relPath), ext, relPath)
                }
                else -> {
                    val baseDir = if (outputPathType == "music")
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "UnlockMusic")
                    else
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "UnlockMusic")
                    val dir = if (subfolder.isEmpty()) baseDir else File(baseDir, subfolder)
                    if (!dir.exists()) dir.mkdirs()
                    saveToFile(data, resolveDuplicateFile(filename, dir), dir)
                }
            }
        }
    }

    private fun saveToFile(data: ByteArray, filename: String, dir: File): Boolean {
        return try {
            FileOutputStream(File(dir, filename)).use { it.write(data) }
            true
        } catch (e: IOException) { Log.e(TAG, "saveToFile", e); false }
    }

    private fun saveToMediaStore(data: ByteArray, filename: String, ext: String, relPath: String): Boolean {
        return try {
            // 覆盖策略：先删除同名旧文件
            if (duplicateStrategy == "overwrite") deleteInMediaStore(filename, getMediaCollection(), relPath)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, AudioUtils.getMimeType(ext))
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            }
            val uri = contentResolver.insert(getMediaCollection(), values) ?: return false
            contentResolver.openOutputStream(uri)?.use { it.write(data); it.flush() } ?: return false
            true
        } catch (e: IOException) { Log.e(TAG, "saveToMediaStore", e); false }
    }

    // ==================== 同名文件处理（静默） ====================

    private fun getMediaCollection(): Uri =
        if (outputPathType == "music") MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Downloads.EXTERNAL_CONTENT_URI

    private fun resolveDuplicateFile(filename: String, dir: File): String {
        if (duplicateStrategy == "overwrite") return filename
        if (!File(dir, filename).exists()) return filename
        val (base, ext) = splitExt(filename)
        var i = 1
        var name: String
        do { name = "${base}_$i$ext"; i++ } while (File(dir, name).exists())
        return name
    }

    private fun resolveDuplicateMedia(filename: String, relPath: String): String {
        if (duplicateStrategy == "overwrite") return filename
        if (!fileExistsInMedia(filename, relPath)) return filename
        val (base, ext) = splitExt(filename)
        var i = 1
        var name: String
        do { name = "${base}_$i$ext"; i++ } while (fileExistsInMedia(name, relPath))
        return name
    }

    private fun splitExt(filename: String): Pair<String, String> {
        val dot = filename.lastIndexOf('.')
        return if (dot > 0) filename.substring(0, dot) to filename.substring(dot) else filename to ""
    }

    private fun fileExistsInMedia(filename: String, relPath: String): Boolean {
        val sel = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        contentResolver.query(getMediaCollection(), arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), sel, arrayOf(filename, "$relPath/"), null)?.use { return it.count > 0 }
        return false
    }

    private fun deleteInMediaStore(filename: String, collection: Uri, relPath: String) {
        val sel = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), sel, arrayOf(filename, "$relPath/"), null)?.use {
            val idIdx = it.getColumnIndex(MediaStore.MediaColumns._ID)
            while (it.moveToNext()) {
                contentResolver.delete(ContentUris.withAppendedId(collection, it.getLong(idIdx)), null, null)
            }
        }
    }

    // ==================== 文件读取 ====================

    private fun readFileData(uri: Uri): ByteArray? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                val baos = java.io.ByteArrayOutputStream()
                var n: Int
                while (stream.read(buffer).also { n = it } != -1) baos.write(buffer, 0, n)
                baos.toByteArray()
            }
        } catch (e: IOException) { Log.e(TAG, "readFileData", e); null }
    }

    // ==================== 播放器 ====================

    private fun initPlayerBar() {
        binding.layoutPlayerBar.seekbarPlayer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mediaPlayer?.let { mp -> if (mp.duration > 0) mp.seekTo(progress * mp.duration / 1000) }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { isUserSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) { isUserSeeking = false }
        })

        binding.layoutPlayerBar.btnPlayPause.setOnClickListener {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) { mp.pause(); binding.layoutPlayerBar.tvPlayIcon.text = "▶" }
                else { mp.start(); binding.layoutPlayerBar.tvPlayIcon.text = "⏸" }
            }
        }

        binding.layoutPlayerBar.btnMute.setOnClickListener {
            mediaPlayer?.let { mp ->
                isMuted = !isMuted
                if (isMuted) { mp.setVolume(0f, 0f); binding.layoutPlayerBar.tvMuteIcon.text = "🔇" }
                else { mp.setVolume(1f, 1f); binding.layoutPlayerBar.tvMuteIcon.text = "🔊" }
            }
        }

        binding.layoutPlayerBar.btnClosePlayer.setOnClickListener { stopPlayback() }
    }

    private fun playAudio(file: MusicFile) {
        mediaPlayer?.let { it.stop(); it.release() }
        mediaPlayer = null
        playTempFile?.delete()
        playTempFile = null

        if (file == currentlyPlaying) { currentlyPlaying = null; stopPlayback(); return }

        val data = file.decryptedData ?: return
        try {
            // 写临时文件用于播放
            playTempFile = File(cacheDir, "play_${System.currentTimeMillis()}.${file.decryptedExt}")
            playTempFile!!.writeBytes(data)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(playTempFile!!.absolutePath)
                setOnPreparedListener { mp -> mp.start(); currentlyPlaying = file; showPlayerBar(file) }
                setOnCompletionListener { stopPlayback() }
                prepareAsync()
            }
        } catch (e: IOException) { toast(getString(R.string.toast_play_failed, e.message)); Log.e(TAG, "playAudio", e) }
    }

    private fun showPlayerBar(file: MusicFile) {
        binding.layoutPlayerBar.apply {
            tvPlayingName.text = file.displayName
            tvPlayIcon.text = "⏸"
            tvMuteIcon.text = "🔊"
            seekbarPlayer.progress = 0
            tvCurrentTime.text = "00:00"
            tvTotalTime.text = "00:00"
            root.visibility = View.VISIBLE
        }
        isMuted = false
        startSeekBarUpdate()
    }

    private fun startSeekBarUpdate() {
        seekRunnable?.let { seekHandler.removeCallbacks(it) }
        seekRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying && !isUserSeeking) {
                        val total = mp.duration
                        val cur = mp.currentPosition
                        binding.layoutPlayerBar.seekbarPlayer.max = 1000
                        binding.layoutPlayerBar.seekbarPlayer.progress = if (total > 0) cur * 1000 / total else 0
                        binding.layoutPlayerBar.tvCurrentTime.text = AudioUtils.formatTime(cur)
                        binding.layoutPlayerBar.tvTotalTime.text = AudioUtils.formatTime(total)
                    }
                }
                seekHandler.postDelayed(this, 200)
            }
        }
        seekRunnable?.let { seekHandler.post(it) }
    }

    private fun stopPlayback() {
        mediaPlayer?.let { it.stop(); it.release() }
        mediaPlayer = null
        currentlyPlaying = null
        seekRunnable?.let { seekHandler.removeCallbacks(it) }
        seekRunnable = null
        binding.layoutPlayerBar.root.visibility = View.GONE
        playTempFile?.delete()
        playTempFile = null
    }

    // ==================== 设置 ====================

    private fun showSettingsDialog() {
        val db = com.unlockmusic.android.databinding.DialogSettingsBinding.inflate(layoutInflater)

        when (outputPathType) {
            "music" -> db.rgOutputPath.check(R.id.rb_music)
            "custom" -> { db.rgOutputPath.check(R.id.rb_custom); db.layoutCustomPath.visibility = View.VISIBLE; db.etCustomPath.setText(customOutputPath) }
            else -> db.rgOutputPath.check(R.id.rb_downloads)
        }
        db.rgFileManager.check(if (fileManagerType == "system") R.id.rb_fm_system else R.id.rb_fm_documents)
        db.rgDuplicate.check(if (duplicateStrategy == "overwrite") R.id.rb_overwrite else R.id.rb_copy)
        db.rgOutputPath.setOnCheckedChangeListener { _, id -> db.layoutCustomPath.visibility = if (id == R.id.rb_custom) View.VISIBLE else View.GONE }
        db.etNamingPattern.setText(folderNamePattern.ifEmpty { getString(R.string.settings_naming_default) })

        // 主题色调色板
        var pendingSeed = seedIndex
        for (i in 0 until db.gridColors.childCount) {
            val swatch = db.gridColors.getChildAt(i)
            swatch.isSelected = (i == seedIndex)
            swatch.setOnClickListener {
                for (j in 0 until db.gridColors.childCount) db.gridColors.getChildAt(j).isSelected = false
                swatch.isSelected = true
                pendingSeed = i
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setView(db.root)
            .setPositiveButton(R.string.settings_save) { _, _ ->
                outputPathType = when (db.rgOutputPath.checkedRadioButtonId) {
                    R.id.rb_music -> "music"
                    R.id.rb_custom -> {
                        customOutputPath = db.etCustomPath.text.toString().trim()
                        if (customOutputPath.isEmpty()) { toast(R.string.toast_custom_path_empty); return@setPositiveButton }
                        "custom"
                    }
                    else -> "downloads"
                }
                fileManagerType = if (db.rgFileManager.checkedRadioButtonId == R.id.rb_fm_system) "system" else "documents"
                duplicateStrategy = if (db.rgDuplicate.checkedRadioButtonId == R.id.rb_overwrite) "overwrite" else "copy"
                folderNamePattern = db.etNamingPattern.text.toString().trim()
                val oldSeed = seedIndex
                seedIndex = pendingSeed
                savePreferences()
                toast(R.string.settings_saved)
                if (seedIndex != oldSeed) recreate()
            }
            .setNegativeButton(R.string.settings_cancel, null)
            .show()
    }

    /** 种子色 ThemeOverlay 资源映射 */
    private fun seedOverlayRes(i: Int): Int = when (i) {
        0 -> R.style.ThemeOverlay_UMusic_Seed01
        1 -> R.style.ThemeOverlay_UMusic_Seed02
        2 -> R.style.ThemeOverlay_UMusic_Seed03
        3 -> R.style.ThemeOverlay_UMusic_Seed04
        4 -> R.style.ThemeOverlay_UMusic_Seed05
        5 -> R.style.ThemeOverlay_UMusic_Seed06
        6 -> R.style.ThemeOverlay_UMusic_Seed07
        7 -> R.style.ThemeOverlay_UMusic_Seed08
        8 -> R.style.ThemeOverlay_UMusic_Seed09
        9 -> R.style.ThemeOverlay_UMusic_Seed10
        10 -> R.style.ThemeOverlay_UMusic_Seed11
        11 -> R.style.ThemeOverlay_UMusic_Seed12
        12 -> R.style.ThemeOverlay_UMusic_Seed13
        13 -> R.style.ThemeOverlay_UMusic_Seed14
        14 -> R.style.ThemeOverlay_UMusic_Seed15
        else -> R.style.ThemeOverlay_UMusic_Seed06
    }

    // ==================== 偏好存储 ====================

    private fun loadPreferences() {
        getSharedPreferences(PREFS, 0).run {
            outputPathType = getString(KEY_OUTPUT, "downloads") ?: "downloads"
            customOutputPath = getString(KEY_CUSTOM, "") ?: ""
            duplicateStrategy = getString(KEY_DUP, "copy") ?: "copy"
            folderNamePattern = getString(KEY_NAMING, "") ?: ""
            seedIndex = getInt(KEY_SEED, 5).coerceIn(0, 14)
            fileManagerType = getString(KEY_FM, "documents") ?: "documents"
        }
    }

    private fun savePreferences() {
        getSharedPreferences(PREFS, 0).edit()
            .putString(KEY_OUTPUT, outputPathType)
            .putString(KEY_CUSTOM, customOutputPath)
            .putString(KEY_DUP, duplicateStrategy)
            .putString(KEY_NAMING, folderNamePattern)
            .putInt(KEY_SEED, seedIndex)
            .putString(KEY_FM, fileManagerType)
            .apply()
    }

    // ==================== 工具方法 ====================

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) name = it.getString(0)
                }
            } catch (e: Exception) { Log.e(TAG, "getFileName", e) }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/')
            if (cut != null && cut >= 0) name = name!!.substring(cut + 1)
        }
        return name
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    override fun onDestroy() { super.onDestroy(); stopPlayback() }

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS = "unlock_music_prefs"
        private const val KEY_OUTPUT = "output_path"
        private const val KEY_CUSTOM = "custom_output_path"
        private const val KEY_DUP = "duplicate_strategy"
        private const val KEY_NAMING = "folder_name_pattern"
        private const val KEY_SEED = "seed_index"
        private const val KEY_FM = "file_manager"
    }
}
