package com.example.testapp.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.tasks.await

// Rich message model matching WhatsApp clone specs
data class WhatsAppMessage(
  val id: String,
  val text: String,
  val sender: String,
  val receiver: String, // username or "group_lounge"
  val type: String = "text", // "text", "image", "location", "poll", "voice", "document"
  val timestamp: Long,
  val formattedTime: String,
  val mediaUrl: String? = null,
  val pollQuestion: String? = null,
  val pollOptions: List<String> = emptyList(),
  val pollVotes: Map<String, Int> = emptyMap(), // username -> optionIndex
  val locationLat: Double = 0.0,
  val locationLng: Double = 0.0,
  val locationName: String? = null,
  val fileName: String? = null,
  val fileSize: String? = null,
  val voiceDurationSec: Int = 0,
  val reactions: Map<String, String> = emptyMap(), // username -> reaction emoji
  val disappearing: Boolean = false,
  val isOneTime: Boolean = false,
  val isViewed: Boolean = false
)

data class WhatsAppUser(
  val username: String,
  val email: String,
  val phone: String,
  val bio: String,
  val statusText: String,
  val avatarColor: Int, // Hex value
  val lastSeen: Long,
  val photoBase64: String? = null, // Profile photo stored as Base64
  val displayName: String? = null
)

class FirestoreChatService {
  private val db by lazy { FirebaseFirestore.getInstance() }
  private val messagesCollection by lazy { db.collection("ghostview_messages") }
  val usersCollection by lazy { db.collection("ghostview_users") }

  // 1. Generate unique 1-to-1 conversation key
  fun getChatId(user1: String, user2: String): String {
    if (user2 == "group_lounge") return "group_lounge"
    val sorted = listOf(user1.lowercase().trim(), user2.lowercase().trim()).sorted()
    return "${sorted[0]}_${sorted[1]}"
  }

  // 2. Stream real-time users list
  fun getRealtimeUsers(): Flow<List<WhatsAppUser>> = callbackFlow {
    val listener = usersCollection.addSnapshotListener { snapshot, error ->
      if (error != null) {
        close(error)
        return@addSnapshotListener
      }
      if (snapshot != null) {
        val users = snapshot.documents.mapNotNull { doc ->
          val username = doc.id
          val email = doc.getString("email") ?: ""
          val phone = doc.getString("phone") ?: ""
          val bio = doc.getString("bio") ?: "Hey there! I am using GhostView."
          val statusText = doc.getString("statusText") ?: "Available"
          val avatarColor = doc.getLong("avatarColor")?.toInt() ?: 0xFF8B5CF6.toInt()
          val lastSeen = doc.getLong("lastSeen") ?: 0L
          val photoBase64 = doc.getString("photoBase64")
          val displayName = doc.getString("displayName")

          WhatsAppUser(username, email, phone, bio, statusText, avatarColor, lastSeen, photoBase64, displayName)
        }
        trySend(users)
      }
    }
    awaitClose { listener.remove() }
  }

  // 3. Register or update a user profile
  fun registerUser(user: WhatsAppUser) {
    val userData = hashMapOf(
      "email" to user.email,
      "phone" to user.phone,
      "bio" to user.bio,
      "statusText" to user.statusText,
      "avatarColor" to user.avatarColor.toLong(),
      "lastSeen" to System.currentTimeMillis(),
      "photoBase64" to user.photoBase64,
      "displayName" to (user.displayName ?: user.username.replaceFirstChar { it.uppercase() })
    )
    usersCollection.document(user.username.lowercase().trim()).set(userData)
      .addOnFailureListener { e -> android.util.Log.e("FirestoreChat", "Error registering user", e) }
  }

  // Find user by email or phone (New Contact Lookup)
  suspend fun findUserByContactInfo(query: String): WhatsAppUser? {
    val q = query.trim()
    if (q.isEmpty()) return null
    val qLower = q.lowercase()
    
    // Try by exact username (document ID) first
    try {
      val doc = usersCollection.document(qLower).get().await()
      if (doc.exists()) {
        return docToUser(doc)
      }
    } catch (_: Exception) {}

    // Try by email (lowercase normalized)
    try {
      val emailResult = usersCollection.whereEqualTo("email", qLower).get().await()
      if (!emailResult.isEmpty) {
        return docToUser(emailResult.documents.first())
      }
    } catch (_: Exception) {}

    // Try by phone
    try {
      val phoneResult = usersCollection.whereEqualTo("phone", q).get().await()
      if (!phoneResult.isEmpty) {
        return docToUser(phoneResult.documents.first())
      }
    } catch (_: Exception) {}

    // Fallback: scan all users for partial email/username match
    // (handles cases where email was stored with different casing or partial input)
    try {
      val allUsers = usersCollection.get().await()
      for (doc in allUsers.documents) {
        val email = (doc.getString("email") ?: "").lowercase()
        val phone = doc.getString("phone") ?: ""
        val username = doc.id.lowercase()
        if (email.isNotEmpty() && (email == qLower || email.contains(qLower) || qLower.contains(email))) {
          return docToUser(doc)
        }
        if (username.contains(qLower) || qLower.contains(username)) {
          return docToUser(doc)
        }
        if (phone.isNotEmpty() && (phone.contains(q) || q.contains(phone))) {
          return docToUser(doc)
        }
      }
    } catch (_: Exception) {}

    return null
  }

  // Find ALL users matching a query (for search results list)
  suspend fun searchUsers(query: String): List<WhatsAppUser> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val qLower = q.lowercase()
    val results = mutableListOf<WhatsAppUser>()

    try {
      val allUsers = usersCollection.get().await()
      for (doc in allUsers.documents) {
        val email = (doc.getString("email") ?: "").lowercase()
        val phone = doc.getString("phone") ?: ""
        val username = doc.id.lowercase()
        if (email.contains(qLower) || username.contains(qLower) || phone.contains(q)) {
          results.add(docToUser(doc))
        }
      }
    } catch (_: Exception) {}

    return results
  }

  // Helper to convert a Firestore document to WhatsAppUser
  private fun docToUser(doc: com.google.firebase.firestore.DocumentSnapshot): WhatsAppUser {
    return WhatsAppUser(
      username = doc.id,
      email = doc.getString("email") ?: "",
      phone = doc.getString("phone") ?: "",
      bio = doc.getString("bio") ?: "Hey there! I am using GhostView.",
      statusText = doc.getString("statusText") ?: "Available",
      avatarColor = doc.getLong("avatarColor")?.toInt() ?: 0xFFFFFFFF.toInt(),
      lastSeen = doc.getLong("lastSeen") ?: 0L,
      photoBase64 = doc.getString("photoBase64"),
      displayName = doc.getString("displayName")
    )
  }

  // Update user profile fields (display name, bio, photo)
  fun updateUserProfile(
    username: String,
    newBio: String? = null,
    newPhotoBase64: String? = null,
    newStatusText: String? = null,
    newDisplayName: String? = null
  ) {
    val updates = mutableMapOf<String, Any>()
    if (newBio != null) updates["bio"] = newBio
    if (newPhotoBase64 != null) updates["photoBase64"] = newPhotoBase64
    if (newStatusText != null) updates["statusText"] = newStatusText
    if (newDisplayName != null) updates["displayName"] = newDisplayName
    updates["lastSeen"] = System.currentTimeMillis()
    if (updates.isNotEmpty()) {
      usersCollection.document(username.lowercase().trim()).update(updates)
    }
  }

  // 4. Stream real-time messages for a specific conversation
  fun getRealtimeMessages(chatId: String): Flow<List<WhatsAppMessage>> = callbackFlow {
    val listener = messagesCollection
      .whereEqualTo("chatId", chatId)
      .addSnapshotListener { snapshot, error ->
        if (error != null) {
          close(error)
          return@addSnapshotListener
        }
        if (snapshot != null) {
          val messageList = snapshot.documents.mapNotNull { doc ->
            val id = doc.id
            val text = doc.getString("text") ?: ""
            val sender = doc.getString("sender") ?: ""
            val receiver = doc.getString("receiver") ?: ""
            val type = doc.getString("type") ?: "text"
            val timestampVal = doc.getLong("timestamp") ?: 0L
            val mediaUrl = doc.getString("mediaUrl")
            val pollQuestion = doc.getString("pollQuestion")
            @Suppress("UNCHECKED_CAST")
            val pollOptions = (doc.get("pollOptions") as? List<String>) ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val pollVotes = (doc.get("pollVotes") as? Map<String, Long>)?.mapValues { it.value.toInt() } ?: emptyMap()
            val locationLat = doc.getDouble("locationLat") ?: 0.0
            val locationLng = doc.getDouble("locationLng") ?: 0.0
            val locationName = doc.getString("locationName")
            val fileName = doc.getString("fileName")
            val fileSize = doc.getString("fileSize")
            val voiceDurationSec = doc.getLong("voiceDurationSec")?.toInt() ?: 0
            @Suppress("UNCHECKED_CAST")
            val reactions = (doc.get("reactions") as? Map<String, String>) ?: emptyMap()
            val disappearing = doc.getBoolean("disappearing") ?: false
            val isOneTime = doc.getBoolean("isOneTime") ?: false
            val isViewed = doc.getBoolean("isViewed") ?: false

            WhatsAppMessage(
              id = id,
              text = text,
              sender = sender,
              receiver = receiver,
              type = type,
              timestamp = timestampVal,
              formattedTime = formatTime(timestampVal),
              mediaUrl = mediaUrl,
              pollQuestion = pollQuestion,
              pollOptions = pollOptions,
              pollVotes = pollVotes,
              locationLat = locationLat,
              locationLng = locationLng,
              locationName = locationName,
              fileName = fileName,
              fileSize = fileSize,
              voiceDurationSec = voiceDurationSec,
              reactions = reactions,
              disappearing = disappearing,
              isOneTime = isOneTime,
              isViewed = isViewed
            )
          }
          trySend(messageList.sortedBy { it.timestamp })
        }
      }
    awaitClose { listener.remove() }
  }

  // 5. Send rich structured message
  fun sendMessage(msg: WhatsAppMessage, chatId: String) {
    val messageData = hashMapOf(
      "chatId" to chatId,
      "text" to msg.text,
      "sender" to msg.sender,
      "receiver" to msg.receiver,
      "type" to msg.type,
      "timestamp" to msg.timestamp,
      "mediaUrl" to msg.mediaUrl,
      "pollQuestion" to msg.pollQuestion,
      "pollOptions" to msg.pollOptions,
      "pollVotes" to msg.pollVotes,
      "locationLat" to msg.locationLat,
      "locationLng" to msg.locationLng,
      "locationName" to msg.locationName,
      "fileName" to msg.fileName,
      "fileSize" to msg.fileSize,
      "voiceDurationSec" to msg.voiceDurationSec.toLong(),
      "reactions" to msg.reactions,
      "disappearing" to msg.disappearing,
      "isOneTime" to msg.isOneTime,
      "isViewed" to msg.isViewed
    )
    messagesCollection.add(messageData)
      .addOnFailureListener { e -> android.util.Log.e("FirestoreChat", "Error sending message", e) }
  }

  // 6. Cast a vote on a poll
  fun castVote(messageId: String, voterName: String, optionIndex: Int) {
    val docRef = messagesCollection.document(messageId)
    db.runTransaction { transaction ->
      val snapshot = transaction.get(docRef)
      @Suppress("UNCHECKED_CAST")
      val pollVotes = (snapshot.get("pollVotes") as? Map<String, Long>)?.toMutableMap() ?: mutableMapOf()
      pollVotes[voterName] = optionIndex.toLong()
      transaction.update(docRef, "pollVotes", pollVotes)
    }.addOnFailureListener {
      // Graceful local logging
    }
  }

  // 7. Add reaction to a message
  fun addReaction(messageId: String, username: String, emoji: String) {
    val docRef = messagesCollection.document(messageId)
    db.runTransaction { transaction ->
      val snapshot = transaction.get(docRef)
      @Suppress("UNCHECKED_CAST")
      val reactions = (snapshot.get("reactions") as? Map<String, String>)?.toMutableMap() ?: mutableMapOf()
      reactions[username] = emoji
      transaction.update(docRef, "reactions", reactions)
    }.addOnFailureListener {
      // Graceful local logging
    }
  }

  // 8. Secure deletion of self-destructed messages
  fun deleteMessage(messageId: String) {
    messagesCollection.document(messageId).delete()
  }

  private fun formatTime(millis: Long): String {
    if (millis == 0L) return "Now"
    val date = Date(millis)
    val format = SimpleDateFormat("h:mm a", Locale.getDefault())
    return format.format(date)
  }

  // 9. Mark One-Time message as viewed
  fun markMessageAsViewed(messageId: String) {
    messagesCollection.document(messageId).update(
      "isViewed", true,
      "mediaUrl", ""
    ).addOnFailureListener { e -> android.util.Log.e("FirestoreChat", "Error marking message as viewed", e) }
  }
}
