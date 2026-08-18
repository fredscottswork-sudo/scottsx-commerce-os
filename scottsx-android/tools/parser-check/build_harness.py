#!/usr/bin/env python3
"""
Builds a runnable Kotlin harness from the SHIPPING model source.

Slicing the real file (rather than keeping a copy) guarantees the harness can
never drift away from the code the app actually uses.

    build_harness.py <MarketplaceModels.kt> <MessagesScreen.kt> <outDir>
"""
import sys

models_path, screens_path, out_dir = sys.argv[1], sys.argv[2], sys.argv[3]
models = open(models_path).read()
screens = open(screens_path).read()


def slice_between(text, start, end):
    i = text.index(start)
    j = text.index(end, i)
    return text[i:j]


parts = [
    # Chat models: OtherParty, Conversation, InboxCounts, Inbox, ChatMessage,
    # Transcript, QuickReplyItem.
    slice_between(models, 'data class OtherParty(', 'data class MessageThread('),
    # JSON helpers, including optStringSafe.
    slice_between(models, 'internal fun JSONObject.optStringOrNull',
                  'internal fun JSONObject.optLongOrNull'),
    'internal fun JSONObject.optLongOrNull(key: String): Long? =\n'
    '    if (isNull(key)) null else optLong(key)\n',
    # The timestamp formatter under test.
    slice_between(screens, 'internal fun chatTimeLabel', '/** Shared inbox UI'),
]

harness = '''import org.json.JSONObject

''' + '\n'.join(parts) + '''

fun main(args: Array<String>) {
    val dir = if (args.isNotEmpty()) args[0] else "/tmp"
    var problems = 0

    fun flag(msg: String) { println("  !! " + msg); problems++ }

    val inbox = JSONObject(java.io.File("$dir/inbox.json").readText())
    val counts = InboxCounts.fromJson(inbox.getJSONObject("counts"))
    println("counts: all=${counts.all} unread=${counts.unread} offers=${counts.offers} " +
            "pinned=${counts.pinned} archived=${counts.archived}")

    val arr = inbox.getJSONArray("conversations")
    println("\\nconversations (${arr.length()}):")
    var sawBlankTimestamp = false
    for (i in 0 until arr.length()) {
        val c = Conversation.fromJson(arr.getJSONObject(i))
        val label = chatTimeLabel(c.lastTime)
        if (c.lastTime.isBlank()) sawBlankTimestamp = true
        if (c.lastTime == "null") flag("lastTime leaked the string \\"null\\"")
        if (c.lastMessage == "null") flag("lastMessage leaked the string \\"null\\"")
        if (label == "null") flag("chatTimeLabel produced \\"null\\"")
        println("  %-24s time=%-26s label=%-10s msg=%s".format(
            c.otherParty.name.take(24), "\\"" + c.lastTime + "\\"",
            "\\"" + label + "\\"", "\\"" + c.lastMessage.take(20) + "\\""))
    }
    if (!sawBlankTimestamp) {
        println("  (note: no message-less thread in this capture — the null-timestamp " +
                "path was not exercised)")
    }

    val tx = JSONObject(java.io.File("$dir/transcript.json").readText())
    val msgs = tx.getJSONArray("messages")
    println("\\nmessages (${msgs.length()}):")
    for (i in 0 until msgs.length()) {
        val m = ChatMessage.fromJson(msgs.getJSONObject(i))
        if (m.text == "null") flag("message text leaked the string \\"null\\"")
        println("  kind=%-7s offer=%-10s status=%-9s qty=%d read=%b text=%s".format(
            m.kind, m.offerMinor?.toString() ?: "-", m.offerStatus ?: "-",
            m.offerQuantity, m.readByOther, "\\"" + m.text.take(24) + "\\""))
    }

    val offer = (0 until msgs.length())
        .map { ChatMessage.fromJson(msgs.getJSONObject(it)) }
        .firstOrNull { it.isOffer }
    if (offer == null) {
        flag("no offer message found in the capture")
    } else {
        val minor = offer.offerMinor ?: 0L
        println("\\noffer parses to UGX " + (minor / 100) + " (minor=" + minor + ")")
        if (minor != 45000000L) flag("offerMinor should be 45000000, got " + minor)
    }

    val head = JSONObject(java.io.File("$dir/head.json").readText())
    val hc = Conversation.fromJson(head.getJSONObject("conversation"))
    println("head: ${hc.otherParty.name} verified=${hc.otherParty.verified} " +
            "mySide=${hc.mySide} price=${hc.productPriceMinor}")
    if (hc.productPriceMinor == null || hc.productPriceMinor == 0L) {
        flag("productPriceMinor failed to parse (bigint returned as a string?)")
    }
    if (hc.otherParty.name == "null") flag("otherParty.name leaked \\"null\\"")

    if (problems == 0) {
        println("\\nOK - every parser handled the real backend JSON correctly.")
    } else {
        println("\\nFAILED - " + problems + " problem(s)")
        kotlin.system.exitProcess(1)
    }
}
'''

with open(f'{out_dir}/Parse.kt', 'w') as fh:
    fh.write(harness)
print('  harness built from the shipping model source')
