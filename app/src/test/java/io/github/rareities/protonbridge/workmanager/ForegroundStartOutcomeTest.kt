package io.github.rareities.protonbridge.workmanager

import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.testing.TestWorkerBuilder
import com.google.common.util.concurrent.Futures
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.util.BisyncPolicy
import io.github.rareities.protonbridge.util.BisyncStateStore
import io.github.rareities.protonbridge.util.RunHistory
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkInfo
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ForegroundStartOutcomeTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private var promotions = 0
    @Before fun setup() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context, androidx.work.Configuration.Builder().build())
        Shadows.shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    @After fun cleanup() { androidx.work.impl.WorkManagerImpl.setDelegate(null) }

    private fun rejected(attempt: Int, automatic: Boolean, error: Throwable = ForegroundServiceStartNotAllowedException("fixture rejection")): Triple<Task, SyncWorker, Pair<File,String>> {
        val connection=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadow=Shadows.shadowOf(connection)
        shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI,0,true,NetworkInfo.State.CONNECTED))
        val capabilities=org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        shadow.setNetworkCapabilities(connection.activeNetwork,capabilities)
        val task=DatabaseHandler(context).createTask(Task(0).apply {
            title="Scheduled fixture"; direction=6; remoteId="fixture"; remotePath="right"; localPath=File(context.filesDir,"left").path
        })
        val root=BisyncPolicy.workdir(context,task)
        val state=BisyncStateStore(root)
        val baseline=UUID.randomUUID().toString()
        state.begin("initialise","unchanged","unchanged",baseline)
        state.finish("initialise","unchanged",baseline,true,0,"ok")
        val original=File(root,"protonbridge.json").readText()
        val worker=TestWorkerBuilder.from(context,SyncWorker::class.java,Executor { it.run() })
            .setInputData(Data.Builder().putLong(SyncWorker.TASK_ID,task.id).putBoolean(SyncManager.AUTOMATIC,automatic).build())
            .setRunAttemptCount(attempt)
            .setForegroundUpdater(ForegroundUpdater { _,_,_ ->
                promotions++
                Futures.immediateFailedFuture<Void>(error)
            }).build()
        return Triple(task,worker,root to original)
    }

    @Test fun scheduledRestrictionNeedsActionWithoutRetryingOrChangingBaseline() {
        val (task,worker,saved)=rejected(0,true)
        val result=worker.doWork()
        assertEquals(1,promotions)
        assertTrue(result is ListenableWorker.Result.Failure)
        val history=RunHistory.read(context,task)
        assertEquals(1,history.length())
        assertEquals("NEEDS_USER_ACTION",history.getJSONObject(0).getString("status"))
        assertFalse(history.getJSONObject(0).isNull("finished"))
        assertEquals(saved.second,File(saved.first,"protonbridge.json").readText())
    }

    @Test fun manualRestrictionRequiresUserActionWithoutResync() {
        val (task,worker,saved)=rejected(0,false)
        assertTrue(worker.doWork() is ListenableWorker.Result.Failure)
        val record=RunHistory.read(context,task).getJSONObject(0)
        assertEquals("NEEDS_USER_ACTION",record.getString("status"))
        assertEquals(1,promotions)
        assertTrue(record.getString("message").contains("background"))
        assertEquals(saved.second,File(saved.first,"protonbridge.json").readText())
    }

    @Test fun unrelatedForegroundErrorsDoNotBecomeBackgroundRetries() {
        val (task,worker,saved)=rejected(0,true,SecurityException("missing manifest permission"))
        assertTrue(worker.doWork() is ListenableWorker.Result.Failure)
        assertEquals(1,promotions)
        assertEquals("FAILED",RunHistory.read(context,task).getJSONObject(0).getString("status"))
        assertEquals(saved.second,File(saved.first,"protonbridge.json").readText())
    }
}
