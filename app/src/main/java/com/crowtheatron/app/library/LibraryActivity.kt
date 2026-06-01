package com.crowtheatron.app.library

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.crowtheatron.app.R
import com.crowtheatron.app.data.VideoEntity
import com.crowtheatron.app.data.VideoRepository
import com.crowtheatron.app.databinding.ActivityLibraryBinding
import com.crowtheatron.app.player.PlayerActivity
import com.crowtheatron.app.ui.BottomNavHelper
import com.crowtheatron.app.ui.LibraryAdapter
import com.crowtheatron.app.ui.LibraryListItem
import com.crowtheatron.app.ui.buildGroupedItems
import com.crowtheatron.app.ui.setContentWithCrowInsets

class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private val repo by lazy { VideoRepository(this) }
    private val expandedFolders = mutableSetOf<String>()
    private var allVideos = listOf<VideoEntity>()
    private var currentMode = MODE_ALL

    private lateinit var adapter: LibraryAdapter
    private var playlistId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentWithCrowInsets(binding.root)

        currentMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ALL
        playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1L)
        
        val navSelected = when (currentMode) {
            MODE_FAVORITES        -> R.id.nav_favorites
            MODE_CONTINUE         -> R.id.nav_library
            MODE_RECENTLY_PLAYED  -> R.id.nav_memory
            else                  -> R.id.nav_library
        }
        binding.toolbar.title = getString(when (currentMode) {
            MODE_FAVORITES       -> R.string.favorites_title
            MODE_CONTINUE        -> R.string.section_continue_watching
            MODE_RECENTLY_PLAYED -> R.string.memory_title
            else                 -> R.string.library_title
        })
        BottomNavHelper.setup(this, binding.bottomNav, navSelected)
        
        if (currentMode == MODE_PLAYLIST) {
            binding.toolbar.title = intent.getStringExtra(EXTRA_PLAYLIST_NAME) ?: "Playlist"
            // Add "Add Video" button to toolbar
            val addItem = binding.toolbar.menu.add("Add Video")
            addItem.setIcon(android.R.drawable.ic_input_add)
            addItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            binding.toolbar.setOnMenuItemClickListener {
                if (it == addItem) { showAddVideoToPlaylistDialog(); true } else false
            }
        }

        adapter = LibraryAdapter(
            onHeaderClick = { folder ->
                if (expandedFolders.contains(folder)) {
                    expandedFolders.remove(folder)
                } else {
                    expandedFolders.add(folder)
                }
                updateList()
            },
            onRemove = if (currentMode == MODE_PLAYLIST) { videoId ->
                repo.removeVideoFromPlaylist(playlistId, videoId)
                refreshData()
            } else null,
            onOpen = { videoId, playlistIds, index ->
                startActivity(
                    Intent(this, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_VIDEO_ID, videoId)
                        .putExtra(PlayerActivity.EXTRA_PLAYLIST_IDS, playlistIds)
                        .putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, index)
                )
            }
        )
        val glm = GridLayoutManager(this, 2)
        glm.spanSizeLookup = adapter.spanSizeLookup()
        binding.recycler.layoutManager = glm
        binding.recycler.adapter = adapter

        allVideos = when (currentMode) {
            MODE_FAVORITES       -> repo.listFavorites()
            MODE_CONTINUE        -> repo.listContinueWatching()
            MODE_RECENTLY_PLAYED -> repo.listRecentlyPlayed()
            MODE_PLAYLIST        -> {
                val playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1L)
                binding.toolbar.title = intent.getStringExtra(EXTRA_PLAYLIST_NAME) ?: "Playlist"
                repo.getVideosInPlaylist(playlistId)
            }
            else                 -> repo.listAllByFolder()
        }

        // Initially expand all
        if (currentMode == MODE_ALL) {
            expandedFolders.addAll(allVideos.map { it.folderGroup })
        }

        updateList()
    }

    private fun refreshData() {
        allVideos = when (currentMode) {
            MODE_FAVORITES       -> repo.listFavorites()
            MODE_CONTINUE        -> repo.listContinueWatching()
            MODE_RECENTLY_PLAYED -> repo.listRecentlyPlayed()
            MODE_PLAYLIST        -> repo.getVideosInPlaylist(playlistId)
            else                 -> repo.listAllByFolder()
        }
        updateList()
    }

    private fun showAddVideoToPlaylistDialog() {
        val allVideosInLibrary = repo.listAllByFolder()
        val currentPlaylistVideoIds = allVideos.map { it.id }.toSet()
        val availableVideos = allVideosInLibrary.filter { it.id !in currentPlaylistVideoIds }
        
        if (availableVideos.isEmpty()) {
            Toast.makeText(this, "All videos are already in this playlist", Toast.LENGTH_SHORT).show()
            return
        }

        val titles = availableVideos.map { it.title }.toTypedArray()
        val selected = BooleanArray(titles.size)

        AlertDialog.Builder(this)
            .setTitle("Add Videos to Playlist")
            .setMultiChoiceItems(titles, selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            .setPositiveButton("Add") { _, _ ->
                selected.forEachIndexed { index, isChecked ->
                    if (isChecked) {
                        repo.addVideoToPlaylist(playlistId, availableVideos[index].id)
                    }
                }
                refreshData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateList() {
        val items: List<LibraryListItem> = when (currentMode) {
            MODE_ALL -> buildGroupedItems(allVideos, expandedFolders, allExpandedInitially = false)
            else     -> {
                if (allVideos.isEmpty()) emptyList()
                else buildList {
                    add(LibraryListItem.Header(binding.toolbar.title?.toString() ?: "", true))
                    addAll(allVideos.map { LibraryListItem.VideoRow(it) })
                }
            }
        }

        adapter.submitList(items)
        binding.empty.visibility =
            if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.recycler.visibility =
            if (items.isEmpty()) View.GONE else View.VISIBLE
    }

    companion object {
        const val EXTRA_MODE         = "mode"
        const val MODE_ALL           = "all"
        const val MODE_FAVORITES     = "favorites"
        const val MODE_CONTINUE      = "continue_watching"
        const val MODE_RECENTLY_PLAYED = "recently_played"
        const val MODE_PLAYLIST      = "playlist"
        const val EXTRA_PLAYLIST_ID  = "playlist_id"
        const val EXTRA_PLAYLIST_NAME = "playlist_name"
    }
}
