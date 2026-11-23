package com.example.splendit.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.splendit.MainActivity
import com.example.splendit.R
import com.example.splendit.data.GroupEvent

/**
 * Local notifications for changes other members make. Tapping one opens the group: the intent
 * carries [EXTRA_GROUP_ID], which [MainActivity] hands to the nav graph.
 */
class GroupNotifier(private val context: Context) {

    private enum class Channel(
        val id: String,
        @StringRes val title: Int,
        @StringRes val desc: Int
    ) {
        GROUPS("groups", R.string.channel_groups, R.string.channel_groups_desc),
        EXPENSES("expenses", R.string.channel_expenses, R.string.channel_expenses_desc),
        PAYMENTS("payments", R.string.channel_payments, R.string.channel_payments_desc)
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        Channel.entries.forEach {
            val channel = NotificationChannel(
                it.id,
                context.getString(it.title),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(it.desc) }
            manager.createNotificationChannel(channel)
        }
    }

    fun show(event: GroupEvent) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val (channel, title, text) = when (event) {
            is GroupEvent.AddedToGroup -> Triple(
                Channel.GROUPS,
                event.groupName,
                "${event.by} added you to the group"
            )

            is GroupEvent.NewExpense -> Triple(
                Channel.EXPENSES,
                event.groupName,
                "${event.by} added ${event.title}. Your share is ${event.myShare}."
            )

            is GroupEvent.PaymentReceived -> Triple(
                Channel.PAYMENTS,
                event.groupName,
                "${event.from} paid you ${event.amount}"
            )
        }
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_wheel)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openGroup(event.groupId))
            .build()
        NotificationManagerCompat.from(context).notify(nextId++, notification)
    }

    private fun openGroup(groupId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_GROUP_ID, groupId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            groupId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private var nextId = 1

    companion object {
        const val EXTRA_GROUP_ID = "com.example.splendit.GROUP_ID"
    }
}
