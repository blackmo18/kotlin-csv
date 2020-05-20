package com.github.doyaaaaaken.kotlincsv.client

import com.github.doyaaaaaken.kotlincsv.dsl.context.CsvReaderContext
import com.github.doyaaaaaken.kotlincsv.parser.CsvHeader
import com.github.doyaaaaaken.kotlincsv.parser.CsvParser
import com.github.doyaaaaaken.kotlincsv.util.CSVFieldNumDifferentException
import com.github.doyaaaaaken.kotlincsv.util.MalformedCSVException
import java.io.BufferedReader
import java.io.Closeable

/**
 * CSV Reader class, which controls file I/O flow.
 *
 * @author doyaaaaaken
 */
class CsvFileReader internal constructor(
        private val ctx: CsvReaderContext,
        reader: BufferedReader
) : Closeable {

    private val reader = BufferedLineReader(reader)
    private var rowNum = 0L

    private val parser = CsvParser(ctx.quoteChar, ctx.delimiter, ctx.escapeChar)

    fun readAll(): List<List<String>> {
        return readAllAsSequence().toList()
    }

    fun readAllWithHeader(): List<Map<String, String>> {
        val headers = readNext()
        val duplicated = headers?.let(::findDuplicate)
        if (duplicated != null) throw MalformedCSVException("header '$duplicated' is duplicated")

        return readAllAsSequence().map { fields ->
            if (requireNotNull(headers).size != fields.size) {
                throw MalformedCSVException("fields num  ${fields.size} is not matched with header num ${headers.size}")
            }
            headers.zip(fields).toMap()
        }.toList()
    }

    fun readAllAsSequence(): Sequence<List<String>> {
        var fieldsNum: Int? = null
        return generateSequence {
            readNext()
        }.mapIndexed { idx, row ->
            if (fieldsNum == null) fieldsNum = row.size
            if (fieldsNum != row.size) throw CSVFieldNumDifferentException(requireNotNull(fieldsNum), row.size, idx + 1)
            row
        }
    }

    fun readAllWithHeaderFromSequence( body: CsvHeader.(index: Int, list: List<String>) -> Unit) {
        var header = CsvHeader()
        readAllAsSequence().forEachIndexed { index, line ->
            if (index == 0) {
                header = CsvHeader(line)
            } else {
                header.body(index, line)
            }
        }
    }

    override fun close() {
        reader.close()
    }

    /**
     * read next csv row (which may contain multiple lines)
     *
     * @return return fields in row as List<String>.
     *         or return null, if all line are already read.
     */
    private tailrec fun readNext(leftOver: String = ""): List<String>? {
        val nextLine = reader.readLineWithTerminator()
        rowNum++
        return if (nextLine == null) {
            if (leftOver.isNotEmpty()) {
                throw MalformedCSVException("\"$leftOver\" on the tail of file is left on the way of parsing row")
            } else {
                null
            }
        } else if (ctx.skipEmptyLine && nextLine.isBlank() && leftOver.isBlank()) {
            readNext(leftOver)
        } else {
            val value = if (leftOver.isEmpty()) {
                "$nextLine"
            } else {
                "$leftOver$nextLine"
            }
            parser.parseRow(value, rowNum) ?: readNext("$leftOver$nextLine")
        }
    }

    private fun findDuplicate(headers: List<String>): String? {
        val set = mutableSetOf<String>()
        headers.forEach { h ->
            if (set.contains(h)) {
                return h
            } else {
                set.add(h)
            }
        }
        return null
    }
}
