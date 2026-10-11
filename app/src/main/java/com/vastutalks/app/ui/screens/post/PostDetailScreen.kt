package com.vastutalks.app.ui.screens.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.vastutalks.app.data.model.Post
import com.vastutalks.app.data.model.PostComment
import com.vastutalks.app.data.model.UserProfile
import com.vastutalks.app.data.repository.PostRepository
import com.vastutalks.app.data.repository.UserRepository
import com.vastutalks.app.ui.components.PostCard
import com.vastutalks.app.ui.components.ProfileAvatar
import com.vastutalks.app.ui.components.timeAgo
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.InputFill
import com.vastutalks.app.ui.theme.SurfaceLight
import com.vastutalks.app.ui.theme.TextHint
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuPrimary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A post with its comments; anyone can like, comment and reply. */
class PostDetailViewModel(savedState: SavedStateHandle) : ViewModel() {
    val postId: String = checkNotNull(savedState["postId"])
    private val posts = PostRepository()
    val myUid: String? = FirebaseAuth.getInstance().currentUser?.uid

    /** null while loading; [PostState.Gone] once deleted (or missing). */
    val post: StateFlow<PostState> = posts.listenPost(postId)
        .map { if (it == null) PostState.Gone else PostState.Loaded(it) }
        .catch { emit(PostState.Gone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PostState.Loading)

    val comments: StateFlow<List<PostComment>> = posts.listenComments(postId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val liked: StateFlow<Boolean> = posts.listenMyLikes().map { postId in it }
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val me: StateFlow<UserProfile?> = UserRepository().listenMyProfile()
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var input by mutableStateOf("")
    /** The comment being answered, if the user tapped Reply. */
    var replyingTo by mutableStateOf<PostComment?>(null)
    var sending by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun toggleLike() {
        viewModelScope.launch { posts.setLiked(postId, !liked.value) }
    }

    fun send() {
        val profile = me.value
        val text = input.trim()
        if (text.isEmpty() || sending) return
        if (profile == null) {
            error = "Still loading your profile — try again in a moment."
            return
        }
        sending = true
        error = null
        viewModelScope.launch {
            posts.addComment(postId, profile, text, replyingTo).fold(
                onSuccess = {
                    input = ""
                    replyingTo = null
                },
                onFailure = { error = "Couldn't send: ${it.message}" }
            )
            sending = false
        }
    }

    fun deleteComment(comment: PostComment) {
        viewModelScope.launch { posts.deleteComment(postId, comment.id) }
    }
}

sealed interface PostState {
    data object Loading : PostState
    data object Gone : PostState
    data class Loaded(val post: Post) : PostState
}

@Composable
fun PostDetailScreen(
    onBack: () -> Unit,
    onAuthorClick: (uid: String, name: String) -> Unit,
    viewModel: PostDetailViewModel = viewModel()
) {
    val state by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val liked by viewModel.liked.collectAsState()
    val inputFocus = remember { FocusRequester() }

    // Top-level comments, each followed by its replies.
    val threads = remember(comments) {
        val replies = comments.filter { it.replyToCommentId.isNotBlank() }.groupBy { it.replyToCommentId }
        comments.filter { it.replyToCommentId.isBlank() }.flatMap { listOf(it) + replies[it.id].orEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = Color(0xFF2B2B2B))
            }
            Text("Post", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
        }

        when (val s = state) {
            PostState.Loading -> Box(Modifier.weight(1f))
            PostState.Gone -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("This post has been deleted.", color = TextSecondary, fontSize = 14.sp)
            }
            is PostState.Loaded -> {
                val post = s.post
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    item {
                        PostCard(
                            post,
                            liked = liked,
                            onLikeClick = viewModel::toggleLike,
                            onOpen = { inputFocus.requestFocus() },
                            onAuthorClick = { onAuthorClick(post.authorUid, post.authorName) },
                            showComments = false // the full list follows
                        )
                    }
                    item {
                        Text(
                            if (threads.isEmpty()) "No comments yet — ask ${post.authorName} a question."
                            else "Comments",
                            fontSize = if (threads.isEmpty()) 13.sp else 15.sp,
                            fontWeight = if (threads.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                            color = if (threads.isEmpty()) TextSecondary else Color(0xFF2B2B2B),
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
                        )
                    }
                    items(threads, key = { it.id }) { comment ->
                        CommentRow(
                            comment = comment,
                            isPostAuthor = comment.authorUid == post.authorUid,
                            // Your own comments, and anything on your own post, can be deleted.
                            canDelete = comment.authorUid == viewModel.myUid || post.authorUid == viewModel.myUid,
                            onReply = {
                                viewModel.replyingTo = comment
                                inputFocus.requestFocus()
                            },
                            onDelete = { viewModel.deleteComment(comment) }
                        )
                    }
                }

                CommentInput(
                    text = viewModel.input,
                    onTextChange = { viewModel.input = it.take(1000) },
                    replyingTo = viewModel.replyingTo,
                    onCancelReply = { viewModel.replyingTo = null },
                    sending = viewModel.sending,
                    error = viewModel.error,
                    onSend = viewModel::send,
                    focusRequester = inputFocus
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: PostComment,
    isPostAuthor: Boolean,
    canDelete: Boolean,
    onReply: () -> Unit,
    onDelete: () -> Unit
) {
    val isReply = comment.replyToCommentId.isNotBlank()
    Row(modifier = Modifier.fillMaxWidth().padding(start = if (isReply) 44.dp else 0.dp, bottom = 12.dp)) {
        ProfileAvatar(comment.authorPhoto, comment.authorName, size = if (isReply) 28.dp else 34.dp)
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.authorName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
                    if (comment.authorIsExpert) {
                        Text(
                            if (isPostAuthor) "Author" else "Expert",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(VastuPrimary)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    (if (isReply && comment.replyToName.isNotBlank()) "@${comment.replyToName} " else "") + comment.text,
                    fontSize = 14.sp,
                    color = Color(0xFF2B2B2B),
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Row(modifier = Modifier.padding(start = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(timeAgo(comment.createdAtMillis), fontSize = 11.sp, color = TextSecondary)
                Text(
                    "Reply",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = VastuPrimary,
                    modifier = Modifier.padding(start = 14.dp).clickable { onReply() }
                )
                if (canDelete) {
                    Text(
                        "Delete",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Danger,
                        modifier = Modifier.padding(start = 14.dp).clickable { onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentInput(
    text: String,
    onTextChange: (String) -> Unit,
    replyingTo: PostComment?,
    onCancelReply: () -> Unit,
    sending: Boolean,
    error: String?,
    onSend: () -> Unit,
    focusRequester: FocusRequester
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp)) {
        replyingTo?.let {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)) {
                Text("Replying to ${it.authorName}", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Cancel reply",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp).clickable { onCancelReply() }
                )
            }
        }
        error?.let { Text(it, fontSize = 12.sp, color = Danger, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(if (replyingTo != null) "Write a reply…" else "Ask a question or comment…", color = TextHint) },
                textStyle = TextStyle(color = Color(0xFF2B2B2B), fontSize = 14.sp),
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = InputFill,
                    focusedContainerColor = InputFill,
                    unfocusedBorderColor = InputFill,
                    focusedBorderColor = VastuPrimary
                ),
                modifier = Modifier.weight(1f).focusRequester(focusRequester)
            )
            val canSend = text.isNotBlank() && !sending
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (canSend) VastuPrimary else VastuPrimary.copy(alpha = 0.35f))
                    .clickable(enabled = canSend) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
