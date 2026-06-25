package com.kamboji.quiver.currency

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamboji.quiver.hub.theme.AppTheme
import kotlinx.coroutines.launch

class CurrencyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { CurrencyScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyScreen(vm: CurrencyViewModel = viewModel()) {
    val scheme = MaterialTheme.colorScheme
    val titles = listOf("Converter", "Rates", "Info")
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
                .statusBarsPadding(),
        ) {
            Text(
                "Currency Converter",
                color = scheme.surface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(16.dp),
            )
            TabRow(
                selectedTabIndex = pager.currentPage,
                containerColor = Color.Transparent,
                contentColor = scheme.surface,
                indicator = { positions ->
                    if (pager.currentPage < positions.size) {
                        TriangleIndicator(positions[pager.currentPage], scheme.surface)
                    }
                },
                divider = {},
            ) {
                titles.forEachIndexed { i, title ->
                    Tab(
                        selected = pager.currentPage == i,
                        onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        text = {
                            Text(
                                title,
                                fontWeight = if (pager.currentPage == i) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
            when (page) {
                0 -> ConverterTab(vm)
                1 -> RatesTab(vm)
                else -> InfoTab(vm)
            }
        }
    }
}

@Composable
private fun TriangleIndicator(position: TabPosition, color: Color) {
    Box(
        Modifier
            .wrapContentSize(Alignment.BottomStart)
            .offset(x = position.left)
            .width(position.width),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(Modifier.size(width = 18.dp, height = 9.dp)) {
            val path = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, color)
        }
    }
}
