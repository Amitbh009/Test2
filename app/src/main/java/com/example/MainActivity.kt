package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.db.AppDatabase
import com.example.data.repository.DocRepository
import com.example.ui.screens.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DocViewModel
import com.example.viewmodel.DocViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) // Handle edge-to-edge full bleed cleanly
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        // Initialize AppDatabase, repository and viewmodel using standard factories
                        val context = LocalContext.current.applicationContext
                        val database = AppDatabase.getDatabase(context)
                        val repository = DocRepository(database.docDao())
                        val docViewModel: DocViewModel = viewModel(
                            factory = DocViewModelFactory(repository)
                        )

                        AppNavigation(viewModel = docViewModel)
                    }
                }
            }
        }
    }
}
