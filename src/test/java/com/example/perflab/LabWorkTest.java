package com.example.perflab;
import com.example.perflab.service.LabWork;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LabWorkTest {
    @Test void touchesAllocatedMemoryAndProducesObservableComputation() {
        var work=new LabWork();
        assertEquals(65536,work.alloc(64).get("allocatedBytes"));
        assertTrue((Long)work.alloc(64).get("checksum")>0);
        assertEquals(work.cpu(10000),work.cpu(10000));
        assertNotEquals(work.cpu(1000).get("checksum"),work.cpu(10000).get("checksum"));
    }
}
