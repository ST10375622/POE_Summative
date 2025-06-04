package com.fake.poe_summative

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NotificationAdapter : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    private val notifications = mutableListOf<Notification>()

    fun submitList(newNotifications: List<Notification>)
    {
        notifications.clear()
        notifications.addAll(newNotifications)
        notifyDataSetChanged()
    }

    class NotificationViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val messageTextView: TextView = itemView.findViewById(R.id.textViewNotificationMessage)
        val timestampTextView: TextView = itemView.findViewById(R.id.textViewNotificationDate)
    }

    /*Inflates the layout for the notification
   * Code Attribution
   * RecyclerView.Adapter
   * Android Developer (2024)*/
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    /*Observes notifications
    * Code Attribution
    * LiveData.observeForever()
    * Android Developer (2024)*/
    override  fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.messageTextView.text = notification.message
        holder.timestampTextView.text = notification.timestamp?.toDate().toString()
    }

    override fun getItemCount(): Int = notifications.size


}