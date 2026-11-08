package xyz.znix.xftl.jmh

import org.jdom2.Document
import org.jdom2.input.SAXBuilder
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import xyz.znix.xftl.VanillaDatafile
import xyz.znix.xftl.bxml.BXMLReader
import xyz.znix.xftl.bxml.BXMLWriter
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path

open class BXMLBenchmark {
    @Benchmark
    fun testReading(state: BenchmarkState): Document {
        return BufferedInputStream(Files.newInputStream(state.path)).use { BXMLReader.read(it) }
    }

    @State(Scope.Benchmark)
    open class BenchmarkState {
        val path: Path = Path.of("/tmp/benchmark.bxml")

        init {
            val df = VanillaDatafile.createWithDefaultPath()

            val data: ByteArray = df.read(df["data/blueprints.xml"])
            val builder = SAXBuilder()
            val doc = builder.build(ByteArrayInputStream(data))

            BufferedOutputStream(Files.newOutputStream(path)).use { BXMLWriter.write(doc, it) }
        }
    }
}
