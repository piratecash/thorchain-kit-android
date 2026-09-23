package io.horizontalsystems.thorchainkit

import com.google.gson.Gson
import io.horizontalsystems.thorchainkit.network.MidgardAction
import io.horizontalsystems.thorchainkit.sync.TransactionSyncer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.math.BigInteger

class TransactionSyncerTest {

    // real mainnet Midgard send action
    private val sendActionJson = """
        {
          "date": "1784454197789344321",
          "height": "27069723",
          "in": [
            {
              "address": "thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh",
              "coins": [{"amount": "950009120000", "asset": "THOR.RUNE"}],
              "txID": "E0C97FCAB81C8CF22B235F38A7CAA97134719BD36C26746DA900B1DC7424E460"
            }
          ],
          "metadata": {
            "send": {
              "code": "0",
              "memo": "hello",
              "networkFees": [{"amount": "20000000", "asset": "THOR.RUNE"}],
              "reason": ""
            }
          },
          "out": [
            {
              "address": "thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws",
              "coins": [{"amount": "950009120000", "asset": "THOR.RUNE"}],
              "txID": "E0C97FCAB81C8CF22B235F38A7CAA97134719BD36C26746DA900B1DC7424E460"
            }
          ],
          "pools": [],
          "status": "success",
          "type": "send"
        }
    """

    @Test
    fun fromMidgardAction_send() {
        val action = Gson().fromJson(sendActionJson, MidgardAction::class.java)
        val transaction = TransactionSyncer.fromMidgardAction(action)

        assertNotNull(transaction)
        transaction!!

        assertEquals("E0C97FCAB81C8CF22B235F38A7CAA97134719BD36C26746DA900B1DC7424E460", transaction.hash)
        assertEquals(27069723L, transaction.blockHeight)
        assertEquals(1784454197L, transaction.timestamp)
        assertEquals("send", transaction.type)
        assertEquals("success", transaction.status)
        assertEquals("hello", transaction.memo)
        assertEquals(false, transaction.isPending)

        assertEquals(1, transaction.incoming.size)
        assertEquals("thor1t60f02r8jvzjrhtnjgfj4ne6rs5wjnejwmj7fh", transaction.incoming[0].address)
        assertEquals("THOR.RUNE", transaction.incoming[0].asset)
        assertEquals(BigInteger("950009120000"), transaction.incoming[0].amount)

        assertEquals(1, transaction.outgoing.size)
        assertEquals("thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws", transaction.outgoing[0].address)
    }

    // real mainnet Midgard action: a BOND that was included in a block but failed
    // with insufficient funds — Midgard still reports status "success"
    private val failedActionJson = """
        {
          "date": "1790070519718577133",
          "height": "27937636",
          "in": [
            {
              "address": "thor1z3e8pxs5fpgcdjpnn92y7xfv90enqm46qtxdl2",
              "coins": [{"amount": "641972000000", "asset": "THOR.RUNE"}],
              "txID": "A9AFEA02641C049E08CC310E626290EC7B22B83EB796AA36F0B202181C321C8E"
            }
          ],
          "metadata": {
            "failed": {
              "code": "5",
              "memo": "BOND:thor1fj6zv7uvn0t898ch7sxlmjept7lfdnrer8rtpq",
              "reason": "failed to execute message; message index: 0: insufficient funds"
            }
          },
          "out": [],
          "pools": [],
          "status": "success",
          "type": "failed"
        }
    """

    @Test
    fun fromMidgardAction_failed() {
        val action = Gson().fromJson(failedActionJson, MidgardAction::class.java)
        val transaction = TransactionSyncer.fromMidgardAction(action)!!

        assertEquals("failed", transaction.type)
        assertEquals("failed", transaction.status)
        assertEquals(true, transaction.isFailed)
        assertEquals(false, transaction.isPending)
        assertEquals("BOND:thor1fj6zv7uvn0t898ch7sxlmjept7lfdnrer8rtpq", transaction.memo)
    }

    @Test
    fun fromMidgardAction_send_notFailed() {
        val action = Gson().fromJson(sendActionJson, MidgardAction::class.java)

        assertEquals(false, TransactionSyncer.fromMidgardAction(action)!!.isFailed)
    }

    @Test
    fun fromMidgardAction_noTxId() {
        val action = Gson().fromJson(
            """{"date":"1","height":"2","in":[],"out":[],"pools":[],"status":"success","type":"send"}""",
            MidgardAction::class.java
        )

        assertEquals(null, TransactionSyncer.fromMidgardAction(action))
    }
}
