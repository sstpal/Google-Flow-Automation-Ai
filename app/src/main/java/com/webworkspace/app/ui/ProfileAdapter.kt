package com.webworkspace.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.webworkspace.app.R
import com.webworkspace.app.data.Profile

class ProfileAdapter(
    private val onPlayClick: (Profile) -> Unit,
    private val onNewSessionClick: (Profile) -> Unit,
    private val onCustomizeClick: (Profile) -> Unit,
    private val onDeleteClick: (Profile) -> Unit
) : ListAdapter<Profile, ProfileAdapter.ProfileViewHolder>(ProfileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_profile, parent, false)
        return ProfileViewHolder(view, onPlayClick, onNewSessionClick, onCustomizeClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ProfileViewHolder(
        itemView: View,
        private val onPlayClick: (Profile) -> Unit,
        private val onNewSessionClick: (Profile) -> Unit,
        private val onCustomizeClick: (Profile) -> Unit,
        private val onDeleteClick: (Profile) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvProfileName: TextView = itemView.findViewById(R.id.tvProfileName)
        private val btnPlay: MaterialButton = itemView.findViewById(R.id.btnPlay)
        private val btnNewSession: MaterialButton = itemView.findViewById(R.id.btnNewSession)
        private val btnCustomize: MaterialButton = itemView.findViewById(R.id.btnCustomize)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(profile: Profile) {
            tvProfileName.text = profile.name
            
            btnPlay.setOnClickListener { onPlayClick(profile) }
            btnNewSession.setOnClickListener { onNewSessionClick(profile) }
            btnCustomize.setOnClickListener { onCustomizeClick(profile) }
            btnDelete.setOnClickListener { onDeleteClick(profile) }
        }
    }
}

class ProfileDiffCallback : DiffUtil.ItemCallback<Profile>() {
    override fun areItemsTheSame(oldItem: Profile, newItem: Profile) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Profile, newItem: Profile) = oldItem == newItem
}
