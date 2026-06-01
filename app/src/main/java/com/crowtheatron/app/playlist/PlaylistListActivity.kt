package com.crowtheatron.app.playlist

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.crowtheatron.app.R
import com.crowtheatron.app.data.VideoRepository
import com.crowtheatron.app.library.LibraryActivity
import com.crowtheatron.app.ui.setContentWithCrowInsets

class PlaylistListActivity : AppCompatActivity() {

    private val repo by lazy { VideoRepository(this) }
    private lateinit var recycler: RecyclerView
    private var playlists = listOf<Pair<Long, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.crow_bg, theme))
        }

        val toolbar = com.google.android.material.appbar.MaterialToolbar(this).apply {
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(androidx.appcompat.R.attr.homeAsUpIndicator, typedValue, true)
            setNavigationIcon(typedValue.resourceId)
            title = "My Playlists"
            setNavigationOnClickListener { finish() }
            
            // Add Create button to toolbar
            val createItem = menu.add("Create")
            createItem.setIcon(android.R.drawable.ic_input_add)
            createItem.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                if (it == createItem) { showCreatePlaylistDialog(); true } else false
            }
        }
        root.addView(toolbar)

        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@PlaylistListActivity)
            setPadding(16, 16, 16, 16)
            clipToPadding = false
        }
        root.addView(recycler, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))

        setContentWithCrowInsets(root)
        loadData()
    }

    private fun loadData() {
        playlists = repo.listPlaylists()
        recycler.adapter = object : RecyclerView.Adapter<PlaylistVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistVH {
                val view = android.widget.LinearLayout(parent.context).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    setPadding(32, 32, 32, 32)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    layoutParams = RecyclerView.LayoutParams(-1, -2)
                    setBackgroundResource(android.R.drawable.list_selector_background)
                }
                val title = android.widget.TextView(parent.context).apply {
                    id = android.R.id.text1
                    textSize = 18f
                    setTextColor(resources.getColor(R.color.crow_on_bg, theme))
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f)
                }
                view.addView(title)
                
                val options = android.widget.ImageView(parent.context).apply {
                    id = android.R.id.icon1
                    setImageResource(android.R.drawable.ic_menu_more)
                    layoutParams = android.widget.LinearLayout.LayoutParams(96, 96)
                    setPadding(16, 16, 16, 16)
                    setColorFilter(resources.getColor(R.color.crow_accent_cyan, theme))
                }
                view.addView(options)
                
                return PlaylistVH(view)
            }

            override fun onBindViewHolder(holder: PlaylistVH, position: Int) {
                val (id, title) = playlists[position]
                val tv = holder.itemView.findViewById<android.widget.TextView>(android.R.id.text1)
                tv.text = title
                
                val btnOptions = holder.itemView.findViewById<android.widget.ImageView>(android.R.id.icon1)
                btnOptions.setOnClickListener { showOptions(id, title) }

                holder.itemView.setOnClickListener {
                    startActivity(Intent(this@PlaylistListActivity, LibraryActivity::class.java)
                        .putExtra(LibraryActivity.EXTRA_MODE, LibraryActivity.MODE_PLAYLIST)
                        .putExtra(LibraryActivity.EXTRA_PLAYLIST_ID, id)
                        .putExtra(LibraryActivity.EXTRA_PLAYLIST_NAME, title))
                }
                holder.itemView.setOnLongClickListener {
                    showOptions(id, title)
                    true
                }
            }

            override fun getItemCount() = playlists.size
        }
    }

    private fun wrapInMargin(view: android.view.View): android.view.View {
        val container = android.widget.FrameLayout(this)
        val lp = android.widget.FrameLayout.LayoutParams(-1, -2)
        lp.setMargins(48, 24, 48, 24)
        view.layoutParams = lp
        container.addView(view)
        return container
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(this).apply { hint = "Playlist Name" }
        AlertDialog.Builder(this).setTitle("New Playlist").setView(wrapInMargin(input))
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    repo.createPlaylist(name)
                    loadData()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showOptions(id: Long, title: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                if (which == 0) showRenameDialog(id, title)
                else confirmDelete(id, title)
            }.show()
    }

    private fun showRenameDialog(id: Long, title: String) {
        val input = EditText(this).apply { setText(title) }
        AlertDialog.Builder(this).setTitle("Rename").setView(wrapInMargin(input))
            .setPositiveButton("Save") { _, _ ->
                repo.renamePlaylist(id, input.text.toString().trim())
                loadData()
            }.show()
    }

    private fun confirmDelete(id: Long, title: String) {
        AlertDialog.Builder(this).setTitle("Delete \"$title\"?")
            .setPositiveButton("Delete") { _, _ ->
                repo.deletePlaylist(id)
                loadData()
            }.setNegativeButton("Cancel", null).show()
    }

    class PlaylistVH(v: android.view.View) : RecyclerView.ViewHolder(v)
}
