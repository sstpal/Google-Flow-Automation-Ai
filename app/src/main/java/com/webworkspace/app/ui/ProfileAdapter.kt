package com.webworkspace.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.webworkspace.app.R
import com.webworkspace.app.data.Profile

class ProfileAdapter(
    private val onProfileClick: (Profile) -> Unit,
    private val onDeleteClick: (Profile) -> Unit
) : ListAdapter<Profile, ProfileAdapter.ProfileViewHolder>(ProfileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile, parent, false)
        return ProfileViewHolder(view, onProfileClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ProfileViewHolder(
        itemView: View,
        private val onProfileClick: (Profile) -> Unit,
        private val onDeleteClick: (Profile) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvProfileName: TextView = itemView.findViewById(R.id.tvProfileName)
        private val tvProfileStatus: TextView = itemView.findViewById(R.id.tvProfileStatus)
        private val btnMenu: ImageButton = itemView.findViewById(R.id.btnMenu)

        fun bind(profile: Profile) {
            tvProfileName.text = profile.name
            if (profile.hasSession) {
                tvProfileStatus.text = itemView.context.getString(R.string.status_session_available)
            } else {
                tvProfileStatus.text = itemView.context.getString(R.string.status_not_logged_in)
            }

            itemView.setOnClickListener {
                onProfileClick(profile)
            }

            btnMenu.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menu.add(0, 1, 0, R.string.action_delete)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> {
                            onDeleteClick(profile)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }
}

class ProfileDiffCallback : DiffUtil.ItemCallback<Profile>() {
    override fun areItemsTheSame(oldItem: Profile, newItem: Profile): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Profile, newItem: Profile): Boolean {
        return oldItem == newItem
    }
}
