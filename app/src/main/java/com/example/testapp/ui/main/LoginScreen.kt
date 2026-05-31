package com.example.testapp.ui.main

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testapp.data.FirestoreChatService
import com.example.testapp.data.WhatsAppUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import kotlin.math.cos
import kotlin.math.sin

// Strict Black & White Theme Colors
private val ColorPureBlack = Color(0xFF000000)
private val ColorCharcoal = Color(0xFF111111)
private val ColorWhite = Color(0xFFFFFFFF)
private val ColorSoftGray = Color(0xFF8E9AA4)
private val ColorBorderGray = Color(0x26FFFFFF) // rgba(255,255,255,0.15)
private val ColorTextFieldContainer = Color(0xFF13171F)

@Composable
fun LoginScreen(
  auth: FirebaseAuth,
  firestoreService: FirestoreChatService?,
  onJoined: (String, String) -> Unit,
  modifier: Modifier = Modifier
) {
  var isLoginMode by remember { mutableStateOf(true) }
  var otpSent by remember { mutableStateOf(false) }
  var loadingState by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf("") }

  // State variables for form inputs
  var emailInput by remember { mutableStateOf("") }
  var passwordInput by remember { mutableStateOf("") }
  var handleName by remember { mutableStateOf("") }
  var phoneNumber by remember { mutableStateOf("") }
  var otpCode by remember { mutableStateOf("") }
  var rememberMe by remember { mutableStateOf(true) }
  var pendingSignupUser by remember { mutableStateOf<WhatsAppUser?>(null) }

  val context = LocalContext.current
  val focusManager = LocalFocusManager.current

  // Form Submission Logic
  val onSubmit: () -> Unit = {
    focusManager.clearFocus()
    if (otpSent) {
      // OTP Verification Mode
      if (otpCode.length != 6) {
        errorMessage = "OTP must be exactly 6 digits."
      } else {
        loadingState = true
        errorMessage = ""
        // Mock verification code
        if (otpCode == "123456") {
          val userObj = pendingSignupUser
          if (userObj != null) {
            firestoreService?.registerUser(userObj)
            loadingState = false
            onJoined(userObj.username, userObj.phone)
            Toast.makeText(context, "Verified! Welcome, ${userObj.username}!", Toast.LENGTH_SHORT).show()
          } else {
            loadingState = false
            errorMessage = "Signup session expired. Please retry."
          }
        } else {
          loadingState = false
          errorMessage = "Invalid OTP. Use 123456 for testing."
        }
      }
    } else {
      // Standard Log In / Sign Up Mode
      if (emailInput.isEmpty() || passwordInput.isEmpty()) {
        errorMessage = "Email and password are required."
      } else {
        val email = emailInput.trim()
        val password = passwordInput.trim()
        loadingState = true
        errorMessage = ""

        if (isLoginMode) {
          auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
              val user = result.user
              val name = user?.displayName ?: email.substringBefore("@")
              
              firestoreService?.usersCollection?.document(name.lowercase())?.get()
                ?.addOnSuccessListener { doc ->
                  val phone = doc.getString("phone") ?: ""
                  if (!doc.exists() || (doc.getString("email") ?: "").isEmpty()) {
                    firestoreService.registerUser(WhatsAppUser(
                      username = name,
                      email = email.lowercase(),
                      phone = phone,
                      bio = "Hey there! I am using GhostView.",
                      statusText = "Available",
                      avatarColor = 0xFFFFFFFF.toInt(), // Pure White theme default avatar
                      lastSeen = System.currentTimeMillis()
                    ))
                  }
                  loadingState = false
                  onJoined(name, phone)
                  Toast.makeText(context, "Welcome back, $name!", Toast.LENGTH_SHORT).show()
                }
                ?.addOnFailureListener {
                  firestoreService.registerUser(WhatsAppUser(
                    username = name,
                    email = email.lowercase(),
                    phone = "",
                    bio = "Hey there! I am using GhostView.",
                    statusText = "Available",
                    avatarColor = 0xFFFFFFFF.toInt(),
                    lastSeen = System.currentTimeMillis()
                  ))
                  loadingState = false
                  onJoined(name, "")
                  Toast.makeText(context, "Welcome back, $name!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
              loadingState = false
              errorMessage = exception.localizedMessage ?: "Log In failed."
            }
        } else {
          // Sign Up Mode
          val name = handleName.trim()
          val num = phoneNumber.trim()

          if (name.isEmpty()) {
            errorMessage = "Display Username is required."
            loadingState = false
          } else if (num.length < 8) {
            errorMessage = "Valid mobile number is required."
            loadingState = false
          } else {
            auth.createUserWithEmailAndPassword(email, password)
              .addOnSuccessListener { result ->
                val user = result.user
                user?.updateProfile(userProfileChangeRequest {
                  displayName = name
                })?.addOnCompleteListener {
                  loadingState = false
                  otpSent = true
                  pendingSignupUser = WhatsAppUser(
                    username = name,
                    email = email.lowercase(),
                    phone = num,
                    bio = "Hey there! I am using GhostView.",
                    statusText = "Available",
                    avatarColor = 0xFFFFFFFF.toInt(),
                    lastSeen = System.currentTimeMillis()
                  )
                  Toast.makeText(context, "OTP sent to $num", Toast.LENGTH_SHORT).show()
                }
              }
              .addOnFailureListener { exception ->
                loadingState = false
                errorMessage = exception.localizedMessage ?: "Sign Up failed."
              }
          }
        }
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(ColorPureBlack)
  ) {
    // 1. Premium 3D spheres background
    DoodleBackground(modifier = Modifier.fillMaxSize())

    // 2. Main interactive viewport scrollable content
    Box(
      modifier = Modifier
        .fillMaxSize()
        .imePadding()
        .verticalScroll(rememberScrollState()),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 40.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
      ) {
        // Top-left Brand Logo and Title (outside the card)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Start
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(Color(0xFF0F141C))
              .border(1.2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
          ) {
            Box(
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.ChatBubble,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
              )
              // 3 tiny dots inside the bubble
              Row(
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                repeat(3) {
                  Box(
                    modifier = Modifier
                      .size(2.dp)
                      .clip(CircleShape)
                      .background(Color.Black)
                  )
                }
              }
            }
          }
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "GhostView",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
          )
        }

        LoginCard(
          isLoginMode = isLoginMode,
          otpSent = otpSent,
          emailInput = emailInput,
          passwordInput = passwordInput,
          handleName = handleName,
          phoneNumber = phoneNumber,
          otpCode = otpCode,
          rememberMe = rememberMe,
          errorMessage = errorMessage,
          loadingState = loadingState,
          onEmailChange = { emailInput = it },
          onPasswordChange = { passwordInput = it },
          onHandleNameChange = { handleName = it },
          onPhoneNumberChange = { phoneNumber = it },
          onOtpCodeChange = { otpCode = it },
          onRememberMeChange = { rememberMe = it },
          onForgotPasswordClick = {
            if (emailInput.isEmpty()) {
              errorMessage = "Please enter your email to reset password."
            } else {
              loadingState = true
              auth.sendPasswordResetEmail(emailInput.trim())
                .addOnSuccessListener {
                  loadingState = false
                  Toast.makeText(context, "Reset email sent successfully!", Toast.LENGTH_LONG).show()
                }
                .addOnFailureListener {
                  loadingState = false
                  errorMessage = it.localizedMessage ?: "Error sending reset email."
                }
            }
          },
          onSubmit = onSubmit,
          onToggleMode = {
            isLoginMode = !isLoginMode
            errorMessage = ""
          },
          onBackToSignUp = {
            otpSent = false
            errorMessage = ""
          }
        )
      }
    }
  }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LoginCard(
  isLoginMode: Boolean,
  otpSent: Boolean,
  emailInput: String,
  passwordInput: String,
  handleName: String,
  phoneNumber: String,
  otpCode: String,
  rememberMe: Boolean,
  errorMessage: String,
  loadingState: Boolean,
  onEmailChange: (String) -> Unit,
  onPasswordChange: (String) -> Unit,
  onHandleNameChange: (String) -> Unit,
  onPhoneNumberChange: (String) -> Unit,
  onOtpCodeChange: (String) -> Unit,
  onRememberMeChange: (Boolean) -> Unit,
  onForgotPasswordClick: () -> Unit,
  onSubmit: () -> Unit,
  onToggleMode: () -> Unit,
  onBackToSignUp: () -> Unit,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "glass_effects")
  
  // Shimmer offset path
  val shimmerOffset by infiniteTransition.animateFloat(
    initialValue = -600f,
    targetValue = 1800f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 4000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "shimmer_shine"
  )

  // Primary Button Press Animation state
  val primaryInteractionSource = remember { MutableInteractionSource() }
  val primaryPressed by primaryInteractionSource.collectIsPressedAsState()
  val primaryScale by animateFloatAsState(
    targetValue = if (primaryPressed) 0.96f else 1.0f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
    label = "primary_scale"
  )

  // Premium Frosted Glassmorphism Card
  Card(
    shape = RoundedCornerShape(32.dp),
    border = BorderStroke(
      width = 1.dp,
      color = ColorWhite.copy(alpha = 0.15f)
    ),
    colors = CardDefaults.cardColors(
      containerColor = ColorCharcoal.copy(alpha = 0.65f) // Frosted dark glass base
    ),
    modifier = modifier
      .widthIn(max = 380.dp)
      .fillMaxWidth()
      .padding(vertical = 8.dp)
      .shadow(
        elevation = 36.dp,
        shape = RoundedCornerShape(32.dp),
        clip = false,
        spotColor = ColorPureBlack.copy(alpha = 0.85f),
        ambientColor = ColorWhite.copy(alpha = 0.08f)
      )
      .drawWithContent {
        drawContent()
        
        // Premium glass reflection shimmer line
        val shineBrush = Brush.linearGradient(
          colors = listOf(
            Color.Transparent,
            ColorWhite.copy(alpha = 0.01f),
            ColorWhite.copy(alpha = 0.10f), // reflection line peak
            ColorWhite.copy(alpha = 0.01f),
            Color.Transparent
          ),
          start = Offset(shimmerOffset, 0f),
          end = Offset(shimmerOffset + 140.dp.toPx(), size.height)
        )
        drawRect(brush = shineBrush)
      }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      // 1. Slashed-eye circular badge (Inside the card)
      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(ColorWhite.copy(alpha = 0.05f))
          .border(1.dp, ColorWhite.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.VisibilityOff,
          contentDescription = "Privacy Shield",
          tint = ColorWhite,
          modifier = Modifier.size(28.dp)
        )
      }

      // Smooth state change transitions
      AnimatedContent(
        targetState = if (otpSent) "otp" else if (isLoginMode) "login" else "signup",
        transitionSpec = {
          fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(250))
        },
        label = "login_form_transition"
      ) { state ->
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          when (state) {
            "otp" -> {
              Text(
                text = "Verify Phone",
                color = ColorWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
              )
              Text(
                text = "Enter the 6-digit OTP code sent to $phoneNumber\n(Use 123456 to test)",
                color = ColorSoftGray,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
              )

              Spacer(modifier = Modifier.height(8.dp))

              CustomTextField(
                value = otpCode,
                onValueChange = onOtpCodeChange,
                label = "OTP Code",
                leadingIcon = Icons.Default.Key,
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
                onImeAction = onSubmit
              )
            }
            "login" -> {
              // Welcome Text Headers
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = "Welcome Back",
                  color = ColorWhite,
                  fontSize = 24.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 0.5.sp
                )
                Text(
                  text = "Sign in to continue your conversations",
                  color = ColorSoftGray,
                  fontSize = 13.sp,
                  textAlign = TextAlign.Center
                )
              }

              Spacer(modifier = Modifier.height(8.dp))

              EmailField(
                value = emailInput,
                onValueChange = onEmailChange
              )

              PasswordField(
                value = passwordInput,
                onValueChange = onPasswordChange,
                imeAction = ImeAction.Done,
                onImeAction = onSubmit
              )

              // Remember Me & Forgot Password Row
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onRememberMeChange(!rememberMe) }
                  )
                ) {
                  Box(
                    modifier = Modifier
                      .size(18.dp)
                      .border(
                        width = 1.dp,
                        color = if (rememberMe) ColorWhite else ColorWhite.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(5.dp)
                      )
                      .background(
                        color = if (rememberMe) Color.Transparent else Color.Transparent,
                        shape = RoundedCornerShape(5.dp)
                      ),
                    contentAlignment = Alignment.Center
                  ) {
                    if (rememberMe) {
                      Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = ColorWhite,
                        modifier = Modifier.size(12.dp)
                      )
                    }
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Remember me",
                    color = ColorWhite.copy(alpha = 0.8f),
                    fontSize = 12.sp
                  )
                }

                Text(
                  text = "Forgot your password?",
                  color = ColorWhite.copy(alpha = 0.6f),
                  fontSize = 12.sp,
                  modifier = Modifier.clickable(onClick = onForgotPasswordClick)
                )
              }
            }
            "signup" -> {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = "Register Account",
                  color = ColorWhite,
                  fontSize = 24.sp,
                  fontWeight = FontWeight.Bold,
                  letterSpacing = 0.5.sp
                )
                Text(
                  text = "Join GhostView to begin encrypted messaging",
                  color = ColorSoftGray,
                  fontSize = 13.sp,
                  textAlign = TextAlign.Center
                )
              }

              Spacer(modifier = Modifier.height(8.dp))

              CustomTextField(
                value = handleName,
                onValueChange = onHandleNameChange,
                label = "Display Username",
                leadingIcon = Icons.Default.Person,
                keyboardType = KeyboardType.Text
              )

              EmailField(
                value = emailInput,
                onValueChange = onEmailChange
              )

              CustomTextField(
                value = phoneNumber,
                onValueChange = onPhoneNumberChange,
                label = "Mobile (e.g. +91XXXXXXXXXX)",
                leadingIcon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone
              )

              PasswordField(
                value = passwordInput,
                onValueChange = onPasswordChange,
                imeAction = ImeAction.Done,
                onImeAction = onSubmit
              )
            }
          }
        }
      }

      // Display validation errors dynamically
      if (errorMessage.isNotEmpty()) {
        Text(
          text = errorMessage,
          color = Color(0xFFEF4444),
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )
      }

      // 1. Primary Capsule Silver/White Gradient Action Button
      Button(
        onClick = onSubmit,
        interactionSource = primaryInteractionSource,
        colors = ButtonDefaults.buttonColors(
          containerColor = Color.Transparent,
          contentColor = ColorPureBlack
        ),
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(
                Color(0xFFECEFF1), // Silver highlight top
                Color(0xFFB0BEC5)  // Muted steel bottom
              )
            ),
            shape = RoundedCornerShape(26.dp)
          )
          .graphicsLayer {
            scaleX = primaryScale
            scaleY = primaryScale
          }
      ) {
        if (loadingState) {
          CircularProgressIndicator(
            color = ColorPureBlack,
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.5.dp
          )
        } else {
          Text(
            text = if (otpSent) "Verify OTP" else if (isLoginMode) "Sign In" else "Sign Up",
            fontWeight = FontWeight.Bold,
            color = ColorPureBlack,
            fontSize = 15.sp
          )
        }
      }

      // 2. OR Divider (not shown in OTP screen)
      if (!otpSent) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .height(1.dp)
              .background(ColorBorderGray)
          )
          Text(
            text = "OR",
            color = ColorSoftGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
          )
          Box(
            modifier = Modifier
              .weight(1f)
              .height(1.dp)
              .background(ColorBorderGray)
          )
        }

        // 3. Secondary Glassmorphic Button
        SecondaryGlassButton(
          text = if (isLoginMode) "Create Account" else "Log In",
          onClick = onToggleMode,
          modifier = Modifier.fillMaxWidth()
        )
      }

      // 4. Center Navigate Toggle Bottom Link
      if (otpSent) {
        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Back to Sign Up",
            color = ColorWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.clickable(onClick = onBackToSignUp)
          )
        }
      }
    }
  }
}

@Composable
fun EmailField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var isFocused by remember { mutableStateOf(false) }
  val borderAlpha by animateFloatAsState(
    targetValue = if (isFocused) 0.50f else 0.12f,
    animationSpec = tween(200),
    label = "border_alpha"
  )
  val glowAlpha by animateFloatAsState(
    targetValue = if (isFocused) 0.20f else 0.0f,
    animationSpec = tween(200),
    label = "glow_alpha"
  )

  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    placeholder = { Text("example@gmail.com", color = ColorWhite.copy(alpha = 0.35f)) },
    leadingIcon = {
      Icon(
        imageVector = Icons.Default.Email,
        contentDescription = null,
        tint = ColorWhite.copy(alpha = 0.7f),
        modifier = Modifier.size(20.dp)
      )
    },
    singleLine = true,
    shape = RoundedCornerShape(26.dp),
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.Email,
      imeAction = ImeAction.Next
    ),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = ColorWhite,
      unfocusedTextColor = ColorWhite,
      focusedContainerColor = ColorTextFieldContainer,
      unfocusedContainerColor = ColorTextFieldContainer,
      cursorColor = ColorWhite,
      focusedBorderColor = ColorWhite.copy(alpha = 0.35f),
      unfocusedBorderColor = ColorWhite.copy(alpha = 0.12f)
    ),
    modifier = modifier
      .fillMaxWidth()
      .onFocusChanged { isFocused = it.isFocused }
      .shadow(
        elevation = if (isFocused) 6.dp else 0.dp,
        shape = RoundedCornerShape(26.dp),
        clip = false,
        spotColor = ColorWhite.copy(alpha = glowAlpha)
      )
  )
}

@Composable
fun PasswordField(
  value: String,
  onValueChange: (String) -> Unit,
  imeAction: ImeAction,
  onImeAction: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isPasswordVisible by remember { mutableStateOf(false) }
  var isFocused by remember { mutableStateOf(false) }
  val borderAlpha by animateFloatAsState(
    targetValue = if (isFocused) 0.50f else 0.12f,
    animationSpec = tween(200),
    label = "border_alpha"
  )
  val glowAlpha by animateFloatAsState(
    targetValue = if (isFocused) 0.20f else 0.0f,
    animationSpec = tween(200),
    label = "glow_alpha"
  )

  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    placeholder = { Text("••••••••••••", color = ColorWhite.copy(alpha = 0.35f)) },
    leadingIcon = {
      Icon(
        imageVector = Icons.Default.Lock,
        contentDescription = null,
        tint = ColorWhite.copy(alpha = 0.7f),
        modifier = Modifier.size(20.dp)
      )
    },
    trailingIcon = {
      IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
        Icon(
          imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
          contentDescription = "Toggle password visibility",
          tint = ColorWhite.copy(alpha = 0.6f),
          modifier = Modifier.size(20.dp)
        )
      }
    },
    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
    singleLine = true,
    shape = RoundedCornerShape(26.dp),
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.Password,
      imeAction = imeAction
    ),
    keyboardActions = KeyboardActions(
      onDone = { onImeAction() },
      onNext = { onImeAction() }
    ),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = ColorWhite,
      unfocusedTextColor = ColorWhite,
      focusedContainerColor = ColorTextFieldContainer,
      unfocusedContainerColor = ColorTextFieldContainer,
      cursorColor = ColorWhite,
      focusedBorderColor = ColorWhite.copy(alpha = 0.35f),
      unfocusedBorderColor = ColorWhite.copy(alpha = 0.12f)
    ),
    modifier = modifier
      .fillMaxWidth()
      .onFocusChanged { isFocused = it.isFocused }
      .shadow(
        elevation = if (isFocused) 6.dp else 0.dp,
        shape = RoundedCornerShape(26.dp),
        clip = false,
        spotColor = ColorWhite.copy(alpha = glowAlpha)
      )
  )
}

@Composable
fun CustomTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
  keyboardType: KeyboardType,
  modifier: Modifier = Modifier,
  imeAction: ImeAction = ImeAction.Next,
  onImeAction: () -> Unit = {}
) {
  var isFocused by remember { mutableStateOf(false) }
  val borderAlpha by animateFloatAsState(
    targetValue = if (isFocused) 0.50f else 0.12f,
    animationSpec = tween(200),
    label = "border_alpha"
  )
  val glowAlpha by animateFloatAsState(
    targetValue = if (isFocused) 0.20f else 0.0f,
    animationSpec = tween(200),
    label = "glow_alpha"
  )

  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    placeholder = { Text(label, color = ColorWhite.copy(alpha = 0.35f)) },
    leadingIcon = {
      Icon(
        imageVector = leadingIcon,
        contentDescription = null,
        tint = ColorWhite.copy(alpha = 0.7f),
        modifier = Modifier.size(20.dp)
      )
    },
    singleLine = true,
    shape = RoundedCornerShape(26.dp),
    keyboardOptions = KeyboardOptions(
      keyboardType = keyboardType,
      imeAction = imeAction
    ),
    keyboardActions = KeyboardActions(
      onDone = { onImeAction() }
    ),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = ColorWhite,
      unfocusedTextColor = ColorWhite,
      focusedContainerColor = ColorTextFieldContainer,
      unfocusedContainerColor = ColorTextFieldContainer,
      cursorColor = ColorWhite,
      focusedBorderColor = ColorWhite.copy(alpha = 0.35f),
      unfocusedBorderColor = ColorWhite.copy(alpha = 0.12f)
    ),
    modifier = modifier
      .fillMaxWidth()
      .onFocusChanged { isFocused = it.isFocused }
      .shadow(
        elevation = if (isFocused) 6.dp else 0.dp,
        shape = RoundedCornerShape(26.dp),
        clip = false,
        spotColor = ColorWhite.copy(alpha = glowAlpha)
      )
  )
}

@Composable
fun SecondaryGlassButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.96f else 1.0f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
    label = "secondary_scale"
  )

  OutlinedButton(
    onClick = onClick,
    interactionSource = interactionSource,
    shape = RoundedCornerShape(26.dp),
    border = BorderStroke(1.dp, ColorWhite.copy(alpha = 0.25f)),
    colors = ButtonDefaults.outlinedButtonColors(
      contentColor = ColorWhite,
      containerColor = Color.Transparent
    ),
    modifier = modifier
      .height(52.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = if (text.contains("Log In", ignoreCase = true)) Icons.Default.Login else Icons.Default.PersonAdd,
        contentDescription = null,
        tint = ColorWhite,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = text,
        color = ColorWhite,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
fun DoodleBackground(modifier: Modifier = Modifier) {
  Canvas(
    modifier = modifier
      .background(ColorPureBlack)
  ) {
    // 1. Top Right Large Sphere (Orb 1)
    val orb1Radius = 320.dp.toPx()
    val orb1Center = Offset(size.width * 1.05f, size.height * 0.1f)
    
    // Draw the base dark gradient of the sphere
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          Color(0xFF1E242B),
          Color(0xFF07090C)
        ),
        center = orb1Center,
        radius = orb1Radius
      ),
      center = orb1Center,
      radius = orb1Radius
    )
    
    // Draw a series of faint glow rings to create the premium glass reflection/crescent highlight
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          Color.White.copy(alpha = 0.08f),
          Color.Transparent
        ),
        center = Offset(orb1Center.x - orb1Radius * 0.7f, orb1Center.y + orb1Radius * 0.4f),
        radius = orb1Radius * 0.8f
      ),
      center = orb1Center,
      radius = orb1Radius
    )
    
    // Fine crescent outline highlight facing the center card
    drawCircle(
      brush = Brush.linearGradient(
        colors = listOf(
          Color.White.copy(alpha = 0.4f),
          Color.White.copy(alpha = 0.1f),
          Color.Transparent
        ),
        start = Offset(orb1Center.x - orb1Radius, orb1Center.y + orb1Radius),
        end = Offset(orb1Center.x + orb1Radius, orb1Center.y - orb1Radius)
      ),
      center = orb1Center,
      radius = orb1Radius,
      style = Stroke(width = 1.2.dp.toPx())
    )

    // 2. Bottom Left Sphere (Orb 2)
    val orb2Radius = 220.dp.toPx()
    val orb2Center = Offset(size.width * -0.1f, size.height * 0.95f)

    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          Color(0xFF1B2026),
          Color(0xFF050608)
        ),
        center = orb2Center,
        radius = orb2Radius
      ),
      center = orb2Center,
      radius = orb2Radius
    )

    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          Color.White.copy(alpha = 0.06f),
          Color.Transparent
        ),
        center = Offset(orb2Center.x + orb2Radius * 0.6f, orb2Center.y - orb2Radius * 0.6f),
        radius = orb2Radius * 0.8f
      ),
      center = orb2Center,
      radius = orb2Radius
    )

    // Fine crescent outline highlight facing the center card
    drawCircle(
      brush = Brush.linearGradient(
        colors = listOf(
          Color.White.copy(alpha = 0.3f),
          Color.White.copy(alpha = 0.05f),
          Color.Transparent
        ),
        start = Offset(orb2Center.x + orb2Radius, orb2Center.y - orb2Radius),
        end = Offset(orb2Center.x - orb2Radius, orb2Center.y + orb2Radius)
      ),
      center = orb2Center,
      radius = orb2Radius,
      style = Stroke(width = 1.0.dp.toPx())
    )
  }
}

@Preview(showBackground = true)
@Composable
fun PreviewLoginScreen() {
  DoodleBackground(modifier = Modifier.fillMaxSize())
}
