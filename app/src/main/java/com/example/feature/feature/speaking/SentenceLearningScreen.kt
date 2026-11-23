
package com.englishai.feature.speaking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Green = Color(0xFF087F50)
private val LightGreen = Color(0xFFE8F3EC)
private val AppBackground = Color(0xFFF7F9F7)
private val DarkText = Color(0xFF202820)
private val GrayText = Color(0xFF777777)

data class SentencePattern(
    val title: String,
    val meaning: String,
    val explanation: String,
    val examples: List<Pair<String, String>>,
    val isPremium: Boolean = false
)

private val patterns = listOf(
    SentencePattern(
        title = "I'd like to + Verb",
        meaning = "আমি ... করতে চাই (ভদ্রভাবে)",
        explanation = "কোনো ইচ্ছা বা চাহিদা ভদ্রভাবে প্রকাশ করতে I'd like to ব্যবহার করা হয়। এর পরে Verb-এর মূল রূপ বসে।",
        examples = listOf(
            "I'd like to go." to "আমি যেতে চাই।",
            "I'd like to learn English." to "আমি ইংরেজি শিখতে চাই।",
            "I'd like to order food." to "আমি খাবার অর্ডার করতে চাই।",
            "I'd like to take a day off tomorrow." to
                "আমি আগামীকাল একদিনের ছুটি নিতে চাই।"
        )
    ),
    SentencePattern(
        title = "I'm thinking of + Verb-ing",
        meaning = "আমি ... করার কথা ভাবছি",
        explanation = "কোনো কাজ করার বিষয়ে চিন্তা বা পরিকল্পনা বিবেচনা করার সময় এই Pattern ব্যবহার করা হয়। এর পরে Verb-এর সঙ্গে ing যুক্ত হয়।",
        examples = listOf(
            "I'm thinking of going." to "আমি যাওয়ার কথা ভাবছি।",
            "I'm thinking of buying a phone." to "আমি একটি ফোন কেনার কথা ভাবছি।",
            "I'm thinking of changing my job." to "আমি চাকরি পরিবর্তনের কথা ভাবছি।"
        )
    ),
    SentencePattern(
        title = "I want to + Verb",
        meaning = "আমি ... করতে চাই",
        explanation = "নিজের ইচ্ছা সরাসরি প্রকাশ করতে I want to ব্যবহার করা হয়। এর পরে Verb-এর মূল রূপ বসে।",
        examples = listOf(
            "I want to learn." to "আমি শিখতে চাই।",
            "I want to improve my English." to "আমি আমার ইংরেজি উন্নত করতে চাই।",
            "I want to start a business." to "আমি একটি ব্যবসা শুরু করতে চাই।"
        ),
        isPremium = true
    ),
    SentencePattern(
        title = "I'm planning to + Verb",
        meaning = "আমি ... করার পরিকল্পনা করছি",
        explanation = "ভবিষ্যতে কোনো কাজ করার পরিকল্পনা বোঝাতে এই Pattern ব্যবহার করা হয়।",
        examples = listOf(
            "I'm planning to travel." to "আমি ভ্রমণের পরিকল্পনা করছি।",
            "I'm planning to study." to "আমি পড়াশোনা করার পরিকল্পনা করছি।"
        ),
        isPremium = true
    ),
    SentencePattern(
        title = "I hope to + Verb",
        meaning = "আমি আশা করি ... করতে",
        explanation = "ভবিষ্যতে কোনো কিছু করার আশা বা প্রত্যাশা প্রকাশ করতে এই Pattern ব্যবহার করা হয়।",
        examples = listOf(
            "I hope to succeed." to "আমি সফল হওয়ার আশা করি।",
            "I hope to see you." to "আমি তোমার সঙ্গে দেখা করার আশা করি।"
        ),
        isPremium = true
    )
)

private enum class LearningPage {
    LIST, EXPLANATION, PRACTICE
}

@Composable
fun SentenceLearningScreen() {
    var page by remember { mutableStateOf(LearningPage.LIST) }
    var selectedIndex by remember { mutableIntStateOf(0) }

    val selectedPattern = patterns[selectedIndex]

    when (page) {
        LearningPage.LIST -> {
            PatternListScreen(
                onPatternClick = { index ->
                    selectedIndex = index
                    page = LearningPage.EXPLANATION
                }
            )
        }

        LearningPage.EXPLANATION -> {
            PatternExplanationScreen(
                pattern = selectedPattern,
                onBack = { page = LearningPage.LIST },
                onPractice = { page = LearningPage.PRACTICE }
            )
        }

        LearningPage.PRACTICE -> {
            SpeakingPracticeScreen(
                pattern = selectedPattern,
                onBack = { page = LearningPage.EXPLANATION }
            )
        }
    }
}

// ------------------------------------
// SCREEN 1: PATTERN LIST
// ------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatternListScreen(
    onPatternClick: (Int) -> Unit
) {
    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Spoken English",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground
                )
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Green)
                    .padding(16.dp)
            ) {
                Text(
                    "⚡ Spoken English Practice",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "প্রতিদিন অনুশীলন করুন",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
            }

            Text(
                "Desire / Intent / Preference",
                modifier = Modifier.padding(
                    start = 20.dp,
                    top = 22.dp,
                    bottom = 4.dp
                ),
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )

            Text(
                "Sentence Structure · বাক্য গঠনের প্যাটার্ন",
                modifier = Modifier.padding(
                    horizontal = 20.dp
                ),
                color = GrayText,
                fontSize = 14.sp
            )

            LazyColumn(
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(patterns) { index, pattern ->

                    PatternCard(
                        pattern = pattern,
                        onClick = {
                            onPatternClick(index)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternCard(
    pattern: SentencePattern,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    pattern.title,
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )

                if (pattern.isPremium) {
                    Surface(
                        color = Color(0xFFFFF2DE),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            "♛ Premium",
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 5.dp
                            ),
                            color = Color(0xFFC17B20),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                pattern.meaning,
                color = GrayText,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = GrayText,
                    modifier = Modifier.size(17.dp)
                )

                Text(
                    "  প্রায় ৮ মিনিট",
                    color = GrayText,
                    fontSize = 12.sp
                )

                Spacer(Modifier.width(20.dp))

                Icon(
                    Icons.Default.Layers,
                    contentDescription = null,
                    tint = GrayText,
                    modifier = Modifier.size(17.dp)
                )

                Text(
                    "  ${pattern.examples.size}টি Example",
                    color = GrayText,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ------------------------------------
// SCREEN 2: PATTERN EXPLANATION
// ------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatternExplanationScreen(
    pattern: SentencePattern,
    onBack: () -> Unit,
    onPractice: () -> Unit
) {
    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Pattern Explanation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground
                )
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = onPractice,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Green
                    )
                ) {
                    Text(
                        "Speaking Practice শুরু করুন",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null
                    )
                }
            }
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Green,
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            pattern.title,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(14.dp))

                        Text(
                            pattern.meaning,
                            fontSize = 19.sp,
                            color = GrayText,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(18.dp))

                        Text(
                            pattern.explanation,
                            fontSize = 16.sp,
                            lineHeight = 27.sp,
                            color = DarkText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            item {
                Text(
                    "Example Sentences",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
            }

            itemsIndexed(pattern.examples) { index, example ->

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Example ${index + 1}",
                            color = Green,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            example.first,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            example.second,
                            fontSize = 15.sp,
                            color = GrayText
                        )
                    }
                }
            }

            item {
                Text(
                    "মনে রাখুন: এই Pattern দিয়ে নিজের জীবনের বাক্য তৈরি করে অনুশীলন করুন।",
                    color = Green,
                    fontSize = 14.sp,
                    lineHeight = 23.sp
                )
            }
        }
    }
}

// ------------------------------------
// SCREEN 3: SPEAKING PRACTICE
// ------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeakingPracticeScreen(
    pattern: SentencePattern,
    onBack: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }

    val examples = pattern.examples
    val currentExample = examples[currentIndex]

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        pattern.title,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            showAnswer = false
                            isListening = false
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground
                )
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
        ) {

            LinearProgressIndicator(
                progress = {
                    (currentIndex + 1).toFloat() /
                        examples.size.toFloat()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp),
                color = Green,
                trackColor = Color.LightGray
            )

            Text(
                "${currentIndex + 1} of ${examples.size}",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                textAlign = TextAlign.Center,
                color = GrayText
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE8E8E8)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {

                    Surface(
                        color = Green,
                        shape = RoundedCornerShape(30.dp)
                    ) {
                        Text(
                            "এখন ইংরেজিতে বলুন...",
                            modifier = Modifier.padding(
                                horizontal = 20.dp,
                                vertical = 10.dp
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            currentExample.second,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 39.sp,
                            color = DarkText,
                            textAlign = TextAlign.Center
                        )

                        if (showAnswer) {
                            Spacer(Modifier.height(24.dp))

                            Text(
                                currentExample.first,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = Green,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(22.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            OutlinedIconButton(
                                onClick = {
                                    // এখানে Text To Speech যুক্ত হবে
                                },
                                modifier = Modifier.size(58.dp)
                            ) {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = "Listen",
                                    tint = Green
                                )
                            }

                            FilledIconButton(
                                onClick = {
                                    // এখানে Android Speech Recognizer যুক্ত হবে
                                    isListening = !isListening
                                },
                                modifier = Modifier.size(72.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isListening)
                                        Color(0xFFD32F2F) else Green
                                )
                            ) {
                                Icon(
                                    if (isListening)
                                        Icons.Default.Stop
                                    else Icons.Default.Mic,
                                    contentDescription = "Speak",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            OutlinedIconButton(
                                onClick = {
                                    showAnswer = !showAnswer
                                },
                                modifier = Modifier.size(58.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Show answer",
                                    tint = Green
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            if (isListening)
                                "মাইক্রোফোন UI চালু হয়েছে"
                            else
                                "শুনুন  •  বলুন  •  উত্তর দেখুন",
                            color = GrayText,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    if (currentIndex < examples.lastIndex) {
                        currentIndex++
                        showAnswer = false
                        isListening = false
                    } else {
                        currentIndex = 0
                        showAnswer = false
                        isListening = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Green
                )
            ) {
                Text(
                    if (currentIndex == examples.lastIndex)
                        "আবার অনুশীলন করুন"
                    else
                        "পরবর্তী বাক্য",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
