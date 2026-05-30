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
private val ColorSoftGray = Color(0xFFCFCFCF)
private val ColorBorderGray = Color(0x26FFFFFF) // rgba(255,255,255,0.15)
private val ColorTextFieldContainer = Color(0x0FFFFFFF) // 6% Opacity White

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
                    email = email,
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
    // 1. Simplified background to reduce emulator load
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(ColorPureBlack, ColorCharcoal)
          )
        )
    )

    // 2. Main interactive viewport scrollable content
    Box(
      modifier = Modifier
        .fillMaxSize()
        .imePadding()
        .verticalScroll(rememberScrollState()),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
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
      width = 1.2.dp,
      brush = Brush.verticalGradient(
        colors = listOf(
          ColorWhite.copy(alpha = 0.22f), // Soft white highlight top edge
          ColorWhite.copy(alpha = 0.05f)  // Subtle blend bottom edge
        )
      )
    ),
    colors = CardDefaults.cardColors(
      containerColor = ColorCharcoal.copy(alpha = 0.60f) // Frosted 18% equivalent dark glass base
    ),
    modifier = modifier
      .widthIn(max = 380.dp)
      .fillMaxWidth()
      .padding(vertical = 16.dp)
      .shadow(
        elevation = 36.dp,
        shape = RoundedCornerShape(32.dp),
        clip = false,
        spotColor = ColorPureBlack.copy(alpha = 0.85f),
        ambientColor = ColorWhite.copy(alpha = 0.08f)
      )
      .drawBehind {
        // Inner highlight glow on top edges
        val innerGlow = Brush.radialGradient(
          colors = listOf(ColorWhite.copy(alpha = 0.06f), Color.Transparent),
          center = Offset(size.width / 2f, 0f),
          radius = size.width
        )
        drawRect(brush = innerGlow)
      }
      .drawWithContent {
        drawContent()
        
        // Premium glass reflection shimmer line
        val shineBrush = Brush.linearGradient(
          colors = listOf(
            Color.Transparent,
            ColorWhite.copy(alpha = 0.01f),
            ColorWhite.copy(alpha = 0.12f), // reflection line peak
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
      // 1. App logo at the top
      Box(
        modifier = Modifier
          .size(60.dp)
          .clip(CircleShape)
          .background(
            brush = Brush.radialGradient(
              colors = listOf(
                ColorWhite.copy(alpha = 0.20f),
                ColorWhite.copy(alpha = 0.03f)
              )
            )
          )
          .border(1.2.dp, ColorWhite.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.VisibilityOff,
          contentDescription = "GhostView App Logo",
          tint = ColorWhite,
          modifier = Modifier.size(32.dp)
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
                        width = 1.5.dp,
                        color = if (rememberMe) ColorWhite else ColorBorderGray,
                        shape = RoundedCornerShape(4.dp)
                      )
                      .background(
                        color = if (rememberMe) ColorWhite else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                      ),
                    contentAlignment = Alignment.Center
                  ) {
                    if (rememberMe) {
                      Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = ColorPureBlack,
                        modifier = Modifier.size(12.dp)
                      )
                    }
                  }
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Remember me",
                    color = ColorSoftGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                  )
                }

                Text(
                  text = "Forget your password?",
                  color = ColorWhite,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
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

      // 1. Primary Solid Action Button
      Button(
        onClick = onSubmit,
        interactionSource = primaryInteractionSource,
        colors = ButtonDefaults.buttonColors(
          containerColor = ColorWhite,
          contentColor = ColorPureBlack
        ),
        shape = RoundedCornerShape(25.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
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
    placeholder = { Text("example@gmail.com", color = ColorSoftGray.copy(alpha = 0.5f)) },
    leadingIcon = {
      Icon(
        imageVector = Icons.Default.Email,
        contentDescription = null,
        tint = ColorSoftGray,
        modifier = Modifier.size(20.dp)
      )
    },
    singleLine = true,
    shape = RoundedCornerShape(16.dp),
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
      focusedBorderColor = ColorWhite.copy(alpha = borderAlpha),
      unfocusedBorderColor = ColorWhite.copy(alpha = 0.1f)
    ),
    modifier = modifier
      .fillMaxWidth()
      .onFocusChanged { isFocused = it.isFocused }
      .shadow(
        elevation = if (isFocused) 8.dp else 0.dp,
        shape = RoundedCornerShape(16.dp),
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
    placeholder = { Text("••••••••••••", color = ColorSoftGray.copy(alpha = 0.5f)) },
    leadingIcon = {
      Icon(
        imageVector = Icons.Default.Lock,
        contentDescription = null,
        tint = ColorSoftGray,
        modifier = Modifier.size(20.dp)
      )
    },
    trailingIcon = {
      IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
        Icon(
          imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
          contentDescription = "Toggle password visibility",
          tint = ColorSoftGray,
          modifier = Modifier.size(20.dp)
        )
      }
    },
    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
    singleLine = true,
    shape = RoundedCornerShape(16.dp),
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
      focusedBorderColor = ColorWhite.copy(alpha = borderAlpha),
      unfocusedBorderColor = ColorWhite.copy(alpha = 0.1f)
    ),
    modifier = modifier
      .fillMaxWidth()
      .onFocusChanged { isFocused = it.isFocused }
      .shadow(
        elevation = if (isFocused) 8.dp else 0.dp,
        shape = RoundedCornerShape(16.dp),
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
    placeholder = { Text(label, color = ColorSoftGray.copy(alpha = 0.5f)) },
    leadingIcon = {
      Icon(
        imageVector = leadingIcon,
        contentDescription = null,
        tint = ColorSoftGray,
        modifier = Modifier.size(20.dp)
      )
    },
    singleLine = true,
    shape = RoundedCornerShape(16.dp),
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
      focusedBorderColor = ColorWhite.copy(alpha = borderAlpha),
      unfocusedBorderColor = ColorWhite.copy(alpha = 0.1f)
    ),
    modifier = modifier
      .fillMaxWidth()
      .onFocusChanged { isFocused = it.isFocused }
      .shadow(
        elevation = if (isFocused) 8.dp else 0.dp,
        shape = RoundedCornerShape(16.dp),
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
    shape = RoundedCornerShape(25.dp),
    border = BorderStroke(1.2.dp, ColorWhite.copy(alpha = 0.15f)),
    colors = ButtonDefaults.outlinedButtonColors(
      contentColor = ColorWhite,
      containerColor = ColorWhite.copy(alpha = 0.05f) // Glass transparent container
    ),
    modifier = modifier
      .height(50.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
  ) {
    Text(
      text = text,
      color = ColorWhite,
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium
    )
  }
}

@Composable
fun DoodleBackground(modifier: Modifier = Modifier) {
  val infiniteTransition = rememberInfiniteTransition(label = "floating_doodles")
  
  // Create slow waving coordinates for background doodles (3-8% opacity outlines)
  val floatState1 by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(18000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "float_state_1"
  )

  val floatState2 by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(24000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "float_state_2"
  )

  Canvas(
    modifier = modifier
      .background(
        Brush.verticalGradient(
          colors = listOf(ColorPureBlack, ColorCharcoal)
        )
      )
  ) {
    // 5% Opacity monochrome white strokes
    val color = ColorWhite.copy(alpha = 0.045f)
    
    // Wave translations
    val cos1 = cos(Math.toRadians(floatState1.toDouble())).toFloat()
    val sin1 = sin(Math.toRadians(floatState1.toDouble())).toFloat()
    val cos2 = cos(Math.toRadians(floatState2.toDouble())).toFloat()
    val sin2 = sin(Math.toRadians(floatState2.toDouble())).toFloat()

    // Layer 1 displacements
    val dX1 = cos1 * 12.dp.toPx()
    val dY1 = sin1 * 12.dp.toPx()

    // Layer 2 displacements
    val dX2 = sin2 * 10.dp.toPx()
    val dY2 = cos2 * 14.dp.toPx()

    // Top Left sector
    drawChatBubble(Offset(50.dp.toPx() + dX1, 90.dp.toPx() + dY1), Size(48.dp.toPx(), 36.dp.toPx()), color)
    drawStar(Offset(120.dp.toPx() + dX1, 60.dp.toPx() + dY1), 12.dp.toPx(), color)
    drawGhost(Offset(80.dp.toPx() + dX2, 200.dp.toPx() + dY2), 40.dp.toPx(), 48.dp.toPx(), color)
    drawWiggle(Offset(30.dp.toPx() + dX1, 300.dp.toPx() + dY1), Offset(90.dp.toPx() + dX1, 340.dp.toPx() + dY1), color)

    // Top Right sector
    drawStar(Offset(size.width - 60.dp.toPx() + dX2, 80.dp.toPx() + dY2), 16.dp.toPx(), color)
    drawChatBubble(Offset(size.width - 110.dp.toPx() + dX1, 160.dp.toPx() + dY1), Size(40.dp.toPx(), 30.dp.toPx()), color)
    drawGhost(Offset(size.width - 70.dp.toPx() + dX1, 270.dp.toPx() + dY1), 35.dp.toPx(), 42.dp.toPx(), color)
    drawWiggle(Offset(size.width - 130.dp.toPx() + dX2, 350.dp.toPx() + dY2), Offset(size.width - 50.dp.toPx() + dX2, 370.dp.toPx() + dY2), color)

    // Mid Left/Right decorative items
    drawArrow(Offset(35.dp.toPx() + dX1, 440.dp.toPx() + dY1), Offset(75.dp.toPx() + dX1, 410.dp.toPx() + dY1), color)
    drawStar(Offset(size.width - 50.dp.toPx() + dX2, 460.dp.toPx() + dY2), 8.dp.toPx(), color)
    drawChatBubble(Offset(30.dp.toPx() + dX2, 540.dp.toPx() + dY2), Size(32.dp.toPx(), 26.dp.toPx()), color)

    // Bottom Left sector
    drawGhost(Offset(60.dp.toPx() + dX1, size.height - 180.dp.toPx() + dY1), 44.dp.toPx(), 52.dp.toPx(), color)
    drawStar(Offset(140.dp.toPx() + dX2, size.height - 110.dp.toPx() + dY2), 14.dp.toPx(), color)
    drawWiggle(Offset(40.dp.toPx() + dX1, size.height - 80.dp.toPx() + dY1), Offset(90.dp.toPx() + dX1, size.height - 40.dp.toPx() + dY1), color)

    // Bottom Right sector
    drawChatBubble(Offset(size.width - 70.dp.toPx() + dX2, size.height - 180.dp.toPx() + dY2), Size(44.dp.toPx(), 34.dp.toPx()), color)
    drawArrow(Offset(size.width - 120.dp.toPx() + dX1, size.height - 110.dp.toPx() + dY1), Offset(size.width - 70.dp.toPx() + dX1, size.height - 130.dp.toPx() + dY1), color)
    drawGhost(Offset(size.width - 90.dp.toPx() + dX2, size.height - 70.dp.toPx() + dY2), 32.dp.toPx(), 38.dp.toPx(), color)
    drawStar(Offset(size.width - 160.dp.toPx() + dX1, size.height - 40.dp.toPx() + dY1), 10.dp.toPx(), color)
  }
}

// Doodle Drawing Canvas Helpers

private fun DrawScope.drawChatBubble(center: Offset, size: Size, color: Color) {
  val path = Path().apply {
    addRoundRect(
      RoundRect(
        rect = Rect(
          center.x - size.width / 2,
          center.y - size.height / 2,
          center.x + size.width / 2,
          center.y + size.height / 2
        ),
        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
      )
    )
    moveTo(center.x - size.width / 4, center.y + size.height / 2)
    lineTo(center.x - size.width / 3, center.y + size.height / 2 + 6.dp.toPx())
    lineTo(center.x - size.width / 6, center.y + size.height / 2)
  }
  drawPath(
    path = path,
    color = color,
    style = Stroke(width = 1.5.dp.toPx())
  )
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
  val path = Path()
  val points = 5
  val doublePI = Math.PI * 2
  val angleStep = doublePI / (points * 2)
  
  for (i in 0 until (points * 2)) {
    val currentRadius = if (i % 2 == 0) radius else radius * 0.4f
    val currentAngle = i * angleStep - Math.PI / 2.0
    val x = center.x + currentRadius * cos(currentAngle).toFloat()
    val y = center.y + currentRadius * sin(currentAngle).toFloat()
    
    if (i == 0) {
      path.moveTo(x, y)
    } else {
      path.lineTo(x, y)
    }
  }
  path.close()
  drawPath(
    path = path,
    color = color,
    style = Stroke(width = 1.5.dp.toPx())
  )
}

private fun DrawScope.drawGhost(center: Offset, width: Float, height: Float, color: Color) {
  val path = Path().apply {
    val left = center.x - width / 2
    val right = center.x + width / 2
    val top = center.y - height / 2
    val bottom = center.y + height / 2
    
    arcTo(
      rect = Rect(left, top, right, top + height * 0.8f),
      startAngleDegrees = 180f,
      sweepAngleDegrees = 180f,
      forceMoveTo = true
    )
    lineTo(right, bottom - height * 0.15f)
    
    val segment = width / 3f
    quadraticTo(right - segment / 2f, bottom, right - segment, bottom - height * 0.15f)
    quadraticTo(right - 1.5f * segment, bottom - height * 0.3f, right - 2f * segment, bottom - height * 0.15f)
    quadraticTo(left + segment / 2f, bottom, left, bottom - height * 0.15f)
    
    close()
  }
  
  drawPath(
    path = path,
    color = color,
    style = Stroke(width = 1.5.dp.toPx())
  )
  
  drawCircle(
    color = color,
    radius = 2.dp.toPx(),
    center = Offset(center.x - width * 0.18f, center.y - height * 0.05f)
  )
  drawCircle(
    color = color,
    radius = 2.dp.toPx(),
    center = Offset(center.x + width * 0.18f, center.y - height * 0.05f)
  )
}

private fun DrawScope.drawArrow(start: Offset, end: Offset, color: Color) {
  drawLine(
    color = color,
    start = start,
    end = end,
    strokeWidth = 1.5.dp.toPx()
  )
  val dx = end.x - start.x
  val dy = end.y - start.y
  val length = kotlin.math.sqrt(dx * dx + dy * dy)
  if (length > 0f) {
    val ux = dx / length
    val uy = dy / length
    val headLen = 8.dp.toPx()
    
    val lx = end.x - headLen * ux + headLen * 0.5f * uy
    val ly = end.y - headLen * uy - headLen * 0.5f * ux
    val rx = end.x - headLen * ux - headLen * 0.5f * uy
    val ry = end.y - headLen * uy + headLen * 0.5f * ux
    
    drawLine(color = color, start = end, end = Offset(lx, ly), strokeWidth = 1.5.dp.toPx())
    drawLine(color = color, start = end, end = Offset(rx, ry), strokeWidth = 1.5.dp.toPx())
  }
}

private fun DrawScope.drawWiggle(start: Offset, end: Offset, color: Color) {
  val path = Path().apply {
    moveTo(start.x, start.y)
    val cx1 = (start.x + end.x) / 2f
    val cy1 = start.y - 12.dp.toPx()
    val cx2 = (start.x + end.x) / 2f
    val cy2 = end.y + 12.dp.toPx()
    cubicTo(cx1, cy1, cx2, cy2, end.x, end.y)
  }
  drawPath(
    path = path,
    color = color,
    style = Stroke(width = 1.5.dp.toPx())
  )
}

@Preview(showBackground = true)
@Composable
fun PreviewLoginScreen() {
  DoodleBackground(modifier = Modifier.fillMaxSize())
}
