package com.thescarletcoder.keys

import java.io.File
import java.io.SequenceInputStream

/** The dictionary's text sources as the build merges them (en.dict, then extra.dict). */
object TestDict {
    fun source() = SequenceInputStream(File("../tools/dict/en.dict").inputStream(), File("../tools/dict/extra.dict").inputStream())
}
