package ca.pkay.rcloneexplorer.workmanager

import ca.pkay.rcloneexplorer.Items.SyncDirectionObject
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProtonBisyncIdentityTest {
    @Test
    fun credentialRotationAndKeyOrderDoNotInvalidateLocalApproval() {
        val before = JSONObject()
            .put("type", "protondrive").put("username", "account")
            .put("client_uid", "old-session").put("client_access_token", "old-access")
        val after = JSONObject()
            .put("client_refresh_token", "new-refresh").put("client_uid", "new-session")
            .put("username", "account").put("type", "protondrive")
        assertEquals(ProtonBisync.configurationIdentity(before), ProtonBisync.configurationIdentity(after))
    }

    @Test
    fun editsToTheAccountOrProviderOptionsInvalidateLocalApproval() {
        val settings = JSONObject().put("type", "protondrive").put("username", "account")
        val original = ProtonBisync.configurationIdentity(settings)
        settings.put("username", "different")
        assertNotEquals(original, ProtonBisync.configurationIdentity(settings))
        settings.put("username", "account").put("root_folder_id", "different-root")
        assertNotEquals(original, ProtonBisync.configurationIdentity(settings))
    }

    @Test
    fun protonDirectionIsAddedAfterExistingDirections() {
        assertEquals(SyncDirectionObject.COPY_REMOTE_TO_REMOTE, SyncDirectionObject.directionForSpinnerPosition(5))
        assertEquals(SyncDirectionObject.SYNC_BIDIRECTIONAL, SyncDirectionObject.directionForSpinnerPosition(6))
        assertEquals(6, SyncDirectionObject.spinnerPositionForDirection(SyncDirectionObject.SYNC_BIDIRECTIONAL))
        assertEquals(0, SyncDirectionObject.spinnerPositionForDirection(SyncDirectionObject.SYNC_BIDIRECTIONAL_INITIAL))
    }
}
