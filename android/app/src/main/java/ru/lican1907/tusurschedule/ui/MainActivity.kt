package ru.lican1907.tusurschedule.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import ru.lican1907.tusurschedule.ui.theme.DarkBackground
import ru.lican1907.tusurschedule.ui.theme.TusurScheduleTheme
import ru.lican1907.tusurschedule.worker.ScheduleSyncWorker

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enqueue periodic background sync for the widget
        ScheduleSyncWorker.enqueuePeriodicSync(this)

        setContent {
            TusurScheduleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    ScheduleScreen()
                }
            }
        }
    }
}
