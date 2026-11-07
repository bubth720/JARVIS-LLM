package fr.jarvis.llm.engine

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions
import java.io.RandomAccessFile

/** Local synthetic sparse/truncated byte fixtures, no downloaded GGUF and no inference. */
class LabFileSecurityTest {
    private class PrivateFixture(sparse: Boolean=false) : AutoCloseable {
        val root=Files.createTempDirectory("jarvis-lab-unit-")
        val file=root.resolve(LabAuthorizedModel.fileName)
        init {
            if(sparse) RandomAccessFile(file.toFile(),"rw").use { it.setLength(LabAuthorizedModel.size) }
            else Files.write(file,byteArrayOf(0,1,2))
            Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("r--------"))
            Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("r-x------"))
        }
        override fun close() {
            Files.setPosixFilePermissions(root,PosixFilePermissions.fromString("rwx------"))
            Files.list(root).use { it.forEach { p -> Files.deleteIfExists(p) } };Files.delete(root)
        }
    }
    @Test fun truncatedFixtureIsRejectedBeforeHashOrLoad()=PrivateFixture().use { f ->
        assertTrue(runCatching { LabFileProof.verify(f.root,f.file) {} }.isFailure)
    }
    @Test fun exactSizeButWrongShaCannotMintFileProof()=PrivateFixture(true).use { f ->
        val error=runCatching { LabFileProof.verify(f.root,f.file) {} }.exceptionOrNull()
        assertNotNull(error);assertEquals("LAB_FILE_SHA256",error!!.message)
    }
    @Test fun cancellationInterruptsHashingOfSyntheticBytes()=PrivateFixture(true).use { f ->
        var checks=0;val error=runCatching { LabFileProof.verify(f.root,f.file) { checks++;error("UNIT_CANCELLED") } }.exceptionOrNull()
        assertEquals(1,checks);assertEquals("UNIT_CANCELLED",error!!.message)
    }
    @Test fun writableFileCannotBeAdmitted()=PrivateFixture(true).use { f ->
        Files.setPosixFilePermissions(f.file,PosixFilePermissions.fromString("rw-------"))
        assertTrue(runCatching { LabFileProof.verify(f.root,f.file) {} }.isFailure)
    }
    @Test fun writableSnapshotDirectoryCannotBeAdmitted()=PrivateFixture(true).use { f ->
        Files.setPosixFilePermissions(f.root,PosixFilePermissions.fromString("rwx------"))
        assertTrue(runCatching { LabFileProof.verify(f.root,f.file) {} }.isFailure)
    }
    @Test fun hardLinkAliasCannotBeAdmitted()=PrivateFixture(true).use { f ->
        Files.setPosixFilePermissions(f.root,PosixFilePermissions.fromString("rwx------"));Files.createLink(f.root.resolve("alias"),f.file)
        Files.setPosixFilePermissions(f.root,PosixFilePermissions.fromString("r-x------"))
        assertTrue(runCatching { LabFileProof.verify(f.root,f.file) {} }.isFailure)
    }
    @Test fun symlinkAndNonExactFileNameRejected()=PrivateFixture(true).use { f ->
        Files.setPosixFilePermissions(f.root,PosixFilePermissions.fromString("rwx------"));val link=Files.createSymbolicLink(f.root.resolve("link"),f.file)
        Files.setPosixFilePermissions(f.root,PosixFilePermissions.fromString("r-x------"))
        assertTrue(runCatching { LabFileProof.verify(f.root,link) {} }.isFailure)
    }
}
