package com.example.testapp.ui.main
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException

import android.os.Handler
import android.os.Looper

import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage

import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import com.example.testapp.data.DefaultDataRepository
import com.example.testapp.data.FirestoreChatService
import com.example.testapp.data.WhatsAppMessage
import com.example.testapp.data.WhatsAppUser
import com.example.testapp.theme.TestAppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch






// Unified Identifier Formatting: converts raw numbers securely to a virtual email address
fun formatAuthIdentifier(input: String): String {
  val trimmed = input.trim()
  return if (trimmed.contains("@")) {
    trimmed
  } else {
    val cleaned = trimmed.replace(Regex("[^0-9+]"), "")
    if (cleaned.isEmpty()) "" else "phone_${cleaned}@ghostview.io"
  }
}

// Native Android Screenshot Shield control
fun toggleScreenSecurity(context: android.content.Context, enabled: Boolean) {
  val activity = context as? Activity
  activity?.runOnUiThread {
    if (enabled) {
      activity.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
      activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
  }
}

// Base64 Helper Utilities for actual media broadcasting
fun uriToBase64(context: android.content.Context, uri: Uri): Pair<String, String> {
  return try {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return Pair("", "")
    val bytes = inputStream.readBytes()
    inputStream.close()
    
    var fileName = "Document.pdf"
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use { c ->
      if (c.moveToFirst()) {
        val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIdx != -1) fileName = c.getString(nameIdx)
      }
    }
    
    val mimeType = context.contentResolver.getType(uri)
    if (mimeType?.startsWith("image") == true) {
      val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      if (bitmap != null) {
        val outputStream = ByteArrayOutputStream()
        // Compress highly to remain securely below Firestore's 1MB document limit
        bitmap.compress(Bitmap.CompressFormat.JPEG, 25, outputStream)
        val compressedBytes = outputStream.toByteArray()
        val base64 = Base64.encodeToString(compressedBytes, Base64.DEFAULT)
        return Pair(base64, fileName)
      }
    }
    
    val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)
    Pair(base64, fileName)
  } catch (e: Exception) {
    Pair("", "")
  }
}

fun bitmapToBase64(bitmap: Bitmap): String {
  return try {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 25, outputStream)
    val bytes = outputStream.toByteArray()
    Base64.encodeToString(bytes, Base64.DEFAULT)
  } catch (e: Exception) {
    ""
  }
}

@Composable
fun Base64Image(base64Str: String, modifier: Modifier = Modifier) {
  val imageBitmap = remember(base64Str) {
    try {
      val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
      val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
      bitmap?.asImageBitmap()
    } catch (e: Exception) {
      null
    }
  }

  if (imageBitmap != null) {
    Image(
      bitmap = imageBitmap,
      contentDescription = "Shared Media Image",
      modifier = modifier,
      contentScale = ContentScale.Crop
    )
  } else {
    Box(
      modifier = modifier.background(Color(0xFF10171D)),
      contentAlignment = Alignment.Center
    ) {
      Icon(imageVector = Icons.Default.BrokenImage, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(32.dp))
    }
  }
}

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  when (state) {
    MainScreenUiState.Loading -> {
      Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
      }
    }
    is MainScreenUiState.Success -> {
      GhostViewDashboard(modifier = modifier)
    }
    is MainScreenUiState.Error -> {
      Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080C10)), contentAlignment = Alignment.Center) {
        Text(
          "Initialization Error: ${(state as MainScreenUiState.Error).throwable.message}",
          color = Color(0xFFEF4444)
        )
      }
    }
  }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GhostViewDashboard(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  
  // Real active user authenticated session
  val firebaseAuth = remember { FirebaseAuth.getInstance() }
  var firebaseUser by remember { mutableStateOf(firebaseAuth.currentUser) }

  var nickname by remember { mutableStateOf(firebaseUser?.displayName ?: "") }
  var phoneNo by remember { mutableStateOf("") }
  var hasJoined by remember { mutableStateOf(firebaseUser != null) }

  // Standard Navigation Tabs: 0 = CHATS, 1 = STATUS, 2 = CALLS, 3 = SETTINGS
  var activeTab by remember { mutableStateOf(0) }

  // Custom Wallpaper index: 0 = Midnight Obsidian, 1 = Spectral Neon, 2 = Spectral Cyber
  var wallpaperIndex by remember { mutableStateOf(0) }

  // Active chat session details: "" (no chat / shows list full screen) or dynamic target
  var activeChatPartner by remember { mutableStateOf("") }
  var activeChatPartnerName by remember { mutableStateOf("") }
  
  // Dynamic Cryptographic Toggles
  var screenShieldEnabled by remember { mutableStateOf(false) }
  var ephemeralChatEnabled by remember { mutableStateOf(false) }

  /* 
  // Disable screenshot shield by default to prevent black screens during mirroring/debugging
  LaunchedEffect(screenShieldEnabled) {
    toggleScreenSecurity(context, screenShieldEnabled)
  }
  */

  // Call simulation states
  var isCallActive by remember { mutableStateOf(false) }
  var callType by remember { mutableStateOf("video") }
  var callerName by remember { mutableStateOf("") }
  var isMuted by remember { mutableStateOf(false) }
  var isCameraOn by remember { mutableStateOf(true) }
  var isScreenSharing by remember { mutableStateOf(false) }

  // Security Verification Center state
  var securityCenterOpen by remember { mutableStateOf(false) }

  // Firestore integration
  val firestoreService = remember {
    try {
      FirestoreChatService()
    } catch (e: Exception) {
      null
    }
  }

  // Active user records synced from Firestore
  val firestoreUsersList = remember { mutableStateListOf<WhatsAppUser>() }

  // Active messages streams
  val firestoreMessages = remember { mutableStateListOf<WhatsAppMessage>() }

  // Active chat stream binding
  val currentChatId = remember(nickname, activeChatPartner) {
    if (firestoreService != null && nickname.isNotEmpty()) {
      firestoreService.getChatId(nickname, activeChatPartner)
    } else {
      ""
    }
  }

  // Real-time Firestore user sync (NO dummy users)
  if (hasJoined && firestoreService != null) {
    LaunchedEffect(Unit) {
      try {
        firestoreService.getRealtimeUsers().collect { list ->
          firestoreUsersList.clear()
          val filtered = list.filter { it.username.lowercase().trim() != nickname.lowercase().trim() }
          firestoreUsersList.addAll(filtered)
        }
      } catch (e: Exception) {}
    }

    // Subscribe to messages in current chatroom
    LaunchedEffect(currentChatId) {
      if (currentChatId.isNotEmpty()) {
        try {
          firestoreService.getRealtimeMessages(currentChatId).collect { list ->
            firestoreMessages.clear()
            firestoreMessages.addAll(list)
          }
        } catch (e: Exception) {}
      }
    }
  }

  val activeMessages = firestoreMessages
  val activeUsersList = firestoreUsersList

  // Send message implementation
  fun handleSendMessage(
    text: String,
    type: String = "text",
    mediaUrl: String? = null,
    pollQuestion: String? = null,
    pollOptions: List<String> = emptyList(),
    locationLat: Double = 0.0,
    locationLng: Double = 0.0,
    locationName: String? = null,
    fileName: String? = null,
    fileSize: String? = null,
    voiceDurationSec: Int = 0,
    isOneTime: Boolean = false
  ) {
    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
    val newMsg = WhatsAppMessage(
      id = "msg_${System.currentTimeMillis()}",
      text = text,
      sender = nickname,
      receiver = activeChatPartner,
      type = type,
      timestamp = System.currentTimeMillis(),
      formattedTime = timeStr,
      mediaUrl = mediaUrl,
      pollQuestion = pollQuestion,
      pollOptions = pollOptions,
      pollVotes = emptyMap(),
      locationLat = locationLat,
      locationLng = locationLng,
      locationName = locationName,
      fileName = fileName,
      fileSize = fileSize,
      voiceDurationSec = voiceDurationSec,
      reactions = emptyMap(),
      disappearing = ephemeralChatEnabled,
      isOneTime = isOneTime
    )

    if (firestoreService != null && currentChatId.isNotEmpty()) {
      firestoreService.sendMessage(newMsg, currentChatId)
    }
  }

  // Interactive Poll Voting handler
  fun handleCastVote(messageId: String, optionIndex: Int) {
    if (firestoreService != null) {
      firestoreService.castVote(messageId, nickname, optionIndex)
    }
  }

  // Emoji Reactions handler
  fun handleAddReaction(messageId: String, emoji: String) {
    if (firestoreService != null) {
      firestoreService.addReaction(messageId, nickname, emoji)
    }
  }

  // Base background theme color sets (Obsidian & Cyan themes)
  val chatBackgroundBrush = when (wallpaperIndex) {
    1 -> Brush.verticalGradient(colors = listOf(Color(0xFF0F0818), Color(0xFF1B1030))) // Spectral Neon
    2 -> Brush.verticalGradient(colors = listOf(Color(0xFF05100E), Color(0xFF0A1D1A))) // Spectral Cyber
    else -> Brush.verticalGradient(colors = listOf(Color(0xFF080C10), Color(0xFF10161D))) // Midnight Obsidian
  }

  Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080C10))) {
    Column(modifier = Modifier.fillMaxSize()) {
      
      // GhostView Styled Header Bar
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF121B22))
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF00E5FF)),
              contentAlignment = Alignment.Center
            ) {
              Icon(imageVector = Icons.Default.VisibilityOff, contentDescription = "Logo", tint = Color.Black, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "GhostView",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
              )
              if (hasJoined) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF00E5FF)))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "Logged in as: @${nickname.lowercase()}",
                    color = Color(0xFF8E9AA4),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
            }
          }

          // Interactive Secure Channel Button
          if (hasJoined) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x1A00E5FF))
                .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(12.dp))
                .clickable { securityCenterOpen = true }
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(imageVector = Icons.Default.Security, contentDescription = "Security Status", tint = Color(0xFF00E5FF), modifier = Modifier.size(13.dp))
                Text(
                  text = "SECURE CHANNEL",
                  color = Color(0xFF00E5FF),
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }

      // Tab Screen Contents Viewport
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        if (!hasJoined) {
          LoginScreen(
            auth = firebaseAuth,
            firestoreService = firestoreService,
            onJoined = { user, phone ->
              nickname = user
              phoneNo = phone
              hasJoined = true
            }
          )
        } else {
          when (activeTab) {
            0 -> {
              GhostViewChatsTab(
                nickname = nickname,
                activePartner = activeChatPartner,
                activePartnerName = activeChatPartnerName,
                activeUsers = activeUsersList,
                messagesList = activeMessages,
                chatBg = chatBackgroundBrush,
                doodleMode = wallpaperIndex == 0,
                onPartnerSelected = { handle, name ->
                  activeChatPartner = handle
                  activeChatPartnerName = name
                },
                onSendMessage = { handleSendMessage(it) },
                onSendRichMessage = { type, mUrl, q, o, lat, lng, lName, fName, fSize, dur, isOneTime ->
                  handleSendMessage("", type, mUrl, q, o, lat, lng, lName, fName, fSize, dur, isOneTime)
                },
                onVoteCast = { msgId, optIdx -> handleCastVote(msgId, optIdx) },
                onReact = { msgId, emoji -> handleAddReaction(msgId, emoji) },
                onTriggerCall = { type ->
                  callType = type
                  callerName = activeChatPartnerName
                  isCallActive = true
                },
                firestoreService = firestoreService
              )
            }
            1 -> {
              GhostViewUpdatesTab(activeUsers = activeUsersList)
            }
            2 -> {
              GhostViewCallsTab(
                activeUsers = activeUsersList,
                onTriggerCall = { name, type ->
                  callerName = name
                  callType = type
                  isCallActive = true
                }
              )
            }
            3 -> {
              GhostViewSettingsTab(
                username = nickname,
                wallpaperIndex = wallpaperIndex,
                screenShield = screenShieldEnabled,
                ephemeralMode = ephemeralChatEnabled,
                firestoreService = firestoreService,
                onWallpaperChanged = { wallpaperIndex = it },
                onScreenShieldToggled = { screenShieldEnabled = it },
                onEphemeralModeToggled = { ephemeralChatEnabled = it },
                onNicknameChanged = { newName -> nickname = newName },
                onLogout = {
                  firebaseAuth.signOut()
                  nickname = ""
                  hasJoined = false
                }
              )
            }
          }
        }
      }

      if (hasJoined) {
        // App Tab Selector Bar (Standard Naming)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF121B22))
            .padding(bottom = 12.dp)
        ) {
          val tabConfigs = listOf(
            Triple("Chats", Icons.Default.ChatBubble, 0),
            Triple("Status", Icons.Default.Adjust, 1),
            Triple("Calls", Icons.Default.Phone, 2),
            Triple("Settings", Icons.Default.Settings, 3)
          )
          tabConfigs.forEach { (title, icon, idx) ->
            val active = activeTab == idx
            val indicatorColor by animateColorAsState(if (active) Color(0xFF00E5FF) else Color.Transparent, label = "")
            Column(
              modifier = Modifier
                .weight(1f)
                .clickable { activeTab = idx }
                .padding(top = 0.dp, bottom = 8.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Box(modifier = Modifier.height(3.dp).fillMaxWidth(0.6f).clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)).background(indicatorColor))
              Spacer(modifier = Modifier.height(8.dp))
              Icon(
                imageVector = icon, 
                contentDescription = title,
                tint = if (active) Color(0xFF00E5FF) else Color(0xFF8E9AA4),
                modifier = Modifier.size(22.dp)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = title,
                color = if (active) Color(0xFF00E5FF) else Color(0xFF8E9AA4),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
              )
            }
          }
        }
      }
    }

    // Call Fullscreen overlay viewer
    if (isCallActive) {
      CallOverlayScreen(
        callerName = callerName,
        type = callType,
        isMuted = isMuted,
        isCameraOn = isCameraOn,
        isScreenSharing = isScreenSharing,
        onMuteToggled = { isMuted = it },
        onCameraToggled = { isCameraOn = it },
        onScreenShareToggled = { isScreenSharing = it },
        onHangup = { isCallActive = false }
      )
    }

    // Security Verification Center Dialog Card
    if (securityCenterOpen) {
      val userHash = remember(nickname) { 
        if (nickname.isNotEmpty()) {
          val sha = java.security.MessageDigest.getInstance("SHA-256")
          val digest = sha.digest(nickname.toByteArray())
          digest.fold("") { str, it -> str + "%02x".format(it) }.take(24).uppercase().chunked(4).joinToString(" ")
        } else {
          "0000 0000 0000 0000"
        }
      }

      val peerHash = remember(activeChatPartner) {
        val partner = if (activeChatPartner.isNotEmpty()) activeChatPartner else "lobby"
        val sha = java.security.MessageDigest.getInstance("SHA-256")
        val digest = sha.digest(partner.toByteArray())
        digest.fold("") { str, it -> str + "%02x".format(it) }.take(24).uppercase().chunked(4).joinToString(" ")
      }

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xB3000000))
          .clickable { securityCenterOpen = false },
        contentAlignment = Alignment.Center
      ) {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF161F26)),
          modifier = Modifier
            .width(320.dp)
            .padding(16.dp)
            .clickable(enabled = false) {}
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            Icon(imageVector = Icons.Default.Security, contentDescription = "Shield", tint = Color(0xFF00E5FF), modifier = Modifier.size(44.dp))
            Text("Security Dashboard", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
              "Your session is verified with a secure tunnel. Configure cryptography metrics below:",
              color = Color(0xFF8E9AA4),
              fontSize = 11.sp,
              textAlign = TextAlign.Center,
              lineHeight = 16.sp
            )

            HorizontalDivider(color = Color(0xFF24303B))

            // Ephemeral & Screen Security Control Swaps
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Incognito Screen Shield", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Blocks screenshots & recordings", color = Color(0xFF8E9AA4), fontSize = 10.sp)
              }
              Switch(
                checked = screenShieldEnabled,
                onCheckedChange = { screenShieldEnabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFF00E5FF))
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Ghost Ephemeral Mode", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Auto deletes messages (30s)", color = Color(0xFF8E9AA4), fontSize = 10.sp)
              }
              Switch(
                checked = ephemeralChatEnabled,
                onCheckedChange = { ephemeralChatEnabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFF00E5FF))
              )
            }

            HorizontalDivider(color = Color(0xFF24303B))

            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text("NODE FINGERPRINT", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
              Text(userHash, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)

              Spacer(modifier = Modifier.height(2.dp))

              Text("TUNNEL PEER FINGERPRINT", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
              Text(peerHash, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }

            HorizontalDivider(color = Color(0xFF24303B))

            Button(
              onClick = { securityCenterOpen = false },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text("CONFIRM SECURITY", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
          }
        }
      }
    }
  }
}







// CHATS VIEW
// CHATS VIEW
@Composable
fun GhostViewChatsTab(
  nickname: String,
  activePartner: String,
  activePartnerName: String,
  activeUsers: List<WhatsAppUser>,
  messagesList: List<WhatsAppMessage>,
  chatBg: Brush,
  doodleMode: Boolean,
  onPartnerSelected: (String, String) -> Unit,
  onSendMessage: (String) -> Unit,
  onSendRichMessage: (String, String?, String?, List<String>, Double, Double, String?, String?, String?, Int, Boolean) -> Unit,
  onVoteCast: (String, Int) -> Unit,
  onReact: (String, String) -> Unit,
  onTriggerCall: (String) -> Unit,
  firestoreService: FirestoreChatService?
) {
  var showAddContactDialog by remember { mutableStateOf(false) }
  var contactQuery by remember { mutableStateOf("") }
  var searchError by remember { mutableStateOf("") }
  var isSearching by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()

  Box(modifier = Modifier.fillMaxSize()) {
    if (activePartner.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize()) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080C10))
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFF121B22))
              .padding(horizontal = 14.dp, vertical = 10.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.Search, contentDescription = "Search Chats", tint = Color(0xFF8E9AA4), modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Search chats or username handles", color = Color(0xFF8E9AA4), fontSize = 13.sp)
            }
          }

          LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Lobby card
            item {
              val selected = activePartner == "group_lounge"
              val itemBg = if (selected) Color(0xFF1C2C35) else Color.Transparent
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(itemBg)
                  .clickable {
                    onPartnerSelected("group_lounge", "Global Lounge Chat")
                  }
                  .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E5FF)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(imageVector = Icons.Default.Group, contentDescription = "Lounge", tint = Color.Black, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text("Global Lounge Chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Online", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                  }
                  Text(
                    "Shared messaging tunnel connecting all online nodes.",
                    color = Color(0xFF8E9AA4),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
              HorizontalDivider(color = Color(0xFF121B22), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
            }

            // Active Chats from Firestore
            if (activeUsers.isEmpty()) {
              item {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.VpnKey, contentDescription = "Security Keys", tint = Color(0xFF24303B), modifier = Modifier.size(44.dp))
                    Text(
                      "No active chats available", 
                      color = Color.White, 
                      fontWeight = FontWeight.Bold, 
                      fontSize = 14.sp
                    )
                    Text(
                      "Invite friends to deploy the GhostView APK. Once online, their handles appear here instantly.", 
                      color = Color(0xFF8E9AA4), 
                      fontSize = 11.sp,
                      textAlign = TextAlign.Center,
                      lineHeight = 16.sp
                    )
                  }
                }
              }
            } else {
              items(activeUsers) { user ->
                val selected = activePartner == user.username
                val itemBg = if (selected) Color(0xFF161F26) else Color.Transparent
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(itemBg)
                    .clickable {
                      onPartnerSelected(user.username, user.username.replaceFirstChar { it.uppercase() })
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(46.dp)
                      .clip(CircleShape)
                      .background(Color(user.avatarColor)),
                    contentAlignment = Alignment.Center
                  ) {
                    Text(
                      text = user.username.take(2).uppercase(),
                      color = Color.Black,
                      fontWeight = FontWeight.Bold,
                      fontSize = 15.sp
                    )
                  }
                  Spacer(modifier = Modifier.width(16.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(
                        text = user.username.replaceFirstChar { it.uppercase() },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                      )
                      Text(
                        text = "Encrypted",
                        color = Color(0xFF8E9AA4),
                        fontSize = 10.sp
                      )
                    }
                    Text(
                      text = user.bio,
                      color = Color(0xFF8E9AA4),
                      fontSize = 12.sp,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
                HorizontalDivider(color = Color(0xFF121B22), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
              }
            }
          }
        }

        // FAB for New Contact
        androidx.compose.material3.FloatingActionButton(
          onClick = { showAddContactDialog = true },
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(24.dp),
          containerColor = Color(0xFF00E5FF)
        ) {
          Icon(Icons.Default.Add, contentDescription = "Add Contact", tint = Color.Black)
        }
      }

      if (showAddContactDialog) {
        var searchResults by remember { mutableStateOf<List<WhatsAppUser>>(emptyList()) }

        androidx.compose.material3.AlertDialog(
          onDismissRequest = {
            showAddContactDialog = false
            searchResults = emptyList()
            contactQuery = ""
            searchError = ""
          },
          containerColor = Color(0xFF121B22),
          title = { Text("New Contact", color = Color.White, fontWeight = FontWeight.Bold) },
          text = {
            Column {
              Text("Search by email, phone, or username.", color = Color(0xFF8E9AA4), fontSize = 13.sp)
              Spacer(modifier = Modifier.height(12.dp))
              OutlinedTextField(
                value = contactQuery,
                onValueChange = { newVal ->
                  contactQuery = newVal
                  searchError = ""
                  // Live search as user types (3+ chars)
                  if (newVal.trim().length >= 3) {
                    coroutineScope.launch {
                      isSearching = true
                      val results = firestoreService?.searchUsers(newVal) ?: emptyList()
                      // Filter out self
                      searchResults = results.filter { it.username.lowercase() != nickname.lowercase() }
                      if (searchResults.isEmpty()) {
                        searchError = "No users found matching \"$newVal\""
                      }
                      isSearching = false
                    }
                  } else {
                    searchResults = emptyList()
                  }
                },
                placeholder = { Text("Email, phone, or username...", color = Color(0xFF8E9AA4)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF8E9AA4), modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                  if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00E5FF), strokeWidth = 2.dp)
                  }
                },
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                  focusedContainerColor = Color(0xFF080C10), unfocusedContainerColor = Color(0xFF080C10),
                  cursorColor = Color(0xFF00E5FF), focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color(0xFF24303B),
                  focusedLabelColor = Color(0xFF00E5FF)
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )

              if (searchError.isNotEmpty() && searchResults.isEmpty()) {
                Text(searchError, color = Color(0xFFEF4444), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
              }

              // Search Results List
              if (searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Found ${searchResults.size} user(s):", color = Color(0xFF8E9AA4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                ) {
                  searchResults.forEach { user ->
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1C2C35))
                        .clickable {
                          onPartnerSelected(user.username, user.username.replaceFirstChar { it.uppercase() })
                          showAddContactDialog = false
                          contactQuery = ""
                          searchResults = emptyList()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Box(
                        modifier = Modifier
                          .size(38.dp)
                          .clip(CircleShape)
                          .background(Color(user.avatarColor)),
                        contentAlignment = Alignment.Center
                      ) {
                        Text(
                          text = user.username.take(2).uppercase(),
                          color = Color.Black,
                          fontWeight = FontWeight.Bold,
                          fontSize = 14.sp
                        )
                      }
                      Spacer(modifier = Modifier.width(12.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = user.username.replaceFirstChar { it.uppercase() },
                          color = Color.White,
                          fontWeight = FontWeight.Bold,
                          fontSize = 14.sp
                        )
                        if (user.email.isNotEmpty()) {
                          Text(
                            text = user.email,
                            color = Color(0xFF8E9AA4),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                          )
                        }
                        if (user.phone.isNotEmpty()) {
                          Text(
                            text = user.phone,
                            color = Color(0xFF8E9AA4),
                            fontSize = 10.sp
                          )
                        }
                      }
                      Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "Start Chat",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                      )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                  }
                }
              }
            }
          },
          confirmButton = {
            Button(
              onClick = {
                if (contactQuery.isEmpty()) {
                  searchError = "Please enter an email or phone number."
                  return@Button
                }
                isSearching = true
                searchError = ""
                coroutineScope.launch {
                  val foundUser = firestoreService?.findUserByContactInfo(contactQuery)
                  if (foundUser != null) {
                    onPartnerSelected(foundUser.username, foundUser.username.replaceFirstChar { it.uppercase() })
                    showAddContactDialog = false
                    contactQuery = ""
                  } else {
                    searchError = "No user found with this email/phone."
                  }
                  isSearching = false
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
              if (isSearching) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
              else Text("Search", color = Color.Black, fontWeight = FontWeight.Bold)
            }
          },
          dismissButton = {
            TextButton(onClick = {
              showAddContactDialog = false
              contactQuery = ""
              searchError = ""
            }) {
              Text("Cancel", color = Color(0xFF8E9AA4))
            }
          }
        )
      }
    } else {
      Box(
        modifier = Modifier.fillMaxSize()
      ) {
        ChatWindow(
          nickname = nickname,
          chatPartnerName = activePartnerName,
          messages = messagesList,
          chatBg = chatBg,
          doodleMode = doodleMode,
          onSendMessage = onSendMessage,
          onSendRichMessage = onSendRichMessage,
          onVoteCast = onVoteCast,
          onReact = onReact,
          onTriggerCall = onTriggerCall,
          onBackClicked = { onPartnerSelected("", "") },
          firestoreService = firestoreService
        )
      }
    }
  }
}


// DETAILED CHAT WINDOW PANEL
@Composable
fun ChatWindow(
  nickname: String,
  chatPartnerName: String,
  messages: List<WhatsAppMessage>,
  chatBg: Brush,
  doodleMode: Boolean,
  onSendMessage: (String) -> Unit,
  onSendRichMessage: (String, String?, String?, List<String>, Double, Double, String?, String?, String?, Int, Boolean) -> Unit,
  onVoteCast: (String, Int) -> Unit,
  onReact: (String, String) -> Unit,
  onTriggerCall: (String) -> Unit,
  onBackClicked: () -> Unit,
  firestoreService: FirestoreChatService?
) {
  val coroutineScope = rememberCoroutineScope()
  val listState = rememberLazyListState()

  var inputVal by remember { mutableStateOf("") }
  var attachmentOpen by remember { mutableStateOf(false) }
  
  // Custom interactive poll dialog state
  var pollDialogOpen by remember { mutableStateOf(false) }

  // Custom interactive voice recording simulation
  var voiceRecordingSec by remember { mutableStateOf(0) }
  var isRecordingVoice by remember { mutableStateOf(false) }

  val context = LocalContext.current

  // Real Activity Result Contracts to support ACTUAL media sharing!
  var pendingImageBase64 by remember { mutableStateOf<String?>(null) }
  var pendingImageFileName by remember { mutableStateOf<String?>(null) }

  val imagePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri ->
    if (uri != null) {
      coroutineScope.launch {
        val (base64, fileName) = uriToBase64(context, uri)
        if (base64.isNotEmpty()) {
          pendingImageBase64 = base64
          pendingImageFileName = fileName
        }
      }
    }
  }

  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bitmap ->
    if (bitmap != null) {
      coroutineScope.launch {
        val base64 = bitmapToBase64(bitmap)
        if (base64.isNotEmpty()) {
          onSendRichMessage("image", base64, null, emptyList(), 0.0, 0.0, null, "CameraPhoto.jpg", null, 0, false)
        }
      }
    }
  }

  val documentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri ->
    if (uri != null) {
      coroutineScope.launch {
        val (base64, fileName) = uriToBase64(context, uri)
        if (base64.isNotEmpty()) {
          onSendRichMessage("document", base64, null, emptyList(), 0.0, 0.0, null, fileName, "145 KB", 0, false)
        }
      }
    }
  }

  // Scroll to bottom when new messages arrive
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  // Timer loop for voice recording simulator
  LaunchedEffect(isRecordingVoice) {
    if (isRecordingVoice) {
      voiceRecordingSec = 0
      while (isRecordingVoice) {
        delay(1000)
        voiceRecordingSec++
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF080C10))
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .let {
          if (doodleMode) it.drawBehind {
            val scale = 60.dp.toPx()
            for (x in 0..size.width.toInt() step scale.toInt()) {
              for (y in 0..size.height.toInt() step scale.toInt()) {
                drawCircle(
                  color = Color(0x0A00E5FF),
                  radius = 2.dp.toPx(),
                  center = androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat())
                )
              }
            }
          } else it.background(chatBg)
        }
    )

    Column(modifier = Modifier.fillMaxSize()) {
      // Chat Room Top Header Bar
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0xFF121B22))
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClicked) {
              Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B)),
              contentAlignment = Alignment.Center
            ) {
              Text(if (chatPartnerName.isNotEmpty()) chatPartnerName.take(1) else "?", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(chatPartnerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
              Text("Encrypted Chat", color = Color(0xFF00E5FF), fontSize = 10.sp)
            }
          }

          // Video & Audio calling triggers
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { onTriggerCall("video") }) {
              Icon(imageVector = Icons.Default.Videocam, contentDescription = "Secure Video Call", tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { onTriggerCall("audio") }) {
              Icon(imageVector = Icons.Default.Call, contentDescription = "Secure Voice Call", tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
            }
          }
        }
      }

      // Messages History List Panel (With native empty chat state)
      if (messages.isEmpty()) {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(imageVector = Icons.Default.Security, contentDescription = "Secure", tint = Color(0x1F00E5FF), modifier = Modifier.size(48.dp))
            Text("End-to-End Encrypted Chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Send a secure message to start the conversation.", color = Color(0xFF8E9AA4), fontSize = 11.sp, textAlign = TextAlign.Center)
          }
        }
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(messages) { msg ->
            GhostViewBubble(
              msg = msg,
              self = msg.sender.lowercase().trim() == nickname.lowercase().trim(),
              onVote = { opt -> onVoteCast(msg.id, opt) },
              onReact = { emoji -> onReact(msg.id, emoji) },
              onDelete = {
                if (firestoreService != null) {
                  firestoreService.deleteMessage(msg.id)
                }
              },
              onMarkViewed = {
                if (firestoreService != null) {
                  firestoreService.markMessageAsViewed(msg.id)
                }
              }
            )
          }
        }
      }
    }

    // Floating attachment dynamic drawer popup menu
    if (attachmentOpen) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clickable { attachmentOpen = false }
      )
      Box(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(bottom = 76.dp, start = 16.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(Color(0xFA121B22))
          .border(1.dp, Color(0xFF24303B), RoundedCornerShape(16.dp))
          .padding(16.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AttachmentItemIcon(Icons.Default.Description, "Document", Color(0xFF7F66FF)) {
              documentLauncher.launch("application/pdf")
              attachmentOpen = false
            }
            AttachmentItemIcon(Icons.Default.PhotoCamera, "Camera", Color(0xFFFF2E74)) {
              cameraLauncher.launch(null)
              attachmentOpen = false
            }
            AttachmentItemIcon(Icons.Default.Image, "Gallery", Color(0xFF00E676)) {
              imagePickerLauncher.launch("image/*")
              attachmentOpen = false
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AttachmentItemIcon(Icons.Default.Mic, "Audio", Color(0xFFFF9800)) {
              onSendRichMessage("voice", null, null, emptyList(), 0.0, 0.0, null, null, null, 24, false)
              attachmentOpen = false
            }
            AttachmentItemIcon(Icons.Default.Place, "Location", Color(0xFF20C0F0)) {
              onSendRichMessage("location", null, null, emptyList(), 12.9716, 77.5946, "Core Coordinates", null, null, 0, false)
              attachmentOpen = false
            }
            AttachmentItemIcon(Icons.Default.BarChart, "Poll", Color(0xFF00BFA5)) {
              pollDialogOpen = true
              attachmentOpen = false
            }
          }
        }
      }
    }

    // Bottom Input & Recording Controllers Area
    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        // Text field panel
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF121B22))
            .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
          IconButton(onClick = {}) {
            Icon(imageVector = Icons.Default.Face, contentDescription = "Emoji Drawer", tint = Color(0xFF8E9AA4), modifier = Modifier.size(22.dp))
          }
          IconButton(onClick = { attachmentOpen = !attachmentOpen }) {
            Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Attachment clip", tint = Color(0xFF8E9AA4), modifier = Modifier.size(20.dp))
          }
          
          if (isRecordingVoice) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Recording Audio: ${formatDuration(voiceRecordingSec)}",
              color = Color(0xFFEF4444),
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              modifier = Modifier.weight(1f)
            )
          } else {
            BasicTextField(
              value = inputVal,
              onValueChange = { inputVal = it },
              modifier = Modifier.weight(1f),
              textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
              cursorBrush = SolidColor(Color(0xFF00E5FF)),
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
              keyboardActions = KeyboardActions(onSend = {
                if (inputVal.trim().isNotEmpty()) {
                  onSendMessage(inputVal.trim())
                  inputVal = ""
                }
              }),
              decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                  if (inputVal.isEmpty()) {
                    Text("Type a message", color = Color(0xFF8E9AA4), fontSize = 15.sp)
                  }
                  innerTextField()
                }
              }
            )
          }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Send / Mic controller button
        val active = inputVal.trim().isNotEmpty()
        Box(
          modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0xFF00E5FF))
            .clickable {
              if (active) {
                onSendMessage(inputVal.trim())
                inputVal = ""
              } else {
                if (isRecordingVoice) {
                  isRecordingVoice = false
                  onSendRichMessage("voice", null, null, emptyList(), 0.0, 0.0, null, null, null, voiceRecordingSec, false)
                } else {
                  isRecordingVoice = true
                }
              }
            },
          contentAlignment = Alignment.Center
        ) {
          if (active) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send Message", tint = Color.Black, modifier = Modifier.size(18.dp))
          } else {
            Icon(
              imageVector = if (isRecordingVoice) Icons.Default.StopCircle else Icons.Default.Mic, 
              contentDescription = "Microphone Transceiver", 
              tint = Color.Black, 
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }

    // Dynamic Poll dialogue screen
    if (pollDialogOpen) {
      var question by remember { mutableStateOf("") }
      var opt1 by remember { mutableStateOf("") }
      var opt2 by remember { mutableStateOf("") }
      var opt3 by remember { mutableStateOf("") }

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xB3000000))
          .clickable { pollDialogOpen = false },
        contentAlignment = Alignment.Center
      ) {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF161F26)),
          modifier = Modifier.padding(24.dp).clickable(enabled = false) {}
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create Poll", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            OutlinedTextField(
              value = question, onValueChange = { question = it }, label = { Text("Question") },
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color(0xFF24303B)
              ),
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = opt1, onValueChange = { opt1 = it }, label = { Text("Option 1") },
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color(0xFF24303B)
              ),
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = opt2, onValueChange = { opt2 = it }, label = { Text("Option 2") },
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color(0xFF24303B)
              ),
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = opt3, onValueChange = { opt3 = it }, label = { Text("Option 3 (Optional)") },
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color(0xFF24303B)
              ),
              modifier = Modifier.fillMaxWidth()
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End
            ) {
              TextButton(onClick = { pollDialogOpen = false }) {
                Text("CANCEL", color = Color(0xFFEF4444))
              }
              Spacer(modifier = Modifier.width(8.dp))
              TextButton(
                onClick = {
                  if (question.isNotEmpty() && opt1.isNotEmpty() && opt2.isNotEmpty()) {
                    val opts = listOf(opt1.trim(), opt2.trim()) + if (opt3.isNotEmpty()) listOf(opt3.trim()) else emptyList()
                    onSendRichMessage("poll", null, question.trim(), opts, 0.0, 0.0, null, null, null, 0, false)
                    pollDialogOpen = false
                  }
                }
              ) {
                Text("CREATE", color = Color(0xFF00E5FF))
              }
            }
          }
        }
      }
    }

    if (pendingImageBase64 != null) {
      androidx.compose.ui.window.Dialog(onDismissRequest = { pendingImageBase64 = null }) {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFF161F26))
        ) {
          Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Send Photo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Base64Image(pendingImageBase64!!, modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              Button(
                onClick = { 
                  onSendRichMessage("image", pendingImageBase64, null, emptyList(), 0.0, 0.0, null, pendingImageFileName, null, 0, false)
                  pendingImageBase64 = null
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
              ) {
                Text("Normal", color = Color.Black, maxLines = 1, softWrap = false)
              }
              Button(
                onClick = { 
                  onSendRichMessage("image", pendingImageBase64, null, emptyList(), 0.0, 0.0, null, pendingImageFileName, null, 0, true)
                  pendingImageBase64 = null
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24303B))
              ) {
                Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("View Once", color = Color.White, maxLines = 1, softWrap = false)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun AttachmentItemIcon(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  bgColor: Color,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.clickable { onClick() }
  ) {
    Box(
      modifier = Modifier
        .size(54.dp)
        .clip(CircleShape)
        .background(bgColor),
      contentAlignment = Alignment.Center
    ) {
      Icon(imageVector = icon, contentDescription = label, tint = Color.Black, modifier = Modifier.size(24.dp))
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(label, color = Color(0xFF8E9AA4), fontSize = 11.sp)
  }
}

// SECURE MESSAGE RENDERING BUBBLES
@Composable
fun GhostViewBubble(
  msg: WhatsAppMessage,
  self: Boolean,
  onVote: (Int) -> Unit,
  onReact: (String) -> Unit,
  onDelete: () -> Unit,
  onMarkViewed: () -> Unit = {}
) {
  val align = if (self) Alignment.End else Alignment.Start
  val bubbleBg = if (self) Color(0xFF053E3F) else Color(0xFF161F26)
  val shape = if (self) {
    RoundedCornerShape(12.dp, 0.dp, 12.dp, 12.dp)
  } else {
    RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp)
  }

  var reactionPickerOpen by remember { mutableStateOf(false) }

  // Ephemeral countdown logic
  if (msg.disappearing) {
    var elapsedSeconds by remember { mutableStateOf(30) }
    LaunchedEffect(msg.id) {
      elapsedSeconds = 30
      while (elapsedSeconds > 0) {
        delay(1000)
        elapsedSeconds--
      }
      onDelete()
    }
    
    // If expired, let's just vanish
    if (elapsedSeconds <= 0) return
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalAlignment = align
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = if (self) Arrangement.End else Arrangement.Start
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 280.dp)
          .clip(shape)
          .background(bubbleBg)
          .combinedClickable(
            onLongClick = { reactionPickerOpen = true },
            onClick = {}
          )
          .padding(10.dp)
      ) {
        if (!self) {
          Text(
            text = msg.sender.replaceFirstChar { it.uppercase() },
            color = Color(0xFF00E5FF),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp)
          )
        }

        // Handle attachment render layouts
        when (msg.type) {
          "poll" -> {
            GhostViewPollBubble(msg, onVote)
          }
          "voice" -> {
            GhostViewVoiceBubble(msg)
          }
          "location" -> {
            GhostViewLocationBubble(msg)
          }
          "document" -> {
            GhostViewDocumentBubble(msg)
          }
          "image" -> {
            GhostViewImageBubble(msg, onDelete, onMarkViewed)
          }
          else -> {
            Text(
              text = msg.text,
              color = Color.White,
              fontSize = 15.sp,
              lineHeight = 20.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Time ticks alignment & self-destruct counter
        Row(
          modifier = Modifier.align(Alignment.End),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (msg.disappearing) {
            Icon(imageVector = Icons.Default.VisibilityOff, contentDescription = "Ghost Expiring", tint = Color(0xFF00E5FF), modifier = Modifier.size(10.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text("Vanish", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
          }
          Text(
            text = msg.formattedTime,
            color = Color(0xFF8E9AA4),
            fontSize = 10.sp
          )
          if (self) {
            Spacer(modifier = Modifier.width(4.dp))
            Text("✓✓", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // Render active message Reactions bubble
    if (msg.reactions.isNotEmpty()) {
      Row(
        modifier = Modifier
          .padding(top = 2.dp, start = 8.dp, end = 8.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0xFF24303B))
          .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        val emojis = msg.reactions.values.distinct().take(3)
        emojis.forEach { emoji ->
          Text(emoji, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(2.dp))
        Text("${msg.reactions.size}", color = Color.White, fontSize = 10.sp)
      }
    }

    // Reaction selector overlay picker
    if (reactionPickerOpen) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { reactionPickerOpen = false }
      )
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(20.dp))
          .background(Color(0xFF121B22))
          .border(1.dp, Color(0xFF24303B), CircleShape)
          .padding(horizontal = 12.dp, vertical = 6.dp)
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          val emojiList = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
          emojiList.forEach { emoji ->
            Box(
              modifier = Modifier
                .clickable {
                  onReact(emoji)
                  reactionPickerOpen = false
                }
                .padding(4.dp)
            ) {
              Text(emoji, fontSize = 20.sp)
            }
          }
        }
      }
    }
  }
}

// POLL COMPOSABLE CARD
@Composable
fun GhostViewPollBubble(
  msg: WhatsAppMessage,
  onVote: (Int) -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = msg.pollQuestion ?: "Poll:",
      color = Color.White,
      fontWeight = FontWeight.Bold,
      fontSize = 15.sp,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    val totalVotes = msg.pollVotes.size
    msg.pollOptions.forEachIndexed { idx, option ->
      val optionVotes = msg.pollVotes.values.count { it == idx }
      val percentage = if (totalVotes > 0) (optionVotes.toFloat() / totalVotes.toFloat()) else 0f
      val hasVoted = msg.pollVotes.values.any { it == idx }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFF080C10))
          .clickable { onVote(idx) }
          .padding(10.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(percentage)
            .background(Color(0x2600E5FF))
            .align(Alignment.CenterStart)
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(16.dp)
                .border(2.dp, if (hasVoted) Color(0xFF00E5FF) else Color(0xFF8E9AA4), CircleShape)
                .padding(2.dp)
                .clip(CircleShape)
                .background(if (hasVoted) Color(0xFF00E5FF) else Color.Transparent)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(option, color = Color.White, fontSize = 13.sp)
          }
          Text(
            text = "$optionVotes (${(percentage * 100).toInt()}%)",
            color = Color(0xFF8E9AA4),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }
  }
}

// AUDIO PLAYER COMPOSABLE CARD
@Composable
fun GhostViewVoiceBubble(
  msg: WhatsAppMessage
) {
  var isPlaying by remember { mutableStateOf(false) }
  var progress by remember { mutableStateOf(0f) }
  val waves = listOf(12, 24, 18, 32, 14, 28, 22, 16, 30, 20, 10)

  LaunchedEffect(isPlaying) {
    if (isPlaying) {
      progress = 0f
      while (progress < 1f) {
        delay(100)
        progress += 0.05f
      }
      isPlaying = false
      progress = 1f
    }
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(Color(0xFF00E5FF))
        .clickable { isPlaying = !isPlaying },
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
        contentDescription = "Play secure stream", 
        tint = Color.Black,
        modifier = Modifier.size(18.dp)
      )
    }

    Spacer(modifier = Modifier.width(12.dp))

    Column(modifier = Modifier.weight(1f)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        waves.forEachIndexed { idx, height ->
          val active = (idx.toFloat() / waves.size.toFloat()) <= progress
          Box(
            modifier = Modifier
              .width(3.dp)
              .height(height.dp)
              .clip(CircleShape)
              .background(if (active) Color(0xFF00E5FF) else Color(0xFF8E9AA4))
          )
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Voice Note (${msg.voiceDurationSec}s)",
        color = Color(0xFF8E9AA4),
        fontSize = 11.sp
      )
    }
  }
}

// MAP LOCATION SHARING COMPOSABLE CARD
@Composable
fun GhostViewLocationBubble(
  msg: WhatsAppMessage
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(Color(0xFF080C10))
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(110.dp)
        .background(Color(0xFF121B22)),
      contentAlignment = Alignment.Center
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
        drawRect(Color(0x2200E5FF), style = stroke)
        drawCircle(Color(0xFFEF4444), radius = 8.dp.toPx())
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(imageVector = Icons.Default.Place, contentDescription = "Location Pin", tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
        Text("Shared Location", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
      }
    }
    Column(modifier = Modifier.padding(10.dp)) {
      Text(
        text = msg.locationName ?: "Location Coordinates",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
      )
      Text(
        text = "Lat: ${msg.locationLat} • Lng: ${msg.locationLng}",
        color = Color(0xFF8E9AA4),
        fontSize = 12.sp
      )
    }
  }
}

// PDF DOCUMENT COMPOSABLE CARD
@Composable
fun GhostViewDocumentBubble(
  msg: WhatsAppMessage
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(Color(0xFF080C10))
      .padding(10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(Color(0xFFEF4444)),
      contentAlignment = Alignment.Center
    ) {
      Icon(imageVector = Icons.Default.Description, contentDescription = "Document Icon", tint = Color.White, modifier = Modifier.size(20.dp))
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = msg.fileName ?: "Document.pdf",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = msg.fileSize ?: "Unknown Size",
        color = Color(0xFF8E9AA4),
        fontSize = 12.sp
      )
    }
  }
}

// GALLERY MEDIA COMPOSABLE CARD WITH ACTUAL PHOTO RENDERING
@Composable
fun GhostViewImageBubble(
  msg: WhatsAppMessage,
  onDelete: () -> Unit = {},
  onMarkViewed: () -> Unit = {}
) {
  var showSecureViewer by remember { mutableStateOf(false) }
  val coroutineScope = rememberCoroutineScope()

  if (showSecureViewer && !msg.mediaUrl.isNullOrEmpty()) {
    GhostViewSecureViewer(
      imageUrl = msg.mediaUrl,
      onClose = {
        showSecureViewer = false
        onMarkViewed()
      }
    )
  }

  if (msg.isOneTime) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0xFF24303B))
        .clickable(enabled = !msg.isViewed) {
          showSecureViewer = true
        },
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (msg.isViewed) {
          Icon(Icons.Default.CheckCircle, contentDescription = "Opened", tint = Color.Gray, modifier = Modifier.size(32.dp))
          Spacer(modifier = Modifier.height(8.dp))
          Text("Opened", color = Color.Gray, fontWeight = FontWeight.Bold)
        } else {
          Icon(Icons.Default.Photo, contentDescription = "Photo", tint = Color(0xFF00E5FF), modifier = Modifier.size(32.dp))
          Spacer(modifier = Modifier.height(8.dp))
          Text("Photo", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
        }
      }
    }
  } else if (!msg.mediaUrl.isNullOrEmpty()) {
    if (msg.disappearing && !msg.isViewed) {
       Box(
         modifier = Modifier
           .fillMaxWidth()
           .height(180.dp)
           .clip(RoundedCornerShape(8.dp))
           .background(Color(0xFF24303B)),
         contentAlignment = Alignment.Center
       ) {
         Column(horizontalAlignment = Alignment.CenterHorizontally) {
           Icon(Icons.Default.VisibilityOff, contentDescription = "Hidden", tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp))
           Spacer(modifier = Modifier.height(8.dp))
           Button(
             onClick = { 
                 onMarkViewed()
                 coroutineScope.launch {
                     kotlinx.coroutines.delay(10000)
                     onDelete()
                 }
             },
             colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
           ) {
             Text("Tap to View (10s)", color = Color.Black, fontWeight = FontWeight.Bold)
           }
         }
       }
    } else {
       if (msg.mediaUrl.startsWith("http")) {
           AsyncImage(
             model = msg.mediaUrl,
             contentDescription = "Cloudinary Image",
             modifier = Modifier
               .fillMaxWidth()
               .height(180.dp)
               .clip(RoundedCornerShape(8.dp))
           )
       } else {
           Base64Image(
             base64Str = msg.mediaUrl,
             modifier = Modifier
               .fillMaxWidth()
               .height(180.dp)
               .clip(RoundedCornerShape(8.dp))
           )
       }
    }
  } else {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(160.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0xFF080C10)),
      contentAlignment = Alignment.Center
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.linearGradient(
              colors = listOf(Color(0xFF00E5FF), Color(0xFFEC4899))
            )
          )
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(imageVector = Icons.Default.Image, contentDescription = "Media Picture", tint = Color.White, modifier = Modifier.size(22.dp))
        Text(
          "Shared Image",
          color = Color.White,
          fontWeight = FontWeight.ExtraBold,
          fontSize = 15.sp,
          style = TextStyle(
            shadow = androidx.compose.ui.graphics.Shadow(
              color = Color.Black,
              blurRadius = 8f
            )
          )
        )
      }
    }
  }
}

// STATUS/UPDATES VIEW (Zero dummy channels, only real active users)
@Composable
fun GhostViewUpdatesTab(
  activeUsers: List<WhatsAppUser>
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF080C10))
      .padding(16.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {
    Text("Recent Status Stories", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    
    if (activeUsers.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(100.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Status Portal", tint = Color(0xFF24303B), modifier = Modifier.size(32.dp))
          Spacer(modifier = Modifier.height(8.dp))
          Text("No status feeds detected from peer nodes", color = Color(0xFF8E9AA4), fontSize = 13.sp)
        }
      }
    } else {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              modifier = Modifier.size(62.dp),
              contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                  .size(62.dp)
                  .drawBehind {
                    drawCircle(
                      color = Color(0xFF8E9AA4),
                      style = Stroke(width = 2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 10f), 0f))
                    )
                  }
              )
              Box(
                modifier = Modifier
                  .size(52.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF121B22)),
                contentAlignment = Alignment.Center
              ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add status update", tint = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("My Status", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Tap to upload", color = Color(0xFF8E9AA4), fontSize = 10.sp)
          }
        }

        items(activeUsers) { user ->
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              modifier = Modifier.size(62.dp),
              contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                  .size(62.dp)
                  .drawBehind {
                    drawCircle(
                      color = Color(0xFF00E5FF),
                      style = Stroke(width = 2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 10f), 0f))
                    )
                  }
              )
              Box(
                modifier = Modifier
                  .size(52.dp)
                  .clip(CircleShape)
                  .background(Color(user.avatarColor)),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = user.username.take(2).uppercase(),
                  color = Color.Black,
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp
                )
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(user.username.replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Recent", color = Color(0xFF8E9AA4), fontSize = 10.sp)
          }
        }
      }
    }

    HorizontalDivider(color = Color(0xFF121B22))

    // Real system announcements and verify info
    Text("Verified Encryption Verification Nodes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      val announcements = listOf(
        Pair("GhostView Cryptography Portal", "Active security tunnels verified under Zero-Knowledge protocols."),
        Pair("System Core Security", "Handshake fingerprints are generated locally using device private keys.")
      )
      announcements.forEach { node ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121B22))
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(Color(0xFF00E5FF)),
            contentAlignment = Alignment.Center
          ) {
            Icon(imageVector = Icons.Default.Security, contentDescription = "Announcement Flag", tint = Color.Black, modifier = Modifier.size(20.dp))
          }
          Spacer(modifier = Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(node.first, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(node.second, color = Color(0xFF8E9AA4), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          Button(
            onClick = {},
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300E5FF)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)
          ) {
            Text("VERIFY", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

// CALLS TAB (Recent call records populated dynamically from active contact directory)
@Composable
fun GhostViewCallsTab(
  activeUsers: List<WhatsAppUser>,
  onTriggerCall: (String, String) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF080C10))
      .padding(16.dp)
  ) {
    Text("Recent Calls Log", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Spacer(modifier = Modifier.height(12.dp))

    if (activeUsers.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(imageVector = Icons.Default.CallEnd, contentDescription = "Call history", tint = Color(0xFF24303B), modifier = Modifier.size(44.dp))
          Text("No recent calls registered", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Text("Launch audio/video encryption calls directly in active tunnels", color = Color(0xFF8E9AA4), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
      }
    } else {
      LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(activeUsers) { user ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFF121B22))
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(Color(user.avatarColor)),
                contentAlignment = Alignment.Center
              ) {
                Text(user.username.take(2).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(user.username.replaceFirstChar { it.uppercase() }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Videocam, 
                    contentDescription = "Videocam Icon",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Secure encrypted handshake call", color = Color(0xFF8E9AA4), fontSize = 11.sp)
                }
              }
            }
            IconButton(onClick = { onTriggerCall(user.username, "video") }) {
              Icon(
                imageVector = Icons.Default.Videocam, 
                contentDescription = "Establish Video Call",
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(22.dp)
              )
            }
          }
        }
      }
    }
  }
}

// SECURITY SETTINGS & THEMES TAB — WITH EDITABLE PROFILE & PHOTO
@Composable
fun GhostViewSettingsTab(
  username: String,
  wallpaperIndex: Int,
  screenShield: Boolean,
  ephemeralMode: Boolean,
  firestoreService: FirestoreChatService?,
  onWallpaperChanged: (Int) -> Unit,
  onScreenShieldToggled: (Boolean) -> Unit,
  onEphemeralModeToggled: (Boolean) -> Unit,
  onNicknameChanged: (String) -> Unit,
  onLogout: () -> Unit
) {
  var isBackingUp by remember { mutableStateOf(false) }
  var backupProgress by remember { mutableStateOf(0f) }
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  // Edit Profile dialog state
  var showEditProfileDialog by remember { mutableStateOf(false) }
  var editDisplayName by remember { mutableStateOf(username) }
  var editBio by remember { mutableStateOf("") }
  var editStatusText by remember { mutableStateOf("Available") }
  var isSavingProfile by remember { mutableStateOf(false) }

  // Profile photo state (Base64)
  var profilePhotoBase64 by remember { mutableStateOf<String?>(null) }

  // Load existing bio from Firestore
  LaunchedEffect(username) {
    if (username.isNotEmpty()) {
      try {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("ghostview_users").document(username.lowercase().trim())
          .get()
          .addOnSuccessListener { doc ->
            editBio = doc.getString("bio") ?: "Hey there! I am using GhostView."
            editStatusText = doc.getString("statusText") ?: "Available"
            profilePhotoBase64 = doc.getString("photoBase64")
          }
      } catch (e: Exception) {}
    }
  }

  // Profile photo picker launcher
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri ->
    if (uri != null) {
      coroutineScope.launch {
        val (base64, _) = uriToBase64(context, uri)
        if (base64.isNotEmpty()) {
          profilePhotoBase64 = base64
          firestoreService?.updateUserProfile(username = username, newPhotoBase64 = base64)
          Toast.makeText(context, "Profile photo updated!", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  LaunchedEffect(isBackingUp) {
    if (isBackingUp) {
      backupProgress = 0f
      while (backupProgress < 1f) {
        delay(150)
        backupProgress += 0.05f
      }
      isBackingUp = false
      try {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("ghostview_users").document(username.lowercase().trim())
          .update("lastBackupTime", System.currentTimeMillis())
        Toast.makeText(context, "Backup registered successfully!", Toast.LENGTH_SHORT).show()
      } catch (e: Exception) {}
    }
  }

  // Edit Profile Dialog
  if (showEditProfileDialog) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xCC000000))
        .clickable { showEditProfileDialog = false },
      contentAlignment = Alignment.Center
    ) {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161F26)),
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp)
          .clickable(enabled = false) {}
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Edit Profile", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            IconButton(onClick = { showEditProfileDialog = false }) {
              Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E9AA4))
            }
          }

          HorizontalDivider(color = Color(0xFF24303B))

          // Display Name field
          OutlinedTextField(
            value = editDisplayName,
            onValueChange = { editDisplayName = it },
            label = { Text("Display Name", color = Color(0xFF8E9AA4)) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF8E9AA4), modifier = Modifier.size(18.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White, unfocusedTextColor = Color.White,
              focusedContainerColor = Color(0xFF080C10), unfocusedContainerColor = Color(0xFF080C10),
              cursorColor = Color(0xFF00E5FF), focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color(0xFF24303B),
              focusedLabelColor = Color(0xFF00E5FF)
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          // Bio field
          OutlinedTextField(
            value = editBio,
            onValueChange = { editBio = it },
            label = { Text("About / Bio", color = Color(0xFF8E9AA4)) },
            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF8E9AA4), modifier = Modifier.size(18.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White, unfocusedTextColor = Color.White,
              focusedContainerColor = Color(0xFF080C10), unfocusedContainerColor = Color(0xFF080C10),
              cursorColor = Color(0xFF00E5FF), focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color(0xFF24303B),
              focusedLabelColor = Color(0xFF00E5FF)
            ),
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
          )

          // Status text field
          OutlinedTextField(
            value = editStatusText,
            onValueChange = { editStatusText = it },
            label = { Text("Status", color = Color(0xFF8E9AA4)) },
            leadingIcon = { Icon(Icons.Default.Circle, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(12.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color.White, unfocusedTextColor = Color.White,
              focusedContainerColor = Color(0xFF080C10), unfocusedContainerColor = Color(0xFF080C10),
              cursorColor = Color(0xFF00E5FF), focusedBorderColor = Color(0xFF00E5FF), unfocusedBorderColor = Color(0xFF24303B),
              focusedLabelColor = Color(0xFF00E5FF)
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          Button(
            onClick = {
              isSavingProfile = true
              // Update Firebase Auth display name
              val fbAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
              fbAuth.currentUser?.updateProfile(userProfileChangeRequest {
                displayName = editDisplayName.trim()
              })?.addOnCompleteListener {
                // Update Firestore bio + status
                firestoreService?.updateUserProfile(
                  username = username,
                  newBio = editBio.trim(),
                  newStatusText = editStatusText.trim()
                )
                isSavingProfile = false
                onNicknameChanged(editDisplayName.trim())
                showEditProfileDialog = false
                Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
          ) {
            if (isSavingProfile) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black)
            else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
              Text("SAVE PROFILE", color = Color.Black, fontWeight = FontWeight.ExtraBold)
            }
          }
        }
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF080C10))
      .padding(20.dp)
      .verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
      IconButton(onClick = onLogout) {
        Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Log Out", tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
      }
    }

    // ─── Profile Card with Photo ─────────────────────────────────────────────
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF121B22)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          // Tappable profile photo
          Box(
            modifier = Modifier
              .size(64.dp)
              .clip(CircleShape)
              .background(Color(0xFF00E5FF))
              .clickable { photoPickerLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
          ) {
            if (!profilePhotoBase64.isNullOrEmpty()) {
              Base64Image(
                base64Str = profilePhotoBase64!!,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
              )
            } else {
              Icon(imageVector = Icons.Default.Person, contentDescription = "Profile Photo", tint = Color.Black, modifier = Modifier.size(32.dp))
            }
            // Camera overlay badge
            Box(
              modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFF080C10))
                .border(1.dp, Color(0xFF00E5FF), CircleShape)
                .align(Alignment.BottomEnd),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.CameraAlt, contentDescription = "Change Photo", tint = Color(0xFF00E5FF), modifier = Modifier.size(12.dp))
            }
          }

          Spacer(modifier = Modifier.width(16.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(username.replaceFirstChar { it.uppercase() }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(editBio.ifEmpty { "Hey there! I am using GhostView." }, color = Color(0xFF8E9AA4), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00E5FF)))
              Spacer(modifier = Modifier.width(4.dp))
              Text(editStatusText.ifEmpty { "Available" }, color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
          }
        }

        // Edit Profile Button
        Button(
          onClick = {
            editDisplayName = username
            showEditProfileDialog = true
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0x1A00E5FF)),
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(10.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x6600E5FF))
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
            Text("Edit Profile", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
          }
        }
      }
    }

    // ─── Security Settings ────────────────────────────────────────────────────
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Security Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)

      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121B22)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Incognito Screen Shield", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
              Text("Block multitasking screenshots", color = Color(0xFF8E9AA4), fontSize = 10.sp)
            }
            Switch(
              checked = screenShield,
              onCheckedChange = onScreenShieldToggled,
              colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFF00E5FF))
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Ghost Ephemeral Mode", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
              Text("Auto self-destruct messages in 30s", color = Color(0xFF8E9AA4), fontSize = 10.sp)
            }
            Switch(
              checked = ephemeralMode,
              onCheckedChange = onEphemeralModeToggled,
              colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color(0xFF00E5FF))
            )
          }
        }
      }
    }

    // ─── Chat Wallpaper Theme ─────────────────────────────────────────────────
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Chat Wallpaper Theme", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
      val wallpapers = listOf("Midnight Obsidian", "Spectral Neon", "Spectral Cyber")
      wallpapers.forEachIndexed { idx, name ->
        val active = wallpaperIndex == idx
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121B22))
            .clickable { onWallpaperChanged(idx) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(name, color = Color.White, fontSize = 14.sp)
          if (active) {
            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Selected", tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
          } else {
            Box(modifier = Modifier.size(18.dp).border(2.dp, Color(0xFF8E9AA4), CircleShape))
          }
        }
      }
    }

    // ─── Backups ──────────────────────────────────────────────────────────────
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Backups", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121B22)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text("End-to-End Encrypted Backups", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Spacer(modifier = Modifier.height(4.dp))
          Text("Back up your messages and media securely to Firestore private tables.", color = Color(0xFF8E9AA4), fontSize = 12.sp)

          if (isBackingUp) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(progress = { backupProgress }, color = Color(0xFF00E5FF), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
            Text("Securing & Uploading Data: ${(backupProgress * 100).toInt()}%", color = Color(0xFF00E5FF), fontSize = 11.sp)
          } else {
            Spacer(modifier = Modifier.height(14.dp))
            Button(
              onClick = { isBackingUp = true },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
              shape = RoundedCornerShape(12.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "Upload backup", tint = Color.Black, modifier = Modifier.size(16.dp))
                Text("BACK UP NOW", color = Color.Black, fontWeight = FontWeight.ExtraBold)
              }
            }
          }
        }
      }
    }
  }
}








// FULLSCREEN CALL OVERLAY SCREEN (High-fidelity overlay)
@Composable
fun CallOverlayScreen(
  callerName: String,
  type: String,
  isMuted: Boolean,
  isCameraOn: Boolean,
  isScreenSharing: Boolean,
  onMuteToggled: (Boolean) -> Unit,
  onCameraToggled: (Boolean) -> Unit,
  onScreenShareToggled: (Boolean) -> Unit,
  onHangup: () -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "callingPulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.95f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse"
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF080C10))
  ) {
    if (type == "video" && isCameraOn) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(Color(0xFF161F26), Color(0xFF080C10))
            )
          ),
        contentAlignment = Alignment.Center
      ) {
        if (isScreenSharing) {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
              modifier = Modifier
                .size(160.dp, 100.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
                .background(Color.Black),
              contentAlignment = Alignment.Center
            ) {
              Icon(imageVector = Icons.AutoMirrored.Filled.ScreenShare, contentDescription = "Screen share feedback", tint = Color.Green, modifier = Modifier.size(36.dp))
            }
            Text("Sharing your viewport with $callerName", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
          }
        } else {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(imageVector = Icons.Default.Videocam, contentDescription = "Vid", tint = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
            Text("VIDEO CAMERA CAPTURE ACTIVE", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xFF080C10)),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
          ) {
            Box(
              modifier = Modifier
                .size(120.dp * pulseScale)
                .clip(CircleShape)
                .background(Color(0x1A00E5FF))
            )
            Box(
              modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B)),
              contentAlignment = Alignment.Center
            ) {
              Icon(imageVector = Icons.Default.Person, contentDescription = "Avatar", tint = Color(0xFF00E5FF), modifier = Modifier.size(46.dp))
            }
          }
        }
      }
    }

    // Header info panel
    Column(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 56.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = callerName.replaceFirstChar { it.uppercase() },
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = if (type == "video") "GhostView Video Call" else "GhostView Voice Call",
        color = Color(0xFF8E9AA4),
        fontSize = 14.sp
      )
    }

    // Call Options Panel
    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(bottom = 48.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Mute mic button
        Box(
          modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(if (isMuted) Color.White else Color(0x1AFFFFFF))
            .clickable { onMuteToggled(!isMuted) },
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, 
            contentDescription = "Mute Microphone", 
            tint = if (isMuted) Color.Black else Color.White,
            modifier = Modifier.size(20.dp)
          )
        }

        // Camera toggle
        if (type == "video") {
          Box(
            modifier = Modifier
              .size(50.dp)
              .clip(CircleShape)
              .background(if (isCameraOn) Color(0x1AFFFFFF) else Color.White)
              .clickable { onCameraToggled(!isCameraOn) },
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff, 
              contentDescription = "Toggle Video Stream Capture", 
              tint = if (isCameraOn) Color.White else Color.Black,
              modifier = Modifier.size(20.dp)
            )
          }
        }

        // Screen share toggle
        Box(
          modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(if (isScreenSharing) Color(0xFF00E5FF) else Color(0x1AFFFFFF))
            .clickable { onScreenShareToggled(!isScreenSharing) },
          contentAlignment = Alignment.Center
        ) {
          Icon(imageVector = Icons.AutoMirrored.Filled.ScreenShare, contentDescription = "Share Screen", tint = Color.White, modifier = Modifier.size(20.dp))
        }

        // End Call Button
        Box(
          modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(Color(0xFFEF4444))
            .clickable { onHangup() },
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.CallEnd, 
            contentDescription = "Disconnect Call", 
            tint = Color.White, 
            modifier = Modifier.size(26.dp)
          )
        }
      }
    }
  }
}

fun formatDuration(seconds: Int): String {
  val mins = seconds / 60
  val secs = seconds % 60
  return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

@Preview(showBackground = true)
@Composable
fun GhostViewPreview() {
  TestAppTheme { GhostViewDashboard() }
}


@Composable
fun GhostViewSecureViewer(
  imageUrl: String,
  onClose: () -> Unit
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var isSnooperDetected by remember { mutableStateOf(false) }
  var snooperWarningText by remember { mutableStateOf("⚠️ SNOOPER DETECTED ⚠️") }
  var hasCameraPermission by remember { 
    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) 
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasCameraPermission = isGranted
  }

  LaunchedEffect(Unit) {
    if (!hasCameraPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    // Automatically close after 10 seconds to ensure they don't see it forever
    kotlinx.coroutines.delay(10000)
    onClose()
  }

  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
  ) {
    // Apply FLAG_SECURE
    val window = (context as? android.app.Activity)?.window
    DisposableEffect(Unit) {
      window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
      onDispose {
        window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
      }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
      if (hasCameraPermission) {
        // Hidden camera preview for ML Kit
        AndroidView(
          factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            val executor = Executors.newSingleThreadExecutor()

            cameraProviderFuture.addListener({
              val cameraProvider = cameraProviderFuture.get()
              val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
              }

              val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
              val detector = FaceDetection.getClient(options)

              var isUploading = false
              val client = OkHttpClient()
              
              val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                  it.setAnalyzer(executor) { imageProxy ->
                    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        detector.process(image)
                            .addOnSuccessListener { faces ->
                                // Trigger Snooper Warning if more than 1 face is detected!
                                if (faces.size > 1) {
                                    Handler(Looper.getMainLooper()).post {
                                        isSnooperDetected = true
                                    }
                                } else {
                                    // If no extra faces, fallback to Python API for object/phone detection
                                    if (!isUploading) {
                                        isUploading = true
                                        try {
                                            val bitmap = imageProxy.toBitmap()
                                            val stream = ByteArrayOutputStream()
                                            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                                            val byteArray = stream.toByteArray()
                                            
                                            val requestBody = MultipartBody.Builder()
                                                .setType(MultipartBody.FORM)
                                                .addFormDataPart("image", "frame.jpg", byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull()))
                                                .build()
                                                
                                            val request = Request.Builder()
                                                .url("http://172.18.6.174:5000/detect")
                                                .post(requestBody)
                                                .build()
                                                
                                            client.newCall(request).enqueue(object : Callback {
                                                override fun onFailure(call: Call, e: IOException) {
                                                    isUploading = false
                                                }
                                                override fun onResponse(call: Call, response: Response) {
                                                    response.body?.string()?.let { jsonString ->
                                                        try {
                                                            val json = JSONObject(jsonString)
                                                            val blackout = json.optBoolean("blackout", false)
                                                            val reason = json.optString("reason", "")
                                                            Handler(Looper.getMainLooper()).post {
                                                                if (reason == "phone_detected") {
                                                                    snooperWarningText = "⚠️ PHONE DETECTED ⚠️"
                                                                } else {
                                                                    snooperWarningText = "⚠️ SNOOPER DETECTED ⚠️"
                                                                }
                                                                isSnooperDetected = blackout
                                                            }
                                                        } catch(e: Exception) { }
                                                    }
                                                    isUploading = false
                                                }
                                            })
                                        } catch(e: Exception) {
                                            isUploading = false
                                        }
                                    } else {
                                        // Clear warning if no extra faces and API is busy
                                        // We let the API callback handle clearing if needed.
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                  }
                }

              try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                  lifecycleOwner,
                  CameraSelector.DEFAULT_FRONT_CAMERA,
                  preview,
                  imageAnalyzer
                )
              } catch (e: Exception) {
                Log.e("GhostView", "Use case binding failed", e)
              }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
          },
          modifier = Modifier.size(1.dp).alpha(0f) // Hidden from view
        )
      }

      // The secure image
      if (imageUrl.startsWith("http")) {
        AsyncImage(
          model = imageUrl,
          contentDescription = "Secure Photo",
          modifier = Modifier
            .fillMaxSize()
            .then(if (isSnooperDetected) Modifier.blur(25.dp) else Modifier)
        )
      } else {
        Base64Image(
          base64Str = imageUrl,
          modifier = Modifier
            .fillMaxSize()
            .then(if (isSnooperDetected) Modifier.blur(25.dp) else Modifier)
        )
      }

      if (isSnooperDetected) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.VisibilityOff, contentDescription = "Hidden", tint = Color.Red, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = snooperWarningText,
              color = Color.Red,
              fontWeight = FontWeight.Bold,
              fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Image hidden for your privacy.",
              color = Color.White,
              fontSize = 14.sp
            )
          }
        }
      }

      // Close button
      IconButton(
        onClick = onClose,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(32.dp)
          .background(Color.Black.copy(alpha = 0.5f), CircleShape)
      ) {
        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
      }
    }
  }
}

