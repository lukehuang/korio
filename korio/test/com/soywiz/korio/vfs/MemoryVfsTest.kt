package com.soywiz.korio.vfs

import com.soywiz.korio.async.sync
import org.junit.Assert
import org.junit.Test

class MemoryVfsTest {
	@Test
	fun name() = sync<Unit> {
		val log = arrayListOf<String>()
		val mem = MemoryVfs()

		mem.watch {
			log += it.toString()
		}.use {
			mem["item.txt"].writeString("test")
			mem["test"].mkdir()
			mem["test"].delete()
			Assert.assertEquals(
				"[MODIFIED(NodeVfs[/item.txt]), CREATED(NodeVfs[/test]), DELETED(NodeVfs[/test])]",
				log.toString()
			)
		}
	}
}