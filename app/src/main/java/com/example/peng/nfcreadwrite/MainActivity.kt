package com.example.peng.nfcreadwrite

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.IntentFilter
import android.widget.TextView
import android.os.Bundle
import com.example.peng.nfcreadwrite.R
import android.widget.Toast
import com.example.peng.nfcreadwrite.MainActivity
import android.content.Intent
import android.nfc.*
import android.os.Parcelable
import kotlin.Throws
import android.nfc.tech.Ndef
import android.util.Log
import android.view.View
import android.widget.Button
import java.io.IOException
import java.io.UnsupportedEncodingException
import kotlin.experimental.and
import android.R.string.no




class MainActivity : Activity() {
    var nfcAdapter: NfcAdapter? = null
    var pendingIntent: PendingIntent? = null
    lateinit var writeTagFilters: Array<IntentFilter>
    var writeMode = false
    var myTag: Tag? = null
    var context: Context? = null
    var tvNFCContent: TextView? = null
    var message: TextView? = null
    var btnWrite: Button? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        context = this
        tvNFCContent = findViewById<View>(R.id.nfc_contents) as TextView
        message = findViewById<View>(R.id.edit_message) as TextView
        btnWrite = findViewById<View>(R.id.button) as Button
        btnWrite!!.setOnClickListener {
            try {
                if (myTag == null) {
                    Toast.makeText(context, ERROR_DETECTED, Toast.LENGTH_LONG).show()
                } else {
                    write(message!!.text.toString(), myTag!!)
                    Toast.makeText(context, WRITE_SUCCESS, Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                Toast.makeText(context, WRITE_ERROR, Toast.LENGTH_LONG).show()
                e.printStackTrace()
            } catch (e: FormatException) {
                Toast.makeText(context, WRITE_ERROR, Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            // Stop here, we definitely need NFC
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show()
            finish()
        }
        readFromIntent(intent)
        pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            0
        )
        val tagDetected = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT)
        writeTagFilters = arrayOf(tagDetected)
    }

    /******************************************************************************
     * Read From NFC Tag***************************
     */
    private fun readFromIntent(intent: Intent) {
        val action = intent.action
        if (NfcAdapter.ACTION_TAG_DISCOVERED == action || NfcAdapter.ACTION_TECH_DISCOVERED == action || NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            var msgs: Array<NdefMessage?>? = null
            if (rawMsgs != null) {
                msgs = arrayOfNulls(rawMsgs.size)
                for (i in rawMsgs.indices) {
                    msgs[i] = rawMsgs[i] as NdefMessage
                }
            }
            buildTagViews(msgs)
        }
    }

    private fun buildTagViews(msgs: Array<NdefMessage?>?) {
        if (msgs == null || msgs.size == 0) return
        var text = ""
        //        String tagId = new String(msgs[0].getRecords()[0].getType());
        val payload = msgs[0]!!.records[0].payload
        val textEncoding = if (payload[0] and 128.toByte() == 0.toByte()) "UTF-8" else "UTF-16"
        val languageCodeLength = (payload[0] and 51.toByte()).toInt()
        // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
        try {
            // Get the Text
            text = String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, charset(
                textEncoding
            ))
        } catch (e: UnsupportedEncodingException) {
            Log.e("UnsupportedEncoding", e.toString())
        }
        tvNFCContent!!.text = "NFC Content: $text"
    }

    /******************************************************************************
     * Write to NFC Tag****************************
     */
    @Throws(IOException::class, FormatException::class)
    private fun write(text: String, tag: Tag) {
        val records = arrayOf(createRecord(text))
        val message = NdefMessage(records)
        // Get an instance of Ndef for the tag.
        val ndef = Ndef.get(tag)
        // Enable I/O
        ndef.connect()
        // Write the message
        ndef.writeNdefMessage(message)
        // Close the connection
        ndef.close()
    }

    @Throws(UnsupportedEncodingException::class)
    private fun createRecord(text: String): NdefRecord {
        val lang = "en"
        val textBytes = text.toByteArray()
        val langBytes = lang.toByteArray(charset("US-ASCII"))
        val langLength = langBytes.size
        val textLength = textBytes.size
        val payload = ByteArray(1 + langLength + textLength)

        // set status byte (see NDEF spec for actual bits)
        payload[0] = langLength.toByte()

        // copy langbytes and textbytes into payload
        System.arraycopy(langBytes, 0, payload, 1, langLength)
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength)
        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
    }

    override fun onNewIntent(intent: Intent) {
        setIntent(intent)
        readFromIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            myTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
    }

    public override fun onPause() {
        super.onPause()
        WriteModeOff()
    }

    public override fun onResume() {
        super.onResume()
        WriteModeOn()
    }

    /******************************************************************************
     * Enable Write********************************
     */
    private fun WriteModeOn() {
        writeMode = true
        nfcAdapter!!.enableForegroundDispatch(this, pendingIntent, writeTagFilters, null)
    }

    /******************************************************************************
     * Disable Write*******************************
     */
    private fun WriteModeOff() {
        writeMode = false
        nfcAdapter!!.disableForegroundDispatch(this)
    }

    companion object {
        const val ERROR_DETECTED = "No NFC tag detected!"
        const val WRITE_SUCCESS = "Text written to the NFC tag successfully!"
        const val WRITE_ERROR = "Error during writing, is the NFC tag close enough to your device?"
    }
}