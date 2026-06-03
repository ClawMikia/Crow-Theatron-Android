package com.crowtheatron.app.folder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import com.crowtheatron.app.ui.showCrowMessage
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.crowtheatron.app.data.FolderScanner
import com.crowtheatron.app.data.VideoRepository
import com.crowtheatron.app.databinding.ActivityFolderSelectBinding
import com.crowtheatron.app.ui.setContentWithCrowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderSelectBinding
    private val repo by lazy { VideoRepository(this) }

    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            showCrowMessage("Access Denied", "Could not keep folder access.") { finish() }
            return@registerForActivityResult
        }
        scanAndImport(uri)
    }

    private val openFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri>? ->
        if (uris == null || uris.isEmpty()) {
            return@registerForActivityResult
        }
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
        }
        importFiles(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderSelectBinding.inflate(layoutInflater)
        setContentWithCrowInsets(binding.root)

        binding.btnChoose.setOnClickListener { openTree.launch(null) }
        binding.btnChooseFiles.setOnClickListener { openFiles.launch(arrayOf("video/*")) }
    }

    private fun scanAndImport(treeUri: Uri) {
        binding.progress.visibility = View.VISIBLE
        binding.status.text = getString(com.crowtheatron.app.R.string.scanning)
        binding.btnChoose.isEnabled = false
        binding.btnChooseFiles.isEnabled = false
        lifecycleScope.launch {
            val videos = withContext(Dispatchers.IO) {
                FolderScanner.scanTreeUri(this@FolderSelectActivity, treeUri)
            }
            if (videos.isEmpty()) {
                binding.progress.visibility = View.GONE
                binding.btnChoose.isEnabled = true
                binding.btnChooseFiles.isEnabled = true
                binding.status.text = "No video files found in that folder."
                showCrowMessage("No Videos Found", binding.status.text)
                return@launch
            }
            withContext(Dispatchers.IO) {
                repo.importScanResults(videos, withThumbnails = true)
            }
            showCrowMessage("Import Complete", "Imported ${videos.size} videos") { finish() }
        }
    }

    private fun importFiles(uris: List<Uri>) {
        binding.progress.visibility = View.VISIBLE
        binding.status.text = "Importing videos…"
        binding.btnChoose.isEnabled = false
        binding.btnChooseFiles.isEnabled = false
        lifecycleScope.launch {
            val videos = withContext(Dispatchers.IO) {
                FolderScanner.resolveUris(this@FolderSelectActivity, uris)
            }
            withContext(Dispatchers.IO) {
                repo.importScanResults(videos, withThumbnails = true)
            }
            showCrowMessage("Import Complete", "Imported ${videos.size} videos") { finish() }
        }
    }
}
