package com.kimyaci60.odevtakibim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.Update
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme,
                typography = MaterialTheme.typography,
                content = {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppScreen()
                    }
                }
            )
        }
    }
}

@Composable
fun AppScreen(viewModel: OdevTakipViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            item {
                Text(text = "Ödev Takibim", style = MaterialTheme.typography.headlineMedium)
            }
            item {
                Text(text = "Öğrenci: ${state.selectedStudentName}", modifier = Modifier.padding(top = 8.dp))
            }
            items(state.homeworks) { homework ->
                HomeworkCard(homework = homework, onToggle = { viewModel.toggleCompleted(it) })
            }
        }
    }
}

@Composable
fun HomeworkCard(homework: Homework, onToggle: (Homework) -> Unit) {
    val cardColor = when {
        homework.isCompleted -> Color(0xFFB9F6CA)
        homework.dueAtMillis < System.currentTimeMillis() -> Color(0xFFFFCDD2)
        else -> Color(0xFFFFF9C4)
    }

    Card(modifier = Modifier.padding(vertical = 8.dp), containerColor = cardColor) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(12.dp)
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(text = homework.title, style = MaterialTheme.typography.titleMedium)
                Text(text = homework.subjectName, style = MaterialTheme.typography.bodyMedium)
                Text(text = "Teslim: ${homework.dueAtText}")
            }
            Checkbox(
                checked = homework.isCompleted,
                onCheckedChange = { onToggle(homework.copy(isCompleted = it)) }
            )
        }
    }
}

class OdevTakipViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState = _uiState.asStateFlow()

    init {
        val sample = listOf(
            Homework(
                id = 1,
                title = "Matematik ödevi",
                subjectName = "Matematik",
                dueAtMillis = System.currentTimeMillis() + 86400000L,
                isCompleted = false,
                dueAtText = "1 gün sonra"
            ),
            Homework(
                id = 2,
                title = "Tarih sunumu",
                subjectName = "Tarih",
                dueAtMillis = System.currentTimeMillis() + 1000L,
                isCompleted = true,
                dueAtText = "Bugün"
            )
        )
        _uiState.value = AppUiState(
            selectedStudentName = "Ahmet Yılmaz",
            homeworks = sample
        )
    }

    fun toggleCompleted(homework: Homework) {
        val updated = _uiState.value.homeworks.map {
            if (it.id == homework.id) homework else it
        }
        _uiState.value = _uiState.value.copy(homeworks = updated)
    }
}

data class AppUiState(
    val selectedStudentName: String = "Seçili öğrenci",
    val homeworks: List<Homework> = emptyList()
)

data class Homework(
    val id: Long,
    val title: String,
    val subjectName: String,
    val dueAtMillis: Long,
    val isCompleted: Boolean = false,
    val dueAtText: String = ""
)

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
)

@Entity(tableName = "classrooms")
data class Classroom(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
)

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val classroomId: Long,
    val name: String
)

@Entity(tableName = "topics")
data class Topic(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val title: String
)

@Entity(tableName = "resources")
data class Resource(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val title: String,
    val url: String? = null
)

@Entity(tableName = "homework")
data class HomeworkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val studentId: Long,
    val subjectId: Long,
    val title: String,
    val description: String? = null,
    val dueAtEpochMillis: Long,
    val isCompleted: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@Dao
interface StudentDao {
    @Query("SELECT * FROM students")
    fun getAll(): Flow<List<Student>>

    @Insert
    suspend fun insert(student: Student): Long
}

@Dao
interface HomeworkDao {
    @Query("SELECT * FROM homework WHERE studentId = :studentId ORDER BY dueAtEpochMillis ASC")
    fun getByStudent(studentId: Long): Flow<List<HomeworkEntity>>

    @Insert
    suspend fun insert(homework: HomeworkEntity): Long

    @Update
    suspend fun update(homework: HomeworkEntity)
}

@Database(
    entities = [Student::class, Classroom::class, Subject::class, Topic::class, Resource::class, HomeworkEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun homeworkDao(): HomeworkDao
}

object ReminderScheduler {
    fun scheduleWork(workManager: WorkManager, homeworkId: Long, dueAtMillis: Long) {
        val now = System.currentTimeMillis()
        val noticeOneDayBefore = dueAtMillis - TimeUnit.DAYS.toMillis(1)
        val noticeOnDueDay = dueAtMillis

        val data = Data.Builder().putLong("homeworkId", homeworkId).build()

        if (noticeOneDayBefore > now) {
            val request = OneTimeWorkRequestBuilder<HomeworkReminderWorker>()
                .setInputData(data)
                .setInitialDelay(noticeOneDayBefore - now, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniqueWork("homework_${homeworkId}_day_before", ExistingWorkPolicy.REPLACE, request)
        }

        if (noticeOnDueDay > now) {
            val request = OneTimeWorkRequestBuilder<HomeworkReminderWorker>()
                .setInputData(data)
                .setInitialDelay(noticeOnDueDay - now, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniqueWork("homework_${homeworkId}_due_day", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

class HomeworkReminderWorker : androidx.work.Worker {
    constructor(context: android.content.Context, params: androidx.work.WorkerParameters) : super(context, params)

    override fun doWork(): Result {
        val homeworkId = inputData.getLong("homeworkId", -1L)
        if (homeworkId == -1L) return Result.failure()

        return Result.success()
    }
}
